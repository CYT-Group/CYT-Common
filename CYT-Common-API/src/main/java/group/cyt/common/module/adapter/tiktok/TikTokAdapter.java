package group.cyt.common.module.adapter.tiktok;

import static group.cyt.common.core.util.JsonUtils.*;
import static java.nio.charset.StandardCharsets.UTF_8;

import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.http.UpstreamHttp;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.adapter.PlatformAdapter;
import group.cyt.common.module.credential.service.CredentialService;
import group.cyt.common.module.registry.model.OperationDefinition;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import okhttp3.HttpUrl;
import org.springframework.stereotype.Component;

@Component
public final class TikTokAdapter implements PlatformAdapter {
  private static final String HOST = "https://open-api.tiktokglobalshop.com";
  private static final String CONVERSATIONS = "/customer_service/202309/conversations";
  private static final String ORDERS = "/order/202309/orders";
  private static final String PRODUCTS = "/product/202309/products";
  private static final Map<String, Object> UPDATE_SORT =
      Map.of("sort_field", "update_time", "sort_order", "ASC");
  private final CredentialService credentials;
  private final UpstreamHttp http;

  public TikTokAdapter(CredentialService credentials, UpstreamHttp http) {
    this.credentials = credentials;
    this.http = http;
  }

  @Override
  public boolean supports(String platform) {
    return "tiktok".equals(platform);
  }

  @Override
  public Object invoke(OperationDefinition operation, Map<String, Object> params, String system) {
    String account = str(params, "account");
    String key = operation.key();
    boolean customerService =
        key.equals("tiktok.conversations.list") || key.equals("tiktok.messages.list");
    if (!customerService) checkCommerceAccount(account);
    String orderId = str(params, "order_id");
    String productId = str(params, "product_id");
    return switch (key) {
      case "tiktok.conversations.list" ->
          page(
              account,
              system,
              params,
              CONVERSATIONS,
              "conversations",
              null,
              Map.of("locale", "zh-CN"),
              Function.identity());
      case "tiktok.messages.list" ->
          page(
              account,
              system,
              params,
              CONVERSATIONS + "/" + str(params, "conversation_id") + "/messages",
              "messages",
              null,
              Map.of(
                  "locale",
                  "zh-CN",
                  "sort_field",
                  "index",
                  "sort_order",
                  "DESC",
                  "need_plaintext",
                  "true"),
              Function.identity());
      case "tiktok.orders.list" ->
          page(
              account,
              system,
              params,
              ORDERS + "/search",
              "orders",
              window(params),
              UPDATE_SORT,
              TikTokAdapter::orderSummary);
      case "tiktok.orders.get" -> orderDetail(account, system, orderId);
      case "tiktok.products.list" ->
          page(
              account,
              system,
              params,
              "/product/202502/products/search",
              "products",
              obj(
                  "update_time_ge",
                  params.get("updated_from"),
                  "update_time_le",
                  ((Number) params.get("updated_until")).longValue() - 1),
              Map.of(),
              TikTokAdapter::productSummary);
      case "tiktok.products.get" -> productDetail(account, system, productId);
      case "tiktok.tracking.get" -> {
        var data =
            read(
                account,
                system,
                "/fulfillment/202309/orders/" + orderId + "/tracking",
                Map.of(),
                null);
        yield obj(
            "raw",
            rows(data.get("tracking")).stream()
                .map(row -> fields(row, "action_code", "update_time_millis"))
                .toList());
      }
      case "tiktok.returns.list" -> {
        var body = window(params);
        body.put("return_types", List.of("REFUND", "RETURN_AND_REFUND"));
        yield page(
            account,
            system,
            params,
            "/return_refund/202602/returns/search",
            "return_orders",
            body,
            UPDATE_SORT,
            row ->
                fields(
                    row,
                    "return_id",
                    "order_id",
                    "return_type",
                    "return_status",
                    "arbitration_status",
                    "return_reason",
                    "return_reason_text",
                    "return_tracking_number",
                    "return_provider_name",
                    "create_time",
                    "update_time"));
      }
      default -> throw GatewayException.config("未知 TikTok Shop 操作");
    };
  }

  private static Map<String, Object> window(Map<String, Object> params) {
    return obj(
        "update_time_ge",
        params.get("updated_from"),
        "update_time_lt",
        params.get("updated_until"));
  }

  private void checkCommerceAccount(String account) {
    String enabled = credentials.raw("TIKTOK_COMMERCE_ACCOUNTS").strip();
    if (enabled.isEmpty()) enabled = "epomaker-keyboard";
    if (Arrays.stream(enabled.split(",")).map(String::strip).noneMatch(account::equals))
      throw GatewayException.forbidden("该店铺未启用 TikTok Shop 订单、商品、物流与售后读取");
  }

  private Map<String, Object> page(
      String account,
      String system,
      Map<String, Object> params,
      String path,
      String field,
      Map<String, Object> body,
      Map<String, Object> options,
      Function<Object, Object> project) {
    var query = new LinkedHashMap<>(options);
    query.put("page_size", params.get("page_size"));
    String previous = str(params, "page_token");
    if (!previous.isEmpty()) query.put("page_token", previous);
    var data = read(account, system, path, query, body);
    Object next = data.get("next_page_token");
    boolean hasMore = Boolean.TRUE.equals(data.get("has_more"));
    if (next == null && !hasMore) next = "";
    if (!(next instanceof String cursor)
        || cursor.length() > 4096
        || (hasMore && cursor.isEmpty())
        || (!cursor.isEmpty() && cursor.equals(previous)))
      throw badResponse("TikTok Shop 返回无效分页游标");
    if (!data.containsKey(field)
        && data.get("total_count") instanceof Integer total
        && total == 0
        && cursor.isEmpty()) return obj("raw", List.of(), "next_page_token", "");
    var entries = rows(data.get(field));
    if (entries.isEmpty() && !cursor.isEmpty()) throw badResponse("TikTok Shop 返回无记录的中间页");
    return obj("raw", entries.stream().map(project).toList(), "next_page_token", cursor);
  }

  private Map<String, Object> orderDetail(String account, String system, String orderId) {
    var data = read(account, system, ORDERS, Map.of("ids", orderId), null);
    var orders = rows(data.get("orders"));
    if (orders.isEmpty()) return obj("raw", null);
    var order = record(orders.getFirst());
    if (orders.size() != 1 || !orderId.equals(order.get("id")))
      throw badResponse("TikTok Shop 返回了其他订单");
    var result =
        fields(
            order,
            "id",
            "status",
            "create_time",
            "update_time",
            "paid_time",
            "fulfillment_type",
            "shipping_type",
            "shipping_provider",
            "tracking_number",
            "collection_time",
            "delivery_time",
            "rts_sla_time",
            "tts_sla_time",
            "delivery_sla_time");
    result.put(
        "line_items",
        nested(
            order,
            "line_items",
            "id",
            "seller_sku",
            "product_id",
            "product_name",
            "sku_id",
            "sku_name",
            "quantity",
            "display_status",
            "package_id",
            "package_status",
            "tracking_number"));
    return obj("raw", result);
  }

  private Map<String, Object> productDetail(String account, String system, String productId) {
    var product = read(account, system, PRODUCTS + "/" + productId, Map.of(), null);
    if (!productId.equals(product.get("id"))) throw badResponse("TikTok Shop 返回了其他商品");
    var result =
        fields(product, "id", "title", "status", "description", "create_time", "update_time");
    result.put("skus", nested(product, "skus", "id", "seller_sku"));
    result.put("category_chains", nested(product, "category_chains", "id", "local_name", "name"));
    result.put("main_images", nested(product, "main_images", "urls", "thumb_urls"));
    return obj("raw", result);
  }

  private static Object orderSummary(Object row) {
    var order = record(row);
    var result =
        fields(
            order,
            "id",
            "status",
            "create_time",
            "update_time",
            "shipping_provider",
            "tracking_number",
            "fulfillment_type",
            "rts_sla_time",
            "tts_sla_time",
            "delivery_sla_time",
            "collection_time");
    result.put(
        "line_items",
        nested(
            order,
            "line_items",
            "seller_sku",
            "product_name",
            "product_id",
            "sku_id",
            "tracking_number",
            "package_status",
            "package_id"));
    result.put(
        "payment",
        fields(
            Objects.requireNonNullElse(order.get("payment"), Map.of()),
            "currency",
            "total_amount"));
    return result;
  }

  private static Object productSummary(Object row) {
    var product = record(row);
    var result = fields(product, "id", "title", "status", "update_time", "create_time");
    result.put("skus", nested(product, "skus", "id", "seller_sku", "price", "status_info"));
    return result;
  }

  private Map<String, Object> read(
      String account,
      String system,
      String path,
      Map<String, Object> options,
      Map<String, Object> body) {
    String prefix = "TIKTOK_" + CredentialService.accountKey(account) + "_";
    String secret = credentials.required(prefix + "APP_SECRET", system);
    String token = credentials.required(prefix + "ACCESS_TOKEN", system);
    var query = new TreeMap<>(options);
    query.put("app_key", credentials.required(prefix + "APP_KEY", system));
    query.put("shop_cipher", credentials.required(prefix + "SHOP_CIPHER", system));
    query.put("timestamp", Instant.now().getEpochSecond());
    String encodedBody = body == null ? "" : write(body);
    var input = new StringBuilder(secret).append(path);
    query.forEach((name, value) -> input.append(name).append(value));
    input.append(encodedBody).append(secret);
    query.put("sign", hmac(secret, input.toString()));
    var url = HttpUrl.get(HOST + path).newBuilder();
    query.forEach((name, value) -> url.addQueryParameter(name, String.valueOf(value)));

    UpstreamHttp.ResponseData response;
    try {
      response =
          http.quietExchange(
              body == null ? "GET" : "POST",
              url.build().toString(),
              Map.of("Content-Type", "application/json", "x-tts-access-token", token),
              body == null ? null : encodedBody.getBytes(UTF_8),
              16);
    } catch (GatewayException error) {
      // Provider errors can include signed query parameters or buyer data.
      throw new GatewayException(502, "upstream_error", "TikTok Shop 请求失败", error.retryable);
    }
    if (response.status() == 429) {
      var error = new GatewayException(429, "rate_limited", "TikTok Shop 请求过于频繁", true);
      error.retryAfter = integer(response.headers().get("Retry-After"), 1);
      throw error;
    }
    if (response.status() >= 500)
      throw new GatewayException(502, "upstream_error", "TikTok Shop 暂时无法响应", true);
    if (response.body().length > 16 * 1024 * 1024) throw badResponse("TikTok Shop 响应过大");
    Map<String, Object> result;
    try {
      result = JsonUtils.read(new String(response.body(), UTF_8));
    } catch (GatewayException error) {
      throw badResponse("TikTok Shop 未返回有效 JSON");
    }
    if (result == null) throw badResponse("TikTok Shop 未返回标准响应");
    if (!(result.get("code") instanceof Integer code)) throw badResponse("TikTok Shop 未返回标准响应");
    if (code != 0) throw badResponse("TikTok Shop 拒绝读取，请核对店铺授权、令牌和接口权限").code("TIKTOK_" + code);
    if (response.status() < 200 || response.status() >= 300) throw badResponse("TikTok Shop 请求失败");
    return record(result.get("data"));
  }

  private static List<Object> rows(Object value) {
    if (!(value instanceof List<?>)) throw badResponse("TikTok Shop 未返回列表");
    return list(value);
  }

  private static Map<String, Object> record(Object value) {
    if (!(value instanceof Map<?, ?>)) throw badResponse("TikTok Shop 返回无效记录");
    return map(value);
  }

  private static Map<String, Object> fields(Object value, String... names) {
    var row = record(value);
    Map<String, Object> result = new LinkedHashMap<>();
    for (String name : names) if (row.containsKey(name)) result.put(name, row.get(name));
    return result;
  }

  private static List<Map<String, Object>> nested(
      Map<String, Object> parent, String name, String... names) {
    var value = parent.get(name);
    return value == null ? List.of() : rows(value).stream().map(row -> fields(row, names)).toList();
  }

  private static GatewayException badResponse(String message) {
    return new GatewayException(502, "upstream_error", message, false);
  }
}
