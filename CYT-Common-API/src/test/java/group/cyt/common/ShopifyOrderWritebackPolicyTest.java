package group.cyt.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import group.cyt.common.core.config.GatewaySettings;
import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.http.UpstreamHttp;
import group.cyt.common.module.adapter.shopify.ShopifyAdapter;
import group.cyt.common.module.adapter.support.TestWritePolicy;
import group.cyt.common.module.credential.service.CredentialService;
import group.cyt.common.module.registry.model.OperationDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class ShopifyOrderWritebackPolicyTest {
  private static final String STORE = "fixture.myshopify.com";

  @Test
  void requiresTheConfiguredTestEnvironmentStoreAndAuthenticatedKey() {
    var settings = new GatewaySettings(
        new MockEnvironment()
            .withProperty("ENV", "test")
            .withProperty("GATEWAY_CS_SHOPIFY_ORDER_WRITEBACK_STORES", STORE)
            .withProperty("GATEWAY_CS_SHOPIFY_ORDER_WRITEBACK_KEY_ID", "12"));
    var policy = new TestWritePolicy(settings);
    assertTrue(policy.authorizeCsOrderUpdate("cs", STORE, "12"));
    assertFalse(policy.authorizeCsOrderUpdate("cs", "other.myshopify.com", "12"));
    assertThrows(GatewayException.class, () -> policy.authorizeCsOrderUpdate("cs", STORE, ""));
    assertThrows(GatewayException.class, () -> policy.authorizeCsOrderUpdate("cs", STORE, "13"));
    assertThrows(GatewayException.class, () -> policy.authorizeCsOrderUpdate("other", STORE, "12"));
    settings.markProductionEnvironment();
    assertFalse(policy.authorizeCsOrderUpdate("cs", STORE, "12"));
  }

  @Test
  void requiresProductionAndAnExactStoreAndKeyForCancellation() {
    var env = new MockEnvironment()
        .withProperty("ENV", "production")
        .withProperty("GATEWAY_CS_SHOPIFY_ORDER_CANCEL_STORES", STORE)
        .withProperty("GATEWAY_CS_SHOPIFY_ORDER_CANCEL_KEY_ID", "12");
    var policy = new TestWritePolicy(new GatewaySettings(env));
    assertTrue(policy.authorizeCsOrderCancel("cs", STORE.toUpperCase(java.util.Locale.ROOT), "12"));
    assertFalse(policy.authorizeCsOrderUpdate("cs", STORE, "12"));
    assertThrows(GatewayException.class, () -> policy.authorizeCsOrderCancel("cs", "other.myshopify.com", "12"));
    assertThrows(GatewayException.class, () -> policy.authorizeCsOrderCancel("cs", STORE, ""));
    assertThrows(GatewayException.class, () -> policy.authorizeCsOrderCancel("cs", STORE, "13"));
    assertThrows(GatewayException.class, () -> policy.authorizeCsOrderCancel("other", STORE, "12"));
    env.setProperty("GATEWAY_CS_SHOPIFY_ORDER_CANCEL_STORES", "");
    assertThrows(GatewayException.class, () -> policy.authorizeCsOrderCancel("cs", STORE, "12"));
    env.setProperty("ENV", "test");
    assertFalse(policy.authorizeCsOrderCancel("cs", STORE, "12"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void requiresTheWriteScopeBeforeSendingAnOrderMutation(boolean writeScope) {
    var credentials = mock(CredentialService.class);
    var http = mock(UpstreamHttp.class);
    when(credentials.resolve("SHOPIFY_STORE", "cs")).thenReturn(STORE);
    when(credentials.resolve("SHOPIFY_API_VERSION", "cs")).thenReturn("2026-07");
    when(credentials.required("SHOPIFY_STORE", "cs")).thenReturn(STORE);
    when(credentials.required("SHOPIFY_ACCESS_TOKEN", "cs")).thenReturn("fixture");
    when(http.object(eq("POST"), anyString(), anyMap(), anyMap()))
        .thenReturn(Map.of("data", Map.of("currentAppInstallation", Map.of("accessScopes",
            List.of(Map.of("handle", writeScope ? "write_orders" : "read_orders"))))))
        .thenReturn(Map.of("data", Map.of("orderUpdate", Map.of(
            "order", Map.of("note", "test"), "userErrors", List.of()))));
    var op = new OperationDefinition("shopify.orders.update", "shopify", "", "", "", true, true,
        30, 0, "", false, List.of("write"), "", 0, "", null);
    var shopify = new ShopifyAdapter(credentials, http, mock(TestWritePolicy.class));
    var params = Map.<String, Object>of("store", STORE, "order_id", "gid://shopify/Order/1", "note", "test");
    if (writeScope) {
      shopify.invokeCsOrderWriteback(op, params, "cs");
    } else {
      var error = assertThrows(GatewayException.class,
          () -> shopify.invokeCsOrderWriteback(op, params, "cs"));
      assertEquals(403, error.status);
      assertTrue(error.getMessage().contains("write_orders"));
    }
    var calls = inOrder(http);
    calls.verify(http).object(eq("POST"), anyString(), anyMap(),
        argThat(body -> body.toString().contains("currentAppInstallation")));
    if (writeScope) calls.verify(http).object(eq("POST"), anyString(), anyMap(),
        argThat(body -> body.toString().contains("orderUpdate")));
    verifyNoMoreInteractions(http);
  }
}
