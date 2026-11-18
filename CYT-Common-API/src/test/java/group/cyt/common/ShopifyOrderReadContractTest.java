package group.cyt.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.http.UpstreamHttp;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.adapter.shopify.ShopifyAdapter;
import group.cyt.common.module.adapter.support.TestWritePolicy;
import group.cyt.common.module.credential.service.CredentialService;
import group.cyt.common.module.registry.model.OperationDefinition;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Synthetic upstream only: stable full line snapshots for the CS order mirror. */
class ShopifyOrderReadContractTest {
  private static final String STORE = "fixture.myshopify.com";
  private static final String ID = "gid://shopify/Order/1";
  private static final String VERSION = "2026-11-17T08:00:00Z";

  private CredentialService credentials() {
    var result = mock(CredentialService.class);
    when(result.resolve(anyString(), anyString()))
        .thenAnswer(a -> a.getArgument(0).equals("SHOPIFY_STORE") ? STORE : "");
    when(result.required(anyString(), anyString()))
        .thenAnswer(a -> a.getArgument(0).equals("SHOPIFY_STORE") ? STORE : "fixture-token");
    return result;
  }

  private ShopifyAdapter adapter(UpstreamHttp http) {
    return new ShopifyAdapter(credentials(), http, mock(TestWritePolicy.class));
  }

  private OperationDefinition operation(String key) {
    return new OperationDefinition("shopify." + key, "shopify", "", "", "", false, false,
        60, 0, "", false, List.of("read"), "", 0, "", null);
  }

  private Map<String, Object> params() {
    return JsonUtils.obj("store", STORE, "order_id", ID, "limit", 100,
        "sort_key", "UPDATED_AT", "reverse", false);
  }

  private Map<String, Object> line(int identifier) {
    return JsonUtils.obj("node", JsonUtils.obj("id", "gid://shopify/LineItem/" + identifier,
        "title", "Synthetic product", "sku", "SKU-" + identifier, "quantity", 1,
        "originalUnitPriceSet", JsonUtils.obj("shopMoney", JsonUtils.obj("amount", "10.00", "currencyCode", "USD")),
        "variant", null));
  }

  private Map<String, Object> order(List<?> edges, boolean more, String cursor) {
    return JsonUtils.obj("id", ID, "updatedAt", VERSION,
        "totalPriceSet", JsonUtils.obj("shopMoney", JsonUtils.obj("amount", "20.00", "currencyCode", "USD")),
        "lineItems", JsonUtils.obj("edges", edges, "pageInfo",
            JsonUtils.obj("hasNextPage", more, "hasPreviousPage", false, "endCursor", cursor)));
  }

  private Map<String, Object> response(Object order) {
    return JsonUtils.obj("data", JsonUtils.obj("order", order));
  }

  private Map<String, Object> get(ShopifyAdapter adapter) {
    return JsonUtils.map(adapter.invoke(operation("orders.get"), params(), "fixture"));
  }

  private Map<String, Object> diagnostic(Map<String, Object> result) {
    return JsonUtils.map(JsonUtils.map(result.get("order")).get("line_snapshot"));
  }

  @Test
  void cancellationChecksSnapshotAndMapsEachRefundMode() {
    var cancel = new OperationDefinition("shopify.orders.cancel", "shopify", "", "", "", true, true,
        30, 0, "", false, List.of("write"), "", 0, "", null);
    for (String method : List.of("original", "credit", "later")) {
      var http = mock(UpstreamHttp.class);
      var snapshot = JsonUtils.obj("data", JsonUtils.obj("shop", JsonUtils.obj("myshopifyDomain", STORE),
          "order", JsonUtils.obj("id", ID, "updatedAt", VERSION, "cancelledAt", null,
              "displayFulfillmentStatus", "UNFULFILLED", "displayFinancialStatus", "PAID",
              "fulfillments", List.of(), "customer", JsonUtils.obj("id", "customer-test"),
              "totalReceivedSet", JsonUtils.obj("presentmentMoney", JsonUtils.obj("amount", "25", "currencyCode", "USD")),
              "totalRefundedSet", JsonUtils.obj("presentmentMoney", JsonUtils.obj("amount", "0", "currencyCode", "USD")))));
      var accepted = JsonUtils.obj("data", JsonUtils.obj("orderCancel",
          JsonUtils.obj("job", JsonUtils.obj("id", "gid://shopify/Job/1", "done", false))));
      when(http.object(anyString(), anyString(), anyMap(), any()))
          .thenReturn(snapshot, snapshot, accepted);
      var params = JsonUtils.obj("store", STORE, "order_id", ID, "updated_at", "stale",
          "refund_method", method, "amount", "25.00", "currency", "USD", "reason", "CUSTOMER",
          "restock", false, "notify_customer", true, "staff_note", "fixture");
      assertEquals(false, JsonUtils.map(adapter(http).invoke(cancel, params, "fixture")).get("accepted"));
      verify(http, times(1)).object(anyString(), anyString(), anyMap(), any());

      params.put("updated_at", VERSION);
      assertEquals(true, JsonUtils.map(adapter(http).invoke(cancel, params, "fixture")).get("accepted"));
      var body = ArgumentCaptor.forClass(Map.class);
      verify(http, times(3)).object(anyString(), anyString(), anyMap(), body.capture());
      var variables = JsonUtils.map(body.getAllValues().getLast().get("variables"));
      var refund = JsonUtils.map(variables.get("refundMethod"));
      assertEquals(method.equals("credit") ? Map.of("storeCreditRefund", Map.of())
          : Map.of("originalPaymentMethodsRefund", method.equals("original")), refund);
      assertEquals("CUSTOMER", variables.get("reason"));
      assertEquals("fixture", variables.get("staffNote"));
      assertEquals(false, variables.get("restock"));
      assertEquals(true, variables.get("notifyCustomer"));
    }
  }

  @Test
  void getReturnsAuthorizedStoreAndVerifiedSingleOrEmptyPage() {
    for (List<?> edges : List.of(List.of(line(1)), List.of())) {
      var http = mock(UpstreamHttp.class);
      when(http.object(anyString(), anyString(), anyMap(), any()))
          .thenReturn(response(order(edges, false, "end")));
      var result = get(adapter(http));
      assertEquals(STORE, result.get("store"));
      assertEquals(false, result.get("mock"));
      assertEquals(true, diagnostic(result).get("complete"));
      assertEquals(edges.size(), diagnostic(result).get("count"));
      verify(http, times(1)).object(eq("POST"), contains(STORE), anyMap(), any());
      var body = ArgumentCaptor.forClass(Map.class);
      verify(http).object(anyString(), anyString(), anyMap(), body.capture());
      String document = Objects.toString(body.getValue().get("query"));
      for (String field : List.of("updatedAt", "originalUnitPriceSet", "variant", "id title quantity sku",
          "hasNextPage", "hasPreviousPage", "endCursor", "shippingAddress", "transactions", "totalRefundedSet"))
        assertTrue(document.contains(field), field);
      assertFalse(document.contains("mutation"));
    }
  }

  @Test
  void listCollectsNestedPagesWithoutReplacingOuterPagination() {
    var http = mock(UpstreamHttp.class);
    var outer = JsonUtils.obj("hasNextPage", true, "endCursor", "outer-cursor");
    when(http.object(anyString(), anyString(), anyMap(), any()))
        .thenReturn(JsonUtils.obj("data", JsonUtils.obj("orders", JsonUtils.obj("edges",
            List.of(JsonUtils.obj("node", order(List.of(line(1)), true, "line-1"))), "pageInfo", outer))))
        .thenReturn(response(order(List.of(line(2)), false, "line-2")));
    var result = JsonUtils.map(adapter(http).invoke(operation("orders.list"), params(), "fixture"));
    assertEquals(outer, result.get("page_info"));
    assertEquals(STORE, result.get("store"));
    var item = JsonUtils.map(JsonUtils.list(result.get("items")).getFirst());
    assertEquals(2, JsonUtils.map(item.get("line_snapshot")).get("count"));
    assertEquals(false, JsonUtils.at(item, "lineItems.pageInfo.hasNextPage"));
    assertEquals(false, JsonUtils.at(item, "lineItems.pageInfo.hasPreviousPage"));
    var body = ArgumentCaptor.forClass(Map.class);
    verify(http, times(2)).object(eq("POST"), contains(STORE), anyMap(), body.capture());
    assertEquals(JsonUtils.obj("id", ID, "after", "line-1"), body.getAllValues().get(1).get("variables"));
    for (Map request : body.getAllValues()) {
      String document = Objects.toString(request.get("query"));
      assertTrue(document.contains("id title quantity sku"));
      assertTrue(document.contains("hasPreviousPage"));
      assertFalse(document.contains("mutation"));
    }
  }

  @Test
  void changedOrderOrVersionCannotClaimACompleteSnapshot() {
    for (String field : List.of("id", "updatedAt")) {
      var http = mock(UpstreamHttp.class);
      var changed = order(List.of(line(2)), false, "line-2");
      changed.put(field, field.equals("id") ? "gid://shopify/Order/2" : "2026-11-17T08:00:01Z");
      when(http.object(anyString(), anyString(), anyMap(), any()))
          .thenReturn(response(order(List.of(line(1)), true, "line-1")))
          .thenReturn(response(changed));
      var result = get(adapter(http));
      assertEquals(false, diagnostic(result).get("complete"));
      assertEquals(1, JsonUtils.list(JsonUtils.at(result, "order.lineItems.edges")).size());
    }
  }

  @Test
  void missingPaginationFieldsAndDuplicateIdsRemainExplicitlyIncomplete() {
    var invalid = new ArrayList<Map<String, Object>>();
    var missingPage = order(List.of(line(1)), false, "end");
    JsonUtils.map(missingPage.get("lineItems")).remove("pageInfo");
    invalid.add(missingPage);
    invalid.add(order(List.of(line(1), line(1)), false, "end"));
    var missingId = order(List.of(line(1)), false, "end");
    JsonUtils.map(JsonUtils.map(JsonUtils.list(JsonUtils.at(missingId, "lineItems.edges")).getFirst()).get("node")).remove("id");
    invalid.add(missingId);
    var missingVersion = order(List.of(line(1)), false, "end");
    missingVersion.remove("updatedAt");
    invalid.add(missingVersion);
    for (var item : invalid) {
      var http = mock(UpstreamHttp.class);
      when(http.object(anyString(), anyString(), anyMap(), any())).thenReturn(response(item));
      assertEquals(false, diagnostic(get(adapter(http))).get("complete"));
    }
  }

  @Test
  void repeatedCursorsAndPageBudgetCannotBecomeFalseCompletion() {
    var http = mock(UpstreamHttp.class);
    when(http.object(anyString(), anyString(), anyMap(), any()))
        .thenReturn(response(order(List.of(line(1)), true, "same")))
        .thenReturn(response(order(List.of(line(2)), true, "same")));
    var result = get(adapter(http));
    assertEquals("line_cursor_missing_or_repeated", diagnostic(result).get("reason"));
    verify(http, times(2)).object(anyString(), anyString(), anyMap(), any());

    var cappedHttp = mock(UpstreamHttp.class);
    var count = new AtomicInteger();
    when(cappedHttp.object(anyString(), anyString(), anyMap(), any())).thenAnswer(a -> {
      int page = count.incrementAndGet();
      return response(order(List.of(line(page)), true, "page-" + page));
    });
    assertEquals("line_pagination_limit", diagnostic(get(adapter(cappedHttp))).get("reason"));
    assertEquals(21, count.get());
  }

  @Test
  void wrongStoreOrOrderIsRejectedAndTransportErrorsPropagate() {
    var http = mock(UpstreamHttp.class);
    var wrong = params();
    wrong.put("store", "unauthorized.myshopify.com");
    assertEquals(403, assertThrows(GatewayException.class,
        () -> adapter(http).invoke(operation("orders.get"), wrong, "fixture")).status);
    verifyNoInteractions(http);
    var otherOrder = order(List.of(line(1)), false, "end");
    otherOrder.put("id", "gid://shopify/Order/2");
    when(http.object(anyString(), anyString(), anyMap(), any())).thenReturn(response(otherOrder));
    assertEquals(502, assertThrows(GatewayException.class, () -> get(adapter(http))).status);
    reset(http);
    when(http.object(anyString(), anyString(), anyMap(), any()))
        .thenReturn(response(order(List.of(line(1)), true, "next")))
        .thenThrow(new IllegalStateException("synthetic transport failure"));
    assertThrows(IllegalStateException.class, () -> get(adapter(http)));
  }
}
