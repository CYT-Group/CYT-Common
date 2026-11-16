package group.cyt.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.http.UpstreamHttp;
import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.adapter.MockAdapter;
import group.cyt.common.module.adapter.shopify.ShopifyAdapter;
import group.cyt.common.module.adapter.shopify.ShopifyRefundMethods;
import group.cyt.common.module.adapter.support.TestWritePolicy;
import group.cyt.common.module.credential.service.CredentialService;
import group.cyt.common.module.registry.service.OperationRegistry;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ShopifyRefundMethodsTest {
  @Test
  void emptyGatewayLabelsUseTheSameFallbackAsPython() throws Exception {
    var o = order();
    JsonUtils.map(JsonUtils.at(o, "full.suggestedTransactions.0")).put("formattedGateway", "");
    JsonUtils.map(JsonUtils.at(o, "transactions.0")).put("formattedGateway", "");
    var q = ShopifyRefundMethods.buildQuote(o, params());
    assertEquals("shopify_payments", JsonUtils.at(q, "allocations.0.label"));
    assertEquals("shopify_payments", JsonUtils.at(q, "payments.0.label"));
  }

  static List<Map<String, Object>> fixtures() throws Exception {
    try (var input =
        ShopifyRefundMethodsTest.class.getResourceAsStream("/shopify-refund-parity.json")) {
      return JsonUtils.MAPPER.readValue(
          input, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }
  }

  static Stream<Arguments> parityCases() throws Exception {
    return fixtures().stream().map(f -> Arguments.of(f.get("name"), f));
  }

  private static Map<String, Object> copy(Object value) {
    return JsonUtils.read(JsonUtils.write(value));
  }

  private Map<String, Object> order() throws Exception {
    return copy(fixtures().getFirst().get("order"));
  }

  private Map<String, Object> params() throws Exception {
    return copy(fixtures().getFirst().get("params"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("parityCases")
  void pythonAndJavaReturnTheSamePlanAndApprovalHash(String name, Map<String, Object> fixture)
      throws Exception {
    var registry = new OperationRegistry(mock(GatewayStore.class));
    var p = registry.validate(registry.get("shopify.refunds.quote"), copy(fixture.get("params")));
    var actual = ShopifyRefundMethods.buildQuote(copy(fixture.get("order")), p);
    assertEquals(
        JsonUtils.write(JsonUtils.sorted(fixture.get("expected"))),
        JsonUtils.write(JsonUtils.sorted(actual)),
        name);
  }

  @Test
  void contractsUseTheSamePathsAndRejectUnconfirmedAndMalformedMoneyWrites() throws Exception {
    var registry = new OperationRegistry(mock(GatewayStore.class));
    var op = registry.get("shopify.store_credit.credit");
    assertEquals("/integrations/shopify/store-credit/credit", op.path());
    assertTrue(op.sensitive());
    assertTrue(op.idempotent());
    assertEquals(0, op.retryMax());
    assertTrue(op.tags().contains("idempotency_required"));
    var p =
        JsonUtils.obj(
            "store",
            "fixture.myshopify.com",
            "order_id",
            "gid://shopify/Order/1",
            "customer_id",
            "gid://shopify/Customer/2",
            "amount",
            "20.00",
            "currency",
            "EUR",
            "confirm",
            true);
    registry.validate(op, p);
    for (Object confirm : List.of(false, "true", 1)) {
      var invalid = new LinkedHashMap<>(p);
      invalid.put("confirm", confirm);
      assertThrows(GatewayException.class, () -> registry.validate(op, invalid));
    }
    for (String amount : List.of("NaN", "Infinity", "-1", "0")) {
      var invalid = new LinkedHashMap<>(p);
      invalid.put("amount", amount);
      assertThrows(GatewayException.class, () -> registry.validate(op, invalid));
    }
    var invalid = new LinkedHashMap<>(p);
    invalid.put("arbitrary_graphql", "mutation { anything }");
    assertThrows(GatewayException.class, () -> registry.validate(op, invalid));
  }

  @Test
  void mixedPartialRefundRequiresAValidExplicitAllocation() throws Exception {
    var p = params();
    p.put("amount", "20.00");
    var o = order();
    assertThrows(GatewayException.class, () -> ShopifyRefundMethods.buildQuote(o, p));
    for (var parts :
        List.of(
            List.of(JsonUtils.obj("parent_id", "gid://shopify/OrderTransaction/1", "amount", "80")),
            List.of(
                JsonUtils.obj("parent_id", "gid://shopify/OrderTransaction/99", "amount", "20")),
            List.of(
                JsonUtils.obj("parent_id", "gid://shopify/OrderTransaction/2", "amount", "10"),
                JsonUtils.obj("parent_id", "gid://shopify/OrderTransaction/2", "amount", "10")))) {
      p.put("allocations", parts);
      assertThrows(GatewayException.class, () -> ShopifyRefundMethods.buildQuote(o, p));
    }
  }

  @Test
  void unknownCurrenciesIncompleteTransactionsAndMissingTaxesFailClosed() throws Exception {
    var p = params();
    var o = order();
    p.put("currency", "USD");
    assertThrows(GatewayException.class, () -> ShopifyRefundMethods.buildQuote(o, p));
    p.put("currency", null);
    JsonUtils.map(o.get("transactionsCount")).put("count", 3);
    assertThrows(GatewayException.class, () -> ShopifyRefundMethods.buildQuote(o, p));
    var missing = order();
    JsonUtils.map(missing.get("full")).remove("totalTaxSet");
    assertThrows(GatewayException.class, () -> ShopifyRefundMethods.buildQuote(missing, p));
  }

  private ShopifyAdapter adapter(UpstreamHttp http, TestWritePolicy policy) throws Exception {
    var credentials = mock(CredentialService.class);
    when(credentials.resolve(anyString(), anyString())).thenReturn("");
    when(credentials.resolve("SHOPIFY_STORE", "cs")).thenReturn("fixture.myshopify.com");
    when(credentials.required("SHOPIFY_STORE", "cs")).thenReturn("fixture.myshopify.com");
    when(credentials.required("SHOPIFY_ACCESS_TOKEN", "cs")).thenReturn("fixture-private-token");
    return new ShopifyAdapter(credentials, http, policy);
  }

  @Test
  void adapterExecutesBothOriginalPaymentParentsInEURWithoutOverRefunding() throws Exception {
    var registry = new OperationRegistry(mock(GatewayStore.class));
    var o = order();
    var p = params();
    var quote = ShopifyRefundMethods.buildQuote(o, p);
    p.putAll(
        JsonUtils.obj(
            "amount",
            "100.00",
            "currency",
            "EUR",
            "quote_hash",
            quote.get("quote_hash"),
            "confirm",
            true,
            "idempotency_key",
            "fixture-original"));
    var input = new AtomicReference<Map<String, Object>>();
    var http = mock(UpstreamHttp.class);
    when(http.object(
            eq("POST"),
            contains("fixture.myshopify.com/admin/api/2026-07/graphql.json"),
            anyMap(),
            any()))
        .thenAnswer(
            call -> {
              var body = JsonUtils.map(call.getArgument(3));
              String query = JsonUtils.str(body, "query");
              if (query.contains("query RefundPaymentContext"))
                return JsonUtils.obj("data", JsonUtils.obj("order", o));
              assertTrue(query.contains("@idempotent(key: \"fixture-original\")"));
              input.set(JsonUtils.map(JsonUtils.at(body, "variables.input")));
              return JsonUtils.obj(
                  "data",
                  JsonUtils.obj(
                      "refundCreate",
                      JsonUtils.obj(
                          "refund",
                          JsonUtils.obj("id", "gid://shopify/Refund/8"),
                          "order",
                          Map.of(),
                          "userErrors",
                          List.of())));
            });
    var policy = mock(TestWritePolicy.class);
    var adapter = adapter(http, policy);
    var op = registry.get("shopify.refunds.execute_plan");
    var result = JsonUtils.map(adapter.invoke(op, registry.validate(op, p), "cs"));
    assertEquals(false, result.get("mock"));
    assertEquals("EUR", input.get().get("currency"));
    var txs = JsonUtils.list(input.get().get("transactions"));
    assertEquals(2, txs.size());
    assertEquals("gid://shopify/OrderTransaction/1", JsonUtils.map(txs.getFirst()).get("parentId"));
    assertEquals("70.00", JsonUtils.map(txs.getFirst()).get("amount"));
    assertEquals("shopify_store_credit", JsonUtils.map(txs.get(1)).get("gateway"));
    assertFalse(input.get().containsKey("allowOverRefunding"));
    verify(policy).requireTarget("shopify", "fixture.myshopify.com");
  }

  @Test
  void creditRefundIncludesApprovedShippingAndNoCashTransactions() throws Exception {
    var f =
        fixtures().stream()
            .filter(v -> v.get("name").equals("shipping with tax excluded"))
            .findFirst()
            .orElseThrow();
    var o = copy(f.get("order"));
    var p = copy(f.get("params"));
    p.put("target", "store_credit");
    var q = ShopifyRefundMethods.buildQuote(o, p);
    p.putAll(
        JsonUtils.obj(
            "amount",
            "100.00",
            "currency",
            "EUR",
            "quote_hash",
            q.get("quote_hash"),
            "confirm",
            true,
            "idempotency_key",
            "fixture-credit"));
    var sent = new AtomicReference<Map<String, Object>>();
    var service =
        new ShopifyRefundMethods(
            (document, variables) -> {
              if (document.startsWith("\nquery RefundPaymentContext")
                  || document.contains("query RefundPaymentContext"))
                return JsonUtils.obj("order", o);
              sent.set(JsonUtils.map(variables.get("input")));
              return JsonUtils.obj(
                  "refundCreate",
                  JsonUtils.obj(
                      "refund",
                      JsonUtils.obj("id", "gid://shopify/Refund/8"),
                      "userErrors",
                      List.of()));
            });
    service.execute(p);
    assertEquals(Map.of("amount", "9.00"), sent.get().get("shipping"));
    assertFalse(sent.get().containsKey("transactions"));
    assertEquals(
        "100.00", JsonUtils.at(sent.get(), "refundMethods.0.storeCreditRefund.amount.amount"));
    assertEquals(
        "EUR", JsonUtils.at(sent.get(), "refundMethods.0.storeCreditRefund.amount.currencyCode"));
  }

  @Test
  void changedHashUnsplitBonusAndManualPaymentsNeverReachMutation() throws Exception {
    for (String mode : List.of("changed", "bonus", "manual")) {
      var o = order();
      var p = params();
      if (mode.equals("bonus"))
        p.putAll(JsonUtils.obj("target", "store_credit", "amount", "120.00"));
      if (mode.equals("manual"))
        JsonUtils.map(JsonUtils.at(o, "full.suggestedTransactions.0.parentTransaction"))
            .put("manualPaymentGateway", true);
      var q = ShopifyRefundMethods.buildQuote(o, p);
      p.putAll(
          JsonUtils.obj(
              "amount",
              q.get("amount"),
              "currency",
              "EUR",
              "quote_hash",
              q.get("quote_hash"),
              "confirm",
              true,
              "idempotency_key",
              "fixture-blocked"));
      if (mode.equals("changed")) p.put("quote_hash", "0".repeat(64));
      int[] calls = {0};
      var service =
          new ShopifyRefundMethods(
              (document, variables) -> {
                calls[0]++;
                assertTrue(document.contains("query RefundPaymentContext"));
                return JsonUtils.obj("order", o);
              });
      assertThrows(GatewayException.class, () -> service.execute(p));
      assertEquals(1, calls[0]);
    }
  }

  @Test
  void extraCreditChecksOrderOwnerAndUsesAnIndependentCreditMutation() {
    var p =
        JsonUtils.obj(
            "order_id",
            "gid://shopify/Order/1",
            "customer_id",
            "gid://shopify/Customer/2",
            "currency",
            "EUR",
            "amount",
            "20.00",
            "confirm",
            true);
    var sent = new AtomicReference<Map<String, Object>>();
    var service =
        new ShopifyRefundMethods(
            (document, variables) -> {
              if (document.contains("query CreditOwner"))
                return JsonUtils.obj(
                    "order",
                    JsonUtils.obj(
                        "customer",
                        Map.of("id", p.get("customer_id")),
                        "presentmentCurrencyCode",
                        "EUR"));
              assertTrue(document.contains("storeCreditAccountCredit"));
              assertFalse(document.contains("refundCreate"));
              sent.set(variables);
              return JsonUtils.obj(
                  "storeCreditAccountCredit",
                  JsonUtils.obj(
                      "storeCreditAccountTransaction",
                      JsonUtils.obj(
                          "id",
                          "gid://shopify/StoreCreditAccountCreditTransaction/9",
                          "amount",
                          JsonUtils.obj("amount", "20.0", "currencyCode", "EUR")),
                      "userErrors",
                      List.of()));
            });
    service.credit(p);
    assertEquals("20.00", JsonUtils.at(sent.get(), "input.creditAmount.amount"));
    assertEquals("gid://shopify/Customer/2", sent.get().get("id"));
    var wrong =
        new ShopifyRefundMethods(
            (document, variables) -> {
              assertTrue(document.contains("query CreditOwner"));
              return JsonUtils.obj(
                  "order",
                  JsonUtils.obj(
                      "customer",
                      Map.of("id", "gid://shopify/Customer/3"),
                      "presentmentCurrencyCode",
                      "EUR"));
            });
    assertThrows(GatewayException.class, () -> wrong.credit(p));
  }

  @Test
  void simulationNeverInventsSuccessfulFinancialReceipts() throws Exception {
    var registry = new OperationRegistry(mock(GatewayStore.class));
    for (String key :
        List.of(
            "shopify.refunds.quote",
            "shopify.refunds.execute_plan",
            "shopify.store_credit.credit")) {
      var result = JsonUtils.map(new MockAdapter().invoke(registry.get(key), Map.of(), "cs"));
      assertEquals(false, result.get("ok"));
      assertEquals(true, result.get("mock"));
      assertFalse(result.containsKey("refund"));
    }
  }
}
