package group.cyt.common.module.adapter.shopify;

import static group.cyt.common.module.adapter.shopify.ShopifyQuery.*;

import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.http.UpstreamHttp;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.adapter.PlatformAdapter;
import group.cyt.common.module.adapter.support.TestWritePolicy;
import group.cyt.common.module.credential.service.CredentialService;
import group.cyt.common.module.registry.model.OperationDefinition;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ShopifyAdapter implements PlatformAdapter {
  private record Client(String store, String url, Map<String, String> headers) {}

  private final CredentialService credentials;
  private final UpstreamHttp http;
  private final TestWritePolicy writePolicy;

  public ShopifyAdapter(
      CredentialService credentials, UpstreamHttp http, TestWritePolicy writePolicy) {
    this.credentials = credentials;
    this.http = http;
    this.writePolicy = writePolicy;
  }

  public boolean supports(String platform) {
    return platform.equals("shopify");
  }

  private static String normalize(String s) {
    return s == null ? "" : s.trim().replaceFirst("^https?://", "").replaceAll("/+$", "");
  }

  public Object invoke(OperationDefinition op, Map<String, Object> p, String system) {
    String requested = normalize(JsonUtils.str(p, "store"));
    // 一个调用方身份按请求里的店铺自动取该店铺的专属凭据：上线时只需配置
    // SHOPIFY_<店铺>_STORE / _ACCESS_TOKEN，不必为每个店铺登记一个接入系统。
    // 找不到对应店铺凭据时保持原行为（下面按「店铺不一致」拒绝），不会串到别的店。
    String credentialSystem = system;
    if (!requested.isBlank()
        && !normalize(credentials.resolve("SHOPIFY_STORE", system)).equalsIgnoreCase(requested)) {
      String scoped = credentials.systemForStore("SHOPIFY_STORE", requested);
      if (scoped != null) credentialSystem = scoped;
    }
    if (credentialSystem.startsWith("cs_") && !system.equals("cs"))
      throw GatewayException.forbidden("此 Shopify 店铺授权仅供客服系统使用");
    String actual = normalize(credentials.required("SHOPIFY_STORE", credentialSystem));
    if (!actual.matches("[A-Za-z0-9][A-Za-z0-9.-]*"))
      throw GatewayException.config("Shopify 店铺域名无效");
    if (!requested.isBlank() && !actual.equalsIgnoreCase(requested))
      throw GatewayException.forbidden("请求店铺与实际授权店铺不一致");
    String version = credentials.resolve("SHOPIFY_API_VERSION", credentialSystem);
    if (version.isBlank()) version = "2026-07";
    if (!version.matches("[0-9]{4}-[0-9]{2}")) throw GatewayException.config("Shopify API 版本格式无效");
    Client c =
        new Client(
            actual,
            "https://" + actual + "/admin/api/" + version + "/graphql.json",
            Map.of(
                "X-Shopify-Access-Token",
                ShopifyStoreAccess.token(credentials, http, credentialSystem, actual, false)));
    String key = op.key().substring(8);
    if (!op.readOnly()) writePolicy.requireTarget("shopify", requested);
    return switch (key) {
      case "orders.get" -> getOrder(c, p);
      case "products.get" ->
          JsonUtils.obj(
              "product",
              query(c, PRODUCT, JsonUtils.obj("id", p.get("product_id"))).get("product"),
              "mock",
              false);
      case "orders.list", "products.list", "returns.list", "refunds.list" -> list(c, key, p);
      case "orders.update" -> {
        var input = JsonUtils.obj("id", p.get("order_id"));
        for (String field : List.of("note", "tags"))
          if (p.get(field) != null) input.put(field, p.get(field));
        if (p.get("shipping_address") != null)
          input.put("shippingAddress", address(JsonUtils.map(p.get("shipping_address"))));
        var r = block(query(c, ORDER_UPDATE, JsonUtils.obj("input", input)), "orderUpdate");
        yield JsonUtils.obj("order", r.get("order"), "mock", false);
      }
      case "inventory.set" -> {
        var input =
            JsonUtils.obj(
                "name",
                "available",
                "reason",
                p.get("reason"),
                "quantities",
                List.of(
                    JsonUtils.obj(
                        "inventoryItemId",
                        p.get("inventory_item_id"),
                        "locationId",
                        p.get("location_id"),
                        "quantity",
                        p.get("available"))));
        block(query(c, INVENTORY, JsonUtils.obj("input", input)), "inventorySetQuantities");
        yield JsonUtils.obj("ok", true, "errors", List.of(), "mock", false);
      }
      case "orders.create" -> create(c, p);
      case "orders.cancel" -> cancelOrder(c, p);
      case "refunds.create" -> refund(c, p);
      case "refunds.quote" -> new ShopifyRefundMethods((q, v) -> graphql(c, q, v)).quote(p);
      case "refunds.execute_plan" ->
          new ShopifyRefundMethods((q, v) -> graphql(c, q, v)).execute(p);
      case "store_credit.credit" -> new ShopifyRefundMethods((q, v) -> graphql(c, q, v)).credit(p);
      default -> throw GatewayException.config("未知 Shopify 操作: " + key);
    };
  }

  private Object cancelOrder(Client c, Map<String, Object> p) {
    String id = JsonUtils.str(p, "order_id");
    String method = JsonUtils.str(p, "refund_method");
    var snapshot = query(c, ORDER_CANCEL_STATE, JsonUtils.obj("id", id));
    var order = JsonUtils.map(snapshot.get("order"));
    if (!c.store().equalsIgnoreCase(JsonUtils.str(JsonUtils.map(snapshot.get("shop")), "myshopifyDomain"))
        || !id.equals(order.get("id")))
      return cancellationRejected(c, id, "无法在目标 Shopify 店铺确认该订单");
    if (!Objects.equals(p.get("updated_at"), order.get("updatedAt")))
      return cancellationRejected(c, id, "Shopify 订单已更新，请重新核对");
    if (!order.containsKey("cancelledAt") || order.get("cancelledAt") != null
        || !"UNFULFILLED".equals(order.get("displayFulfillmentStatus"))
        || !(order.get("fulfillments") instanceof List<?> fulfillments)
        || fulfillments.stream().anyMatch(entry -> !Set.of("CANCELLED", "FAILURE", "ERROR")
            .contains(JsonUtils.str(JsonUtils.map(entry), "status"))))
      return cancellationRejected(c, id, "订单已取消或已进入履约流程");
    if (!"later".equals(method)) {
      String currency = JsonUtils.str(p, "currency");
      if (!"PAID".equals(order.get("displayFinancialStatus"))
          || currency.isBlank() || !refundAmountMatches(order, currency, JsonUtils.str(p, "amount")))
        return cancellationRejected(c, id, "可退款金额已变化或订单已有退款，请重新核对");
      if ("credit".equals(method) && JsonUtils.str(JsonUtils.map(order.get("customer")), "id").isBlank())
        return cancellationRejected(c, id, "店铺余额退款需要关联 Shopify 客户");
    }
    var refundMethod = switch (method) {
      case "original" -> JsonUtils.obj("originalPaymentMethodsRefund", true);
      case "credit" -> JsonUtils.obj("storeCreditRefund", Map.of());
      case "later" -> JsonUtils.obj("originalPaymentMethodsRefund", false);
      default -> throw GatewayException.config("未知 Shopify 取消退款方式");
    };
    var response = query(c, ORDER_CANCEL, JsonUtils.obj("orderId", id,
        "reason", p.get("reason"), "refundMethod", refundMethod, "restock", p.get("restock"),
        "notifyCustomer", p.get("notify_customer"), "staffNote", p.get("staff_note")));
    var result = JsonUtils.map(response.get("orderCancel"));
    var errors = JsonUtils.list(result.get("orderCancelUserErrors"));
    if (errors.isEmpty()) errors = JsonUtils.list(result.get("userErrors"));
    if (!errors.isEmpty())
      return cancellationRejected(c, id, JsonUtils.str(JsonUtils.map(errors.getFirst()), "message"));
    var job = JsonUtils.map(result.get("job"));
    if (JsonUtils.str(job, "id").isBlank())
      throw new GatewayException(502, "upstream_error", "Shopify 未确认取消任务", false);
    return JsonUtils.obj("order_id", id, "store", c.store(), "accepted", true, "job", job, "mock", false);
  }

  private static Object cancellationRejected(Client c, String id, String message) {
    return JsonUtils.obj("order_id", id, "store", c.store(), "accepted", false, "message", message, "mock", false);
  }

  private static boolean refundAmountMatches(Map<String, Object> order, String currency, String expected) {
    if (!currency.equals(JsonUtils.at(order, "totalReceivedSet.presentmentMoney.currencyCode"))
        || !currency.equals(JsonUtils.at(order, "totalRefundedSet.presentmentMoney.currencyCode")))
      return false;
    try {
      var received = new BigDecimal(Objects.toString(JsonUtils.at(order, "totalReceivedSet.presentmentMoney.amount"), ""));
      var refunded = new BigDecimal(Objects.toString(JsonUtils.at(order, "totalRefundedSet.presentmentMoney.amount"), ""));
      return received.signum() > 0 && refunded.signum() == 0
          && received.compareTo(new BigDecimal(expected)) == 0;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  private Map<String, Object> query(Client c, ShopifyQuery query, Map<String, Object> variables) {
    return graphql(c, query.document(), variables);
  }

  private Map<String, Object> graphql(Client c, String query, Map<String, Object> variables) {
    var result =
        http.object(
            "POST", c.url(), c.headers(), JsonUtils.obj("query", query, "variables", variables));
    if (!JsonUtils.list(result.get("errors")).isEmpty())
      throw new GatewayException(502, "upstream_error", "Shopify GraphQL 调用失败，请核对应用权限与参数", false);
    return JsonUtils.map(result.get("data"));
  }

  private Object getOrder(Client c, Map<String, Object> p) {
    var value = query(c, ORDER, JsonUtils.obj("id", p.get("order_id"))).get("order");
    return decorateOrderRead(JsonUtils.obj("order", value, "mock", false), c, "orders.get", p);
  }

  private Object decorateOrderRead(Object value, Client c, String key, Map<String, Object> p) {
    if (!key.equals("orders.get") && !key.equals("orders.list")) return value;
    var result = new LinkedHashMap<>(JsonUtils.map(value));
    int[] budget = {20};
    if (key.equals("orders.get")) {
      Object order = result.get("order");
      if (order instanceof Map<?, ?> && !Objects.equals(p.get("order_id"), JsonUtils.map(order).get("id")))
        throw new GatewayException(502, "upstream_error", "Shopify 返回订单与请求订单不一致", false);
      result.put("order", completeOrderLines(c, order, budget));
    } else {
      List<Object> items = new ArrayList<>();
      for (Object item : JsonUtils.list(result.get("items"))) items.add(completeOrderLines(c, item, budget));
      result.put("items", items);
    }
    result.put("store", c.store());
    return result;
  }

  private Map<String, Object> incompleteLines(Map<String, Object> order, String reason) {
    order.put("line_snapshot", JsonUtils.obj("complete", false, "reason", reason));
    return order;
  }

  /** Fetch a bounded, version-consistent full snapshot without changing the outer order cursor. */
  private Object completeOrderLines(Client c, Object value, int[] budget) {
    if (!(value instanceof Map<?, ?>)) return value;
    var order = new LinkedHashMap<>(JsonUtils.map(value));
    String id = JsonUtils.str(order, "id"), version = JsonUtils.str(order, "updatedAt");
    if (!id.matches("gid://shopify/Order/[1-9][0-9]*"))
      return incompleteLines(order, "order_identity_or_version_missing");
    try {
      OffsetDateTime.parse(version);
    } catch (DateTimeParseException ex) {
      return incompleteLines(order, "order_identity_or_version_missing");
    }
    if (!(order.get("lineItems") instanceof Map<?, ?>))
      return incompleteLines(order, "line_connection_missing");
    var connection = JsonUtils.map(order.get("lineItems"));
    if (!(connection.get("edges") instanceof List<?>))
      return incompleteLines(order, "line_connection_missing");
    if (Boolean.TRUE.equals(JsonUtils.map(connection.get("pageInfo")).get("hasPreviousPage")))
      return incompleteLines(order, "line_snapshot_starts_after_first_page");
    List<Object> edges = new ArrayList<>(JsonUtils.list(connection.get("edges")));
    Set<String> cursors = new HashSet<>();
    while (true) {
      var page = JsonUtils.map(connection.get("pageInfo"));
      if (!(page.get("hasNextPage") instanceof Boolean))
        return incompleteLines(order, "line_page_info_missing");
      if (Boolean.FALSE.equals(page.get("hasNextPage"))) break;
      String after = JsonUtils.str(page, "endCursor");
      if (after.isBlank() || !cursors.add(after))
        return incompleteLines(order, "line_cursor_missing_or_repeated");
      if (budget[0] <= 0 || edges.size() >= 10000)
        return incompleteLines(order, "line_pagination_limit");
      budget[0]--;
      var next = query(c, ORDER_LINES, JsonUtils.obj("id", id, "after", after)).get("order");
      if (!(next instanceof Map<?, ?>) || !id.equals(JsonUtils.map(next).get("id")))
        return incompleteLines(order, "line_order_identity_changed");
      if (!version.equals(JsonUtils.map(next).get("updatedAt")))
        return incompleteLines(order, "order_changed_during_line_pagination");
      if (!(JsonUtils.map(next).get("lineItems") instanceof Map<?, ?>))
        return incompleteLines(order, "line_page_missing");
      connection = JsonUtils.map(JsonUtils.map(next).get("lineItems"));
      if (!(connection.get("edges") instanceof List<?>))
        return incompleteLines(order, "line_page_missing");
      edges.addAll(JsonUtils.list(connection.get("edges")));
    }
    Set<String> identities = new HashSet<>();
    for (Object edge : edges) {
      var item = JsonUtils.map(JsonUtils.map(edge).get("node"));
      if (!item.keySet().containsAll(Set.of("id", "title", "sku", "quantity", "originalUnitPriceSet", "variant")))
        return incompleteLines(order, "line_fields_missing");
      Object quantity = item.get("quantity");
      if (!JsonUtils.str(item, "id").matches("gid://shopify/LineItem/[1-9][0-9]*")
          || !(quantity instanceof Integer || quantity instanceof Long)
          || ((Number) quantity).longValue() < 0)
        return incompleteLines(order, "line_identity_or_quantity_invalid");
      if (!identities.add(JsonUtils.str(item, "id")) || edges.size() > 10000)
        return incompleteLines(order, "line_identity_duplicate_or_limit");
    }
    var finalPage = new LinkedHashMap<>(JsonUtils.map(connection.get("pageInfo")));
    finalPage.put("hasNextPage", false);
    finalPage.put("hasPreviousPage", false);
    order.put("lineItems", JsonUtils.obj("edges", edges, "pageInfo", finalPage));
    order.put("line_snapshot", JsonUtils.obj("complete", true, "count", edges.size()));
    return order;
  }

  private Object list(Client c, String key, Map<String, Object> p) {
    ShopifyQuery template =
        switch (key) {
          case "orders.list" -> ORDERS;
          case "products.list" -> PRODUCTS;
          case "returns.list" -> RETURNS;
          default -> REFUNDS;
        };
    var variables =
        JsonUtils.obj("first", p.get("limit"), "query", p.get("query"), "after", p.get("after"));
    if (key.equals("orders.list") || key.equals("products.list")) {
      variables.put("sortKey", p.get("sort_key"));
      variables.put("reverse", p.get("reverse"));
    }
    if (key.equals("products.list") && p.get("limit") instanceof Number n) {
      // _PRODUCTS_QUERY 每页只取 50 个商品：嵌套的 first 相乘计费（50 商品 × 10 变体），
      // 再大会撞 Shopify 单查询 1000 点上限。调用方要 250 也只给 50，翻页靠 endCursor。
      variables.put("first", Math.min(50, n.intValue()));
    }
    var result = query(c, template, variables);
    String resource = key.split("\\.")[0];
    var connection = JsonUtils.map(result.get(resource.equals("refunds") ? "orders" : resource));
    List<Object> items = new ArrayList<>();
    int[] lineBudget = {20};
    for (Object edge : JsonUtils.list(connection.get("edges"))) {
      var node = JsonUtils.map(JsonUtils.map(edge).get("node"));
      if (resource.equals("orders")) items.add(completeOrderLines(c, node, lineBudget));
      else if (!resource.equals("refunds")) items.add(node);
      else
        for (Object value : JsonUtils.list(node.get("refunds"))) {
          var refund = new LinkedHashMap<>(JsonUtils.map(value));
          refund.put(
              "transactions",
              JsonUtils.list(JsonUtils.map(refund.get("transactions")).get("edges")).stream()
                  .map(x -> JsonUtils.map(x).get("node"))
                  .toList());
          refund.put(
              "order",
              JsonUtils.obj(
                  "id",
                  node.get("id"),
                  "name",
                  node.get("name"),
                  "created_at",
                  node.get("createdAt"),
                  "updated_at",
                  node.get("updatedAt"),
                  "financial_status",
                  node.get("displayFinancialStatus"),
                  "customer",
                  node.get("customer"),
                  "total_refunded_set",
                  node.get("totalRefundedSet")));
          items.add(refund);
        }
    }
    return JsonUtils.obj(
        "items", items, "page_info", connection.get("pageInfo"), "store", c.store(), "mock", false);
  }

  private Object create(Client c, Map<String, Object> p) {
    String tag = "cs-reship-" + JsonUtils.str(p, "reference");
    var existing =
        query(
            c,
            ORDERS,
            JsonUtils.obj(
                "first",
                2,
                "query",
                "tag:" + tag,
                "after",
                null,
                "sortKey",
                "CREATED_AT",
                "reverse",
                false));
    var edges = JsonUtils.list(JsonUtils.map(existing.get("orders")).get("edges"));
    if (edges.size() > 1) throw new GatewayException(409, "conflict", "存在多个同编号补发订单，需人工核对", false);
    if (!edges.isEmpty()) {
      var old = JsonUtils.map(JsonUtils.map(edges.getFirst()).get("node"));
      return JsonUtils.obj("id", old.get("id"), "order", old, "duplicate", true, "mock", false);
    }
    List<Object> lines = new ArrayList<>();
    for (Object v : JsonUtils.list(p.get("line_items"))) {
      var line = JsonUtils.map(v);
      String sku = JsonUtils.str(line, "sku");
      String escaped = sku.replace("\\", "\\\\").replace("\"", "\\\"");
      var variants = query(c, VARIANT_SKU, JsonUtils.obj("query", "sku:\"" + escaped + "\""));
      var matches =
          JsonUtils.list(JsonUtils.map(variants.get("productVariants")).get("nodes")).stream()
              .map(JsonUtils::map)
              .filter(x -> sku.equals(x.get("sku")))
              .toList();
      if (matches.size() != 1) throw GatewayException.invalid("SKU 未唯一匹配店铺商品，拒绝创建补发单");
      lines.add(
          JsonUtils.obj(
              "variantId",
              matches.getFirst().get("id"),
              "quantity",
              line.get("quantity"),
              "priceSet",
              JsonUtils.obj(
                  "shopMoney",
                  JsonUtils.obj("amount", "0.00", "currencyCode", p.get("currency")))));
    }
    var address = address(JsonUtils.map(p.get("shipping_address")));
    if (JsonUtils.str(address, "address1").isBlank()
        || JsonUtils.str(address, "countryCode").isBlank())
      throw GatewayException.invalid("补发收货地址和国家不能为空");
    var order =
        JsonUtils.obj(
            "email",
            p.get("customer_email"),
            "currency",
            p.get("currency"),
            "financialStatus",
            "PAID",
            "shippingAddress",
            address,
            "lineItems",
            lines,
            "tags",
            List.of(tag, "CS-RESHIP"),
            "sourceName",
            "cyt-cs-reship",
            "sourceIdentifier",
            p.get("reference"),
            "note",
            "Replacement for " + p.get("reference"));
    var result =
        block(
            query(
                c,
                ORDER_CREATE,
                JsonUtils.obj(
                    "order",
                    order,
                    "options",
                    JsonUtils.obj("sendReceipt", false, "sendFulfillmentReceipt", false))),
            "orderCreate");
    var created = JsonUtils.map(result.get("order"));
    if (JsonUtils.str(created, "id").isBlank())
      throw new GatewayException(502, "upstream_error", "Shopify 未确认补发创建成功", false);
    return JsonUtils.obj("id", created.get("id"), "order", created, "mock", false);
  }

  private Object refund(Client c, Map<String, Object> p) {
    var pre = query(c, REFUND_PREFLIGHT, JsonUtils.obj("id", p.get("order_id")));
    var order = JsonUtils.map(pre.get("order"));
    if (order.isEmpty()) throw GatewayException.missing("订单不存在");
    BigDecimal total = money(order, "totalPriceSet"),
        refunded = money(order, "totalRefundedSet"),
        amount = new BigDecimal(JsonUtils.str(p, "amount"));
    if (amount.compareTo(total.subtract(refunded)) > 0)
      throw GatewayException.invalid("退款金额超过订单可退余额");
    var sale =
        JsonUtils.list(order.get("transactions")).stream()
            .map(JsonUtils::map)
            .filter(
                t ->
                    JsonUtils.str(t, "kind").equals("SALE")
                        && JsonUtils.str(t, "status").equals("SUCCESS"))
            .reduce((a, b) -> b)
            .orElseThrow(() -> GatewayException.invalid("该订单没有成功的 SALE 流水，无法退款"));
    var input =
        JsonUtils.obj(
            "orderId",
            p.get("order_id"),
            "notify",
            p.get("notify"),
            "transactions",
            List.of(
                JsonUtils.obj(
                    "orderId",
                    p.get("order_id"),
                    "kind",
                    "REFUND",
                    "gateway",
                    sale.get("gateway"),
                    "parentId",
                    sale.get("id"),
                    "amount",
                    amount.toPlainString())));
    if (!JsonUtils.str(p, "note").isBlank()) input.put("note", p.get("note"));
    if (JsonUtils.bool(p.get("restock"))) {
      List<Object> lines = new ArrayList<>();
      for (Object edge : JsonUtils.list(JsonUtils.map(order.get("lineItems")).get("edges"))) {
        var node = JsonUtils.map(JsonUtils.map(edge).get("node"));
        int quantity =
            JsonUtils.integer(
                node.get("currentQuantity"), JsonUtils.integer(node.get("quantity"), 0));
        if (quantity > 0)
          lines.add(
              JsonUtils.obj(
                  "lineItemId", node.get("id"), "quantity", quantity, "restockType", "RETURN"));
      }
      if (!lines.isEmpty()) input.put("refundLineItems", lines);
    }
    String key = JsonUtils.str(p, "idempotency_key");
    if (key.isBlank()) key = UUID.randomUUID().toString();
    if (!key.matches("[A-Za-z0-9_-]{1,64}")) throw GatewayException.invalid("退款幂等键格式无效");
    String mutation = REFUND_CREATE.document().replace("__KEY__", key);
    var result = block(graphql(c, mutation, JsonUtils.obj("input", input)), "refundCreate");
    return JsonUtils.obj(
        "refund",
        result.get("refund"),
        "order",
        result.get("order"),
        "requested",
        JsonUtils.obj(
            "amount",
            amount.toPlainString(),
            "currency",
            JsonUtils.at(order, "totalPriceSet.shopMoney.currencyCode"),
            "remaining_before",
            total.subtract(refunded).toPlainString()),
        "mock",
        false);
  }

  private static BigDecimal money(Map<String, Object> order, String field) {
    return new BigDecimal(Objects.toString(JsonUtils.at(order, field + ".shopMoney.amount"), "0"));
  }

  private static Map<String, Object> block(Map<String, Object> data, String name) {
    var result = JsonUtils.map(data.get(name));
    if (!JsonUtils.list(result.get("userErrors")).isEmpty())
      throw new GatewayException(502, "upstream_error", "Shopify 拒绝执行操作", false)
          .detail(result.get("userErrors"));
    return result;
  }

  private static Map<String, Object> address(Map<String, Object> source) {
    var out = new LinkedHashMap<>(source);
    out.values().removeIf(Objects::isNull);
    String name = JsonUtils.str(out, "name").trim();
    out.remove("name");
    if (!name.isBlank()) {
      int split = name.lastIndexOf(' ');
      out.put("firstName", split < 0 ? name : name.substring(0, split).trim());
      if (split >= 0) out.put("lastName", name.substring(split + 1).trim());
    }
    return out;
  }
}
