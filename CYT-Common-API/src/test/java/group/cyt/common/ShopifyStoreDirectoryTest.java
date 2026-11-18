package group.cyt.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import group.cyt.common.core.config.GatewaySettings;
import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.http.UpstreamHttp;
import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.adapter.shopify.ShopifyStoreDirectory;
import group.cyt.common.module.auth.service.AuthenticationService;
import group.cyt.common.module.credential.service.CredentialService;
import group.cyt.common.module.grant.service.AuthorizationService;
import group.cyt.common.module.registry.service.OperationRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShopifyStoreDirectoryTest {
  private final AuthenticationService auth = mock(AuthenticationService.class);
  private final AuthorizationService grant = mock(AuthorizationService.class);
  private final OperationRegistry operations = mock(OperationRegistry.class);
  private final CredentialService credentials = mock(CredentialService.class);
  private final UpstreamHttp http = mock(UpstreamHttp.class);
  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final ShopifyStoreDirectory directory = new ShopifyStoreDirectory(auth, grant, operations,
      mock(GatewayStore.class), mock(GatewaySettings.class), credentials, http);

  @Test
  void reportsOnlyVerifiedShopMetadataAndDistinguishesMissingOrderScope() {
    String managed = "fixture.myshopify.com";
    String missing = "unconfigured.myshopify.com";
    String wrong = "wrong-identity.myshopify.com";
    String noScope = "no-scope.myshopify.com";
    when(auth.system(request)).thenReturn("cs");
    when(credentials.resolve(eq("SHOPIFY_API_VERSION"), anyString())).thenReturn("2026-07");
    when(credentials.resolve("SHOPIFY_STORE", "cs")).thenReturn("");
    when(credentials.raw(anyString())).thenReturn("");
    when(credentials.raw("SHOPIFY_CS_FIXTURE_CLIENT_ID")).thenReturn("fixture-id");
    when(credentials.raw("SHOPIFY_CS_FIXTURE_CLIENT_SECRET")).thenReturn("fixture-secret");
    when(credentials.raw("SHOPIFY_OTHER_ACCESS_TOKEN")).thenReturn("fixture-other-token");
    when(credentials.systemForStore("SHOPIFY_STORE", managed)).thenReturn("cs_fixture");
    when(credentials.systemForStore("SHOPIFY_STORE", wrong)).thenReturn("other");
    when(credentials.systemForStore("SHOPIFY_STORE", noScope)).thenReturn("other");
    when(http.form(eq("https://" + managed + "/admin/oauth/access_token"), anyMap(), anyMap()))
        .thenReturn(Map.of("access_token", "fixture-app-token", "expires_in", 86400));
    when(http.object(eq("POST"), contains(managed), eq(Map.of("X-Shopify-Access-Token", "fixture-app-token")), any()))
        .thenReturn(shop(managed, List.of("read_orders", "read_all_orders")));
    when(http.object(eq("POST"), contains(wrong), anyMap(), any()))
        .thenReturn(shop(managed, List.of("read_orders")));
    when(http.object(eq("POST"), contains(noScope), anyMap(), any()))
        .thenReturn(shop(noScope, List.of("read_products")));

    var body = Map.<String, Object>of("stores", List.of(managed, missing, wrong, noScope));
    var result = JsonUtils.map(directory.list(body, request));
    var shops = JsonUtils.list(JsonUtils.map(result.get("data")).get("stores"));
    assertEquals(List.of("authorized", "pending", "unavailable", "pending"), shops.stream()
        .map(shop -> JsonUtils.str(JsonUtils.map(shop), "status")).toList());
    assertEquals(true, JsonUtils.map(shops.getFirst()).get("historical_orders"));
    assertFalse(result.toString().contains("fixture-app-token"));
    assertFalse(result.toString().contains("fixture-secret"));
    directory.list(body, request);
    verify(http, times(1)).form(anyString(), anyMap(), anyMap());
  }

  @Test
  void rejectsOtherConsumersBeforeReadingUpstream() {
    when(auth.system(request)).thenReturn("portal");
    assertEquals(403, assertThrows(GatewayException.class,
        () -> directory.list(Map.of("stores", List.of()), request)).status);
    verifyNoInteractions(grant, credentials, http);
  }

  private Map<String, Object> shop(String domain, List<String> scopes) {
    return Map.of("data", Map.of("shop", Map.of("id", "gid://shopify/Shop/1",
        "myshopifyDomain", domain, "name", "Fixture"), "currentAppInstallation",
        Map.of("accessScopes", scopes.stream().map(scope -> Map.of("handle", scope)).toList())));
  }
}
