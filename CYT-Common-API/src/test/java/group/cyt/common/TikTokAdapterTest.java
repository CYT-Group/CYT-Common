package group.cyt.common;

import static group.cyt.common.core.util.JsonUtils.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.http.UpstreamHttp;
import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.module.adapter.tiktok.TikTokAdapter;
import group.cyt.common.module.credential.service.CredentialService;
import group.cyt.common.module.grant.service.AuthorizationService;
import group.cyt.common.module.registry.service.DocumentationContracts;
import group.cyt.common.module.registry.service.OperationRegistry;
import java.util.*;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;

class TikTokAdapterTest {
  private static final String SHOP = "kiiboom";
  private static final String SECRET = "fixture-secret";
  private static final String HOST = "open-api.tiktokglobalshop.com";

  private record Lookup(
      String resource,
      String method,
      String path,
      Map<String, Object> params,
      String body,
      Map<String, Object> data,
      Object expected) {}

  private final GatewayStore store = mock(GatewayStore.class);
  private final UpstreamHttp http = mock(UpstreamHttp.class);
  private final CredentialService credentials = mock(CredentialService.class);

  private void configureShop() {
    when(credentials.raw("TIKTOK_COMMERCE_ACCOUNTS")).thenReturn("epomaker-keyboard,kiiboom");
    when(credentials.required("TIKTOK_KIIBOOM_APP_KEY", "cs")).thenReturn("fixture-app");
    when(credentials.required("TIKTOK_KIIBOOM_APP_SECRET", "cs")).thenReturn(SECRET);
    when(credentials.required("TIKTOK_KIIBOOM_ACCESS_TOKEN", "cs")).thenReturn("fixture-token");
    when(credentials.required("TIKTOK_KIIBOOM_SHOP_CIPHER", "cs")).thenReturn("fixture-cipher");
  }

  @Test
  void csOperationsHaveSeparatePathsAndMatchTheirSignedUpstreamContracts() throws Exception {
    configureShop();
    var registry = new OperationRegistry(store);
    var docs = new DocumentationContracts(store, registry);
    var adapter = new TikTokAdapter(credentials, http);
    var window = obj("updated_from", 200, "updated_until", 400);
    var order = obj("id", "123", "status", "DELIVERED");
    var orderWithPrivateFields =
        obj(
            "id",
            "123",
            "status",
            "DELIVERED",
            "buyer_email",
            "private",
            "recipient_address",
            obj("name", "private"),
            "line_items",
            List.of(obj("seller_sku", "SKU", "buyer_email", "private")));
    var product = obj("id", "456", "title", "Fixture");
    var tracking = obj("action_code", 50101, "update_time_millis", 1795579812000L);
    var returns = obj("return_id", "789", "order_id", "123", "return_status", "COMPLETE");
    var lookups =
        List.of(
            new Lookup(
                "conversations/list",
                "GET",
                "/customer_service/202309/conversations",
                Map.of(),
                "",
                obj("conversations", List.of(order)),
                obj("raw", List.of(order), "next_page_token", "")),
            new Lookup(
                "messages/list",
                "GET",
                "/customer_service/202309/conversations/123/messages",
                obj("conversation_id", "123", "page_token", "cursor/+?"),
                "",
                obj("messages", List.of(order), "next_page_token", "next"),
                obj("raw", List.of(order), "next_page_token", "next")),
            new Lookup(
                "orders/list",
                "POST",
                "/order/202309/orders/search",
                window,
                "{\"update_time_ge\":200,\"update_time_lt\":400}",
                obj("orders", List.of(orderWithPrivateFields)),
                obj(
                    "raw",
                    List.of(
                        obj(
                            "id",
                            "123",
                            "status",
                            "DELIVERED",
                            "line_items",
                            List.of(obj("seller_sku", "SKU")),
                            "payment",
                            Map.of())),
                    "next_page_token",
                    "")),
            new Lookup(
                "orders/get",
                "GET",
                "/order/202309/orders",
                obj("order_id", "123"),
                "",
                obj("orders", List.of(orderWithPrivateFields)),
                obj(
                    "raw",
                    obj(
                        "id",
                        "123",
                        "status",
                        "DELIVERED",
                        "line_items",
                        List.of(obj("seller_sku", "SKU"))))),
            new Lookup(
                "products/list",
                "POST",
                "/product/202502/products/search",
                window,
                "{\"update_time_ge\":200,\"update_time_le\":399}",
                obj("products", List.of(product)),
                obj(
                    "raw",
                    List.of(obj("id", "456", "title", "Fixture", "skus", List.of())),
                    "next_page_token",
                    "")),
            new Lookup(
                "products/get",
                "GET",
                "/product/202309/products/456",
                obj("product_id", "456"),
                "",
                product,
                obj(
                    "raw",
                    obj(
                        "id",
                        "456",
                        "title",
                        "Fixture",
                        "skus",
                        List.of(),
                        "category_chains",
                        List.of(),
                        "main_images",
                        List.of()))),
            new Lookup(
                "tracking/get",
                "GET",
                "/fulfillment/202309/orders/123/tracking",
                obj("order_id", "123"),
                "",
                obj("tracking", List.of(tracking)),
                obj("raw", List.of(tracking))),
            new Lookup(
                "returns/list",
                "POST",
                "/return_refund/202602/returns/search",
                window,
                "{\"update_time_ge\":200,\"update_time_lt\":400,\"return_types\":[\"REFUND\",\"RETURN_AND_REFUND\"]}",
                obj(
                    "return_orders",
                    List.of(
                        obj(
                            "return_id",
                            "789",
                            "order_id",
                            "123",
                            "return_status",
                            "COMPLETE",
                            "refund_amount",
                            obj("amount", "20")))),
                obj("raw", List.of(returns), "next_page_token", "")));
    var registered =
        registry.builtinOperations().stream()
            .filter(op -> op.platform().equals("tiktok") && op.readOnly())
            .toList();
    assertEquals(lookups.size(), registered.size());
    for (var lookup : lookups) {
      var op = registry.get("tiktok." + lookup.resource().replace('/', '.'));
      assertEquals("/integrations/tiktok/" + lookup.resource(), op.path());
      assertEquals("POST", op.httpMethod());
      assertTrue(op.readOnly());
      assertTrue(op.sensitive());
      assertTrue(tree(docs.response(op)).path("properties").has("raw"));
      reset(http);
      when(http.quietExchange(anyString(), anyString(), anyMap(), any(), anyDouble()))
          .thenAnswer(
              call -> {
                assertEquals(lookup.method(), call.getArgument(0));
                var url = HttpUrl.get(call.getArgument(1, String.class));
                assertEquals(HOST, url.host());
                assertEquals(lookup.path(), url.encodedPath());
                Map<String, String> headers = call.getArgument(2);
                assertEquals("fixture-token", headers.get("x-tts-access-token"));
                byte[] body = call.getArgument(3);
                assertEquals(lookup.body(), body == null ? "" : new String(body, UTF_8));
                var query = new TreeMap<String, String>();
                url.queryParameterNames()
                    .forEach(field -> query.put(field, url.queryParameter(field)));
                assertEquals("fixture-app", query.get("app_key"));
                assertEquals("fixture-cipher", query.get("shop_cipher"));
                if (lookup.resource().equals("orders/get")) assertEquals("123", query.get("ids"));
                if (lookup.resource().equals("messages/list")) {
                  assertEquals("cursor/+?", query.get("page_token"));
                  assertEquals("10", query.get("page_size"));
                  assertEquals("true", query.get("need_plaintext"));
                }
                String given = query.remove("sign");
                var signed = new StringBuilder(SECRET).append(lookup.path());
                query.forEach((field, value) -> signed.append(field).append(value));
                assertEquals(
                    hmac(SECRET, signed.append(lookup.body()).append(SECRET).toString()), given);
                return response(200, obj("code", 0, "data", lookup.data()));
              });
      var params = obj("account", SHOP);
      params.putAll(lookup.params());
      assertEquals(
          lookup.expected(),
          adapter.invoke(op, registry.validate(op, params), "cs"),
          lookup.resource());
    }
  }

  @Test
  void rejectsInvalidScopeAndMalformedWindowsBeforeCallingTheProvider() throws Exception {
    configureShop();
    var registry = new OperationRegistry(store);
    var tracking = registry.get("tiktok.tracking.get");
    var invalidOrder = obj("account", SHOP, "order_id", "../123");
    assertEquals(
        422,
        assertThrows(GatewayException.class, () -> registry.validate(tracking, invalidOrder))
            .status);
    assertEquals(
        422,
        assertThrows(
                GatewayException.class,
                () ->
                    registry.validate(
                        registry.get("tiktok.orders.list"),
                        obj("account", SHOP, "updated_from", 400, "updated_until", 200)))
            .status);
    var adapter = new TikTokAdapter(credentials, http);
    var disabled = registry.validate(tracking, obj("account", "linsoul", "order_id", "123"));
    assertEquals(
        403,
        assertThrows(GatewayException.class, () -> adapter.invoke(tracking, disabled, "cs"))
            .status);
    verifyNoInteractions(http);
  }

  @Test
  void sendsConfirmedTextToTheSignedShopConversation() throws Exception {
    configureShop();
    var registry = new OperationRegistry(store);
    var operation = registry.get("tiktok.messages.send");
    assertEquals("/integrations/tiktok/messages/send", operation.path());
    assertFalse(operation.readOnly());
    assertTrue(operation.sensitive());
    assertTrue(operation.idempotent());
    assertTrue(operation.tags().contains("idempotency_required"));

    String path = "/customer_service/202606/conversations/123/messages";
    String text = "您好，已核对订单。";
    when(http.quietExchange(eq("POST"), anyString(), anyMap(), any(), anyDouble()))
        .thenAnswer(
            call -> {
              var url = HttpUrl.get(call.getArgument(1, String.class));
              assertEquals(HOST, url.host());
              assertEquals(path, url.encodedPath());
              Map<String, String> headers = call.getArgument(2);
              assertEquals("fixture-token", headers.get("x-tts-access-token"));
              String body = new String(call.getArgument(3, byte[].class), UTF_8);
              assertEquals(
                  obj(
                      "type", "TEXT",
                      "content", write(obj("content", text)),
                      "sender_role", "CUSTOMER_SERVICE"),
                  read(body));
              var query = new TreeMap<String, String>();
              url.queryParameterNames()
                  .forEach(field -> query.put(field, url.queryParameter(field)));
              assertEquals("fixture-cipher", query.get("shop_cipher"));
              String signature = query.remove("sign");
              var signed = new StringBuilder(SECRET).append(path);
              query.forEach((field, value) -> signed.append(field).append(value));
              assertEquals(hmac(SECRET, signed.append(body).append(SECRET).toString()), signature);
              return response(200, obj("code", 0, "data", obj("message_id", "456")));
            });
    var params = obj("account", SHOP, "conversation_id", "123", "content", text, "confirm", true);
    var adapter = new TikTokAdapter(credentials, http);
    assertEquals(
        obj("message_id", "456"),
        adapter.invoke(operation, registry.validate(operation, params), "cs"));
    verify(credentials).requireAccount("tiktok", SHOP, "cs");
  }

  @Test
  void rejectsUnconfirmedTextBeforeAnyUpstreamCall() throws Exception {
    var registry = new OperationRegistry(store);
    var operation = registry.get("tiktok.messages.send");
    var params =
        obj("account", SHOP, "conversation_id", "123", "content", "reply", "confirm", false);
    assertEquals(
        422, assertThrows(GatewayException.class, () -> registry.validate(operation, params)).status);
    var adapter = new TikTokAdapter(credentials, http);
    assertEquals(
        422, assertThrows(GatewayException.class, () -> adapter.invoke(operation, params, "cs")).status);
    verifyNoInteractions(http);
  }

  @Test
  void anExactGrantIsRequiredForEveryTikTokOperation() throws Exception {
    var registry = new OperationRegistry(store);
    var auth = new AuthorizationService(store, registry);
    var wildcard = obj("operation_pattern", "tiktok.*", "effect", "allow");
    for (var op :
        registry.builtinOperations().stream()
            .filter(op -> op.platform().equals("tiktok"))
            .toList()) {
      var exact = obj("operation_pattern", op.key(), "effect", "allow");
      var deny = obj("operation_pattern", "tiktok.*", "effect", "deny");
      assertEquals(false, auth.decision(op, List.of(wildcard)).get("allowed"));
      assertEquals(true, auth.decision(op, List.of(exact)).get("allowed"));
      assertEquals(false, auth.decision(op, List.of(exact, deny)).get("allowed"));
    }
  }

  @Test
  void providerErrorKeepsTheSafeCodeAndNeverReturnsProviderDetails() throws Exception {
    configureShop();
    var registry = new OperationRegistry(store);
    var tracking = registry.get("tiktok.tracking.get");
    when(http.quietExchange(anyString(), anyString(), anyMap(), any(), anyDouble()))
        .thenReturn(response(400, obj("code", 105005, "message", "private-provider-data")));
    var error =
        assertThrows(
            GatewayException.class,
            () ->
                new TikTokAdapter(credentials, http)
                    .invoke(
                        tracking,
                        registry.validate(tracking, obj("account", SHOP, "order_id", "123")),
                        "cs"));
    assertEquals("TIKTOK_105005", error.code);
    assertFalse(write(error.payload()).contains("private-provider-data"));
  }

  private static UpstreamHttp.ResponseData response(int status, Map<String, Object> body) {
    return new UpstreamHttp.ResponseData(status, Map.of(), write(body).getBytes(UTF_8));
  }
}
