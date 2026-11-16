package group.cyt.common.module.adapter.shopify;

import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.util.JsonUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/** Customer-currency payment plans, compatible with Python common-api a65dedb. */
public final class ShopifyRefundMethods {
  @FunctionalInterface
  public interface Graphql {
    Map<String, Object> query(String document, Map<String, Object> variables);
  }

  private static final BigDecimal ZERO = new BigDecimal("0.00");
  private static final Map<String, Object> QUERIES;
  private final Graphql graphql;

  static {
    try (var input =
        ShopifyRefundMethods.class.getResourceAsStream("/shopify-refund-queries.json")) {
      QUERIES =
          JsonUtils.MAPPER.readValue(
              input, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public ShopifyRefundMethods(Graphql graphql) {
    this.graphql = graphql;
  }

  public Map<String, Object> quote(Map<String, Object> p) {
    var items = items(p);
    var upstream =
        items.stream()
            .map(
                item ->
                    JsonUtils.obj(
                        "lineItemId",
                        item.get("line_item_id"),
                        "quantity",
                        item.get("quantity"),
                        "restockType",
                        "NO_RESTOCK"))
            .toList();
    var data =
        graphql.query(
            JsonUtils.str(QUERIES, "context"),
            JsonUtils.obj(
                "id",
                p.get("order_id"),
                "items",
                upstream,
                "shipping",
                Boolean.TRUE.equals(p.get("refund_shipping")),
                "allItems",
                items.isEmpty()));
    var order = JsonUtils.map(data.get("order"));
    if (order.isEmpty()) throw GatewayException.missing("Shopify 订单不存在");
    var result = buildQuote(order, p);
    result.put("mock", false);
    return result;
  }

  public Map<String, Object> execute(Map<String, Object> p) {
    confirm(p);
    var current = quote(p);
    if (!Objects.equals(current.get("quote_hash"), p.get("quote_hash")))
      throw GatewayException.invalid("支付或余额已变化，请重新预览并审批");
    if (money(current.get("bonus_credit_amount")).signum() > 0)
      throw GatewayException.invalid("额外 Credit 必须独立发放并单独记录凭证，不能写成订单超额退款");
    var allocations = maps(current.get("allocations"));
    if (allocations.stream().anyMatch(tx -> Boolean.TRUE.equals(tx.get("manual"))))
      throw GatewayException.invalid("原支付包含人工收款渠道，须由财务核对实际出款");
    var payload =
        JsonUtils.obj(
            "orderId",
            p.get("order_id"),
            "currency",
            p.get("currency"),
            "notify",
            p.getOrDefault("notify", false),
            "note",
            p.getOrDefault("note", ""));
    if (Boolean.TRUE.equals(p.get("refund_shipping")))
      payload.put("shipping", JsonUtils.obj("amount", current.get("shipping_refund_amount")));
    if ("store_credit".equals(current.get("target")))
      payload.put(
          "refundMethods",
          List.of(
              JsonUtils.obj(
                  "storeCreditRefund",
                  JsonUtils.obj(
                      "amount",
                      JsonUtils.obj(
                          "amount", current.get("amount"), "currencyCode", p.get("currency"))))));
    else
      payload.put(
          "transactions",
          allocations.stream()
              .map(
                  tx ->
                      JsonUtils.obj(
                          "orderId",
                          p.get("order_id"),
                          "kind",
                          "REFUND",
                          "gateway",
                          tx.get("gateway"),
                          "parentId",
                          tx.get("parent_id"),
                          "amount",
                          tx.get("amount")))
              .toList());
    String key = JsonUtils.str(p, "idempotency_key");
    if (!key.matches("[A-Za-z0-9_-]{1,64}")) throw GatewayException.invalid("退款幂等键格式无效");
    var block =
        block(
            graphql.query(
                JsonUtils.str(QUERIES, "refund").replace("__KEY__", key),
                JsonUtils.obj("input", payload)),
            "refundCreate");
    if (JsonUtils.str(JsonUtils.map(block.get("refund")), "id").isBlank())
      throw upstream("Shopify 未确认退款成功");
    block.put("requested", current);
    block.put("mock", false);
    return block;
  }

  public Map<String, Object> credit(Map<String, Object> p) {
    confirm(p);
    var amount = positive(p.get("amount"));
    var data =
        graphql.query(
            "query CreditOwner($id: ID!) { order(id: $id) { customer { id } presentmentCurrencyCode } }",
            JsonUtils.obj("id", p.get("order_id")));
    var order = JsonUtils.map(data.get("order"));
    if (!Objects.equals(JsonUtils.at(order, "customer.id"), p.get("customer_id"))
        || !Objects.equals(order.get("presentmentCurrencyCode"), p.get("currency")))
      throw GatewayException.invalid("额外 Credit 的客户或币种与订单不一致");
    var block =
        block(
            graphql.query(
                JsonUtils.str(QUERIES, "credit"),
                JsonUtils.obj(
                    "id",
                    p.get("customer_id"),
                    "input",
                    JsonUtils.obj(
                        "creditAmount",
                        JsonUtils.obj(
                            "amount", amount.toPlainString(), "currencyCode", p.get("currency"))))),
            "storeCreditAccountCredit");
    var tx = JsonUtils.map(block.get("storeCreditAccountTransaction"));
    if (JsonUtils.str(tx, "id").isBlank()
        || !Objects.equals(JsonUtils.at(tx, "amount.currencyCode"), p.get("currency"))
        || money(JsonUtils.at(tx, "amount.amount")).compareTo(amount) != 0)
      throw upstream("Shopify 未确认额外 Credit 发放成功");
    block.put("mock", false);
    return block;
  }

  public static Map<String, Object> buildQuote(Map<String, Object> order, Map<String, Object> p) {
    String currency = JsonUtils.str(order, "presentmentCurrencyCode");
    if (currency.isBlank() || p.get("currency") != null && !currency.equals(p.get("currency")))
      throw GatewayException.invalid("退款币种必须是订单客户实际支付币种");
    var paid = bag(order.get("totalReceivedSet"), currency);
    var refunded = bag(order.get("totalRefundedSet"), currency);
    var full = JsonUtils.map(order.get("full"));
    var selected = JsonUtils.map(order.get("selected"));
    String shopCurrency =
        Objects.toString(JsonUtils.at(order, "totalPriceSet.shopMoney.currencyCode"), "");
    if (shopCurrency.isBlank()) throw GatewayException.invalid("缺少店铺本位币信息，请同步核对");
    var shopMoney = new LinkedHashMap<String, Object>();
    for (String[] field :
        List.of(
            new String[] {"total", "totalPriceSet"},
            new String[] {"paid", "totalReceivedSet"},
            new String[] {"refunded", "totalRefundedSet"}))
      shopMoney.put(field[0], paired(order.get(field[1]), currency, shopCurrency));
    var items = items(p);
    var source = items.isEmpty() ? full : selected;
    var calculation = new LinkedHashMap<String, Object>();
    for (String[] field :
        List.of(
            new String[] {"subtotal", "subtotalSet"},
            new String[] {"discounted_subtotal", "discountedSubtotalSet"},
            new String[] {"cart_discount", "totalCartDiscountAmountSet"},
            new String[] {"tax", "totalTaxSet"},
            new String[] {"duties", "totalDutiesSet"},
            new String[] {"recommended", "amountSet"}))
      calculation.put(field[0], paired(source.get(field[1]), currency, shopCurrency));
    var shipping = JsonUtils.map(source.get("shipping"));
    for (String[] field :
        List.of(
            new String[] {"shipping", "amountSet"},
            new String[] {"shipping_tax", "taxSet"},
            new String[] {"shipping_maximum", "maximumRefundableSet"}))
      calculation.put(field[0], paired(shipping.get(field[1]), currency, shopCurrency));
    boolean refundShipping = Boolean.TRUE.equals(p.get("refund_shipping"));
    var shippingAmount =
        refundShipping ? money(JsonUtils.at(calculation, "shipping.customer")) : ZERO;
    var shippingTotal =
        refundShipping
            ? bag(source.get("amountSet"), currency)
                .subtract(bag(JsonUtils.at(order, "withoutShipping.amountSet"), currency))
            : ZERO;
    if (shippingTotal.signum() < 0) throw GatewayException.invalid("Shopify 运费预计算结果不一致，请重新核对");
    String target = JsonUtils.str(p, "target", "original");
    if (!Set.of("original", "store_credit").contains(target))
      throw GatewayException.invalid("退款目标无效");
    var remaining = paid.subtract(refunded).max(ZERO);
    if (target.equals("original"))
      remaining = remaining.min(bag(full.get("maximumRefundableSet"), currency));
    var recommended = bag(source.get("amountSet"), currency);
    var amount = p.get("amount") == null ? recommended.min(remaining) : positive(p.get("amount"));
    if (amount.compareTo(shippingTotal) < 0)
      throw GatewayException.invalid("退款总额不能少于已选择的运费及运费税；请调整金额或取消退运费");
    Object customer = JsonUtils.at(order, "customer.id");
    if (target.equals("store_credit")
        && !Objects.toString(customer, "").matches("gid://shopify/Customer/[0-9]+"))
      throw GatewayException.invalid("店铺 Credit 需要订单关联的准确客户账户");
    if (target.equals("original") && amount.compareTo(remaining) > 0)
      throw GatewayException.invalid("原路退款不能超过订单当前可退余额");
    var transactions = maps(order.get("transactions"));
    if (!"EXACT".equals(JsonUtils.at(order, "transactionsCount.precision"))
        || money(JsonUtils.at(order, "transactionsCount.count"))
                .compareTo(BigDecimal.valueOf(transactions.size()))
            != 0) throw GatewayException.invalid("支付流水未完整获取，不能安全分配退款");
    var payments = new ArrayList<Map<String, Object>>();
    for (var tx : transactions) {
      if (Set.of("SALE", "CAPTURE").contains(JsonUtils.str(tx, "kind"))
          && "SUCCESS".equals(tx.get("status")))
        payments.add(
            JsonUtils.obj(
                "parent_id",
                tx.get("id"),
                "gateway",
                tx.get("gateway"),
                "label",
                label(tx),
                "paid",
                bag(tx.get("amountSet"), currency).toPlainString()));
    }
    var available = new LinkedHashMap<String, Map<String, Object>>();
    for (var tx : maps(full.get("suggestedTransactions"))) {
      var parent = JsonUtils.map(tx.get("parentTransaction"));
      if (!"SUGGESTED_REFUND".equals(tx.get("kind"))
          || !"SUCCESS".equals(parent.get("status"))
          || !Set.of("SALE", "CAPTURE").contains(JsonUtils.str(parent, "kind")))
        throw GatewayException.invalid("Shopify 未提供可核对的原支付退款建议");
      String id = JsonUtils.str(parent, "id");
      if (id.isBlank() || available.containsKey(id) || JsonUtils.str(tx, "gateway").isBlank())
        throw GatewayException.invalid("Shopify 退款建议缺少或重复原支付流水");
      available.put(
          id,
          JsonUtils.obj(
              "parent_id",
              id,
              "gateway",
              tx.get("gateway"),
              "manual",
              Boolean.TRUE.equals(parent.get("manualPaymentGateway")),
              "label",
              label(tx),
              "maximum",
              bag(tx.get("maximumRefundableSet"), currency).toPlainString(),
              "suggested",
              bag(tx.get("amountSet"), currency).toPlainString()));
    }
    var allocations = new ArrayList<Map<String, Object>>();
    if (target.equals("original")) {
      var requested = maps(p.get("allocations"));
      if (!requested.isEmpty()) {
        var seen = new HashSet<String>();
        for (var entry : requested) {
          String id = JsonUtils.str(entry, "parent_id");
          var tx = available.get(id);
          var part = money(entry.get("amount"));
          if (tx == null || !seen.add(id) || part.compareTo(money(tx.get("maximum"))) > 0)
            throw GatewayException.invalid("退款分配重复、不是原支付流水或超过该渠道可退余额");
          if (part.signum() > 0) allocations.add(withAmount(tx, part));
        }
      } else if (available.size() == 1) {
        var tx = available.values().iterator().next();
        if (amount.compareTo(money(tx.get("maximum"))) > 0)
          throw GatewayException.invalid("超过原支付渠道可退余额");
        allocations.add(withAmount(tx, amount));
      } else {
        if (amount.compareTo(bag(source.get("amountSet"), currency)) != 0
            && amount.compareTo(bag(full.get("amountSet"), currency)) == 0) source = full;
        if (amount.compareTo(bag(source.get("amountSet"), currency)) != 0)
          throw GatewayException.invalid("混合支付部分退款请明确填写每个原支付渠道的退款金额");
        for (var tx : maps(source.get("suggestedTransactions"))) {
          String id = Objects.toString(JsonUtils.at(tx, "parentTransaction.id"), "");
          var part = bag(tx.get("amountSet"), currency);
          if (!available.containsKey(id)
              || part.compareTo(money(available.get(id).get("maximum"))) > 0)
            throw GatewayException.invalid("Shopify 建议退款分配无法核对");
          if (part.signum() > 0) allocations.add(withAmount(available.get(id), part));
        }
      }
      var sum =
          allocations.stream().map(tx -> money(tx.get("amount"))).reduce(ZERO, BigDecimal::add);
      if (sum.compareTo(amount) != 0) throw GatewayException.invalid("各支付渠道退款合计必须等于本次退款金额");
    }
    allocations.sort(Comparator.comparing(tx -> JsonUtils.str(tx, "parent_id")));
    var orderAmount = amount.min(remaining);
    var plan =
        JsonUtils.obj(
            "version",
            1,
            "store",
            JsonUtils.str(p, "store").trim(),
            "order_id",
            order.get("id"),
            "target",
            target,
            "currency",
            currency,
            "amount",
            amount.toPlainString(),
            "paid",
            paid.toPlainString(),
            "refunded",
            refunded.toPlainString(),
            "remaining",
            remaining.toPlainString(),
            "customer_id",
            customer,
            "order_refund_amount",
            orderAmount.toPlainString(),
            "bonus_credit_amount",
            amount.subtract(orderAmount).toPlainString(),
            "allocations",
            allocations,
            "shipping_refund_amount",
            shippingAmount.toPlainString(),
            "shipping_refund_total",
            shippingTotal.toPlainString(),
            "calculation",
            calculation,
            "taxes_included",
            order.get("taxesIncluded"),
            "shop_currency",
            shopCurrency,
            "shop_money",
            shopMoney,
            "items",
            items,
            "refund_shipping",
            refundShipping);
    // The same input must keep its approval hash when callers switch gateway implementations.
    String hash = JsonUtils.sha(pythonJson(plan));
    plan.put("quote_hash", hash);
    plan.put("payments", payments);
    plan.put("channels", new ArrayList<>(available.values()));
    plan.put("recommended_amount", recommended.toPlainString());
    return plan;
  }

  private static Object label(Map<String, Object> tx) {
    String value = JsonUtils.str(tx, "formattedGateway");
    return value.isEmpty() ? tx.get("gateway") : value;
  }

  private static Map<String, Object> withAmount(Map<String, Object> tx, BigDecimal amount) {
    var copy = new LinkedHashMap<>(tx);
    copy.put("amount", amount.toPlainString());
    return copy;
  }

  private static List<Map<String, Object>> items(Map<String, Object> p) {
    var seen = new HashSet<String>();
    var result = new ArrayList<Map<String, Object>>();
    for (var item : maps(p.get("items"))) {
      String id = JsonUtils.str(item, "line_item_id");
      if (!id.matches("gid://shopify/LineItem/[0-9]+") || !seen.add(id))
        throw GatewayException.invalid("售后商品 ID 无效或重复");
      int qty;
      try {
        qty = new BigDecimal(Objects.toString(item.get("quantity"), "")).intValueExact();
      } catch (RuntimeException e) {
        throw GatewayException.invalid("售后数量无效");
      }
      if (qty < 1 || qty > 10000) throw GatewayException.invalid("售后数量超出范围");
      result.add(JsonUtils.obj("line_item_id", id, "quantity", qty));
    }
    return result;
  }

  private static Map<String, Object> paired(
      Object value, String customerCurrency, String shopCurrency) {
    var shop = JsonUtils.map(JsonUtils.map(value).get("shopMoney"));
    if (!shopCurrency.equals(shop.get("currencyCode")))
      throw GatewayException.invalid("缺少或不一致的店铺本位币金额，不能自行按汇率推算");
    return JsonUtils.obj(
        "customer",
        bag(value, customerCurrency).toPlainString(),
        "shop",
        money(shop.get("amount")).toPlainString());
  }

  private static BigDecimal bag(Object value, String currency) {
    var presentment = JsonUtils.map(JsonUtils.map(value).get("presentmentMoney"));
    if (!currency.equals(presentment.get("currencyCode")))
      throw GatewayException.invalid("缺少或不一致的客户原币金额，不能把本位币当作原币");
    return money(presentment.get("amount"));
  }

  private static BigDecimal positive(Object value) {
    var amount = money(value);
    if (amount.signum() <= 0) throw GatewayException.invalid("退款金额必须大于零");
    return amount;
  }

  private static BigDecimal money(Object value) {
    try {
      var amount = new BigDecimal(Objects.toString(value, ""));
      if (amount.signum() < 0
          || amount.compareTo(new BigDecimal("999999999999999999999999.99")) > 0)
        throw new IllegalArgumentException();
      return amount.setScale(2, RoundingMode.UNNECESSARY);
    } catch (RuntimeException e) {
      throw GatewayException.invalid("金额必须是非负有限数，最多两位小数");
    }
  }

  private static List<Map<String, Object>> maps(Object value) {
    return JsonUtils.list(value).stream().map(JsonUtils::map).toList();
  }

  private static void confirm(Map<String, Object> p) {
    if (!Boolean.TRUE.equals(p.get("confirm")))
      throw GatewayException.invalid("资金操作必须 confirm=true");
  }

  private static Map<String, Object> block(Map<String, Object> data, String field) {
    var result = new LinkedHashMap<>(JsonUtils.map(data.get(field)));
    if (!JsonUtils.list(result.get("userErrors")).isEmpty())
      throw upstream("Shopify 拒绝执行资金操作").detail(result.get("userErrors"));
    return result;
  }

  private static GatewayException upstream(String message) {
    return new GatewayException(502, "upstream_error", message, false);
  }

  private static String pythonJson(Object value) {
    if (value instanceof Map<?, ?> map)
      return map.entrySet().stream()
          .sorted(Comparator.comparing(e -> e.getKey().toString()))
          .map(e -> JsonUtils.write(e.getKey().toString()) + ": " + pythonJson(e.getValue()))
          .collect(Collectors.joining(", ", "{", "}"));
    if (value instanceof List<?> list)
      return list.stream()
          .map(ShopifyRefundMethods::pythonJson)
          .collect(Collectors.joining(", ", "[", "]"));
    return JsonUtils.write(value);
  }
}
