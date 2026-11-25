package group.cyt.common.module.registry.catalog.platform;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import group.cyt.common.module.registry.catalog.*;
import java.util.List;

public final class TikTokCatalog implements PlatformCatalog {
  private static final String DOCS = "https://partner.tiktokshop.com/docv2/page/";
  private static final String ADAPTER =
      "src/main/java/group/cyt/common/module/adapter/tiktok/TikTokAdapter.java";
  private static final BuiltinCatalog CATALOG = new BuiltinCatalog(List.of(new TikTokCatalog()));

  public static BuiltinCatalog catalog() {
    return CATALOG;
  }

  @Override
  public void register(CatalogBuilder catalog) {
    catalog.platform("tiktok", "TikTok Shop", "120/min");
    catalog.credentials(
        new PlatformCredentials(
            "tiktok",
            "TikTok Shop",
            DOCS + "common-parameters",
            "每家店铺使用自己的卖家访问令牌、应用凭据和 Shop Cipher。",
            "inherit",
            false,
            false,
            "TIKTOK_ACCOUNTS",
            "",
            "",
            List.of(
                new PlatformCredentials.Variable(
                    "TIKTOK_COMMERCE_ACCOUNTS",
                    "交易读取店铺",
                    false,
                    false,
                    "逗号分隔，默认 epomaker-keyboard",
                    "epomaker-keyboard",
                    false,
                    "",
                    false),
                secret("APP_KEY", "App Key", false),
                secret("APP_SECRET", "App Secret", true),
                secret("ACCESS_TOKEN", "Seller Access Token", true),
                secret("SHOP_CIPHER", "Shop Cipher", true))));

    catalog.operation(
        read(
            "tiktok.conversations.list",
            "分页读取店铺客服会话",
            "客服",
            "get-conversation-202601",
            schema(false, 1, 20, null, false),
            paged(object("type", "object", "description", "TikTok Shop 原始会话")),
            60));
    catalog.operation(
        read(
            "tiktok.messages.list",
            "分页读取会话消息",
            "客服",
            "get-conversation-202601",
            schema(false, 1, 10, "conversation_id", false),
            paged(object("type", "object", "description", "TikTok Shop 原始消息，按消息索引倒序")),
            120));
    catalog.operation(
        read(
            "tiktok.orders.list",
            "按更新时间读取订单",
            "订单",
            "get-order-list-202309",
            schema(true, 1, 100, null, true),
            paged(
                row(
                    "id",
                    "status",
                    "tracking_number",
                    "shipping_provider",
                    "create_time",
                    "update_time",
                    "line_items",
                    "payment")),
            60));
    catalog.operation(
        read(
            "tiktok.orders.get",
            "读取指定订单详情",
            "订单",
            "get-order-detail-202309",
            schema(true, 0, 0, "order_id", false),
            raw(
                row(
                        "id",
                        "status",
                        "tracking_number",
                        "shipping_provider",
                        "create_time",
                        "update_time",
                        "line_items")
                    .set("type", array("object", "null"))),
            60));
    catalog.operation(
        read(
            "tiktok.products.list",
            "按更新时间读取商品",
            "商品",
            "search-products-202502",
            schema(true, 1, 100, null, true),
            paged(row("id", "title", "status", "update_time", "skus")),
            60));
    catalog.operation(
        read(
            "tiktok.products.get",
            "读取指定商品详情",
            "商品",
            "get-product-202309",
            schema(true, 0, 0, "product_id", false),
            raw(
                row(
                    "id",
                    "title",
                    "status",
                    "description",
                    "update_time",
                    "skus",
                    "category_chains",
                    "main_images")),
            60));
    catalog.operation(
        read(
            "tiktok.tracking.get",
            "按订单读取物流轨迹",
            "物流",
            "get-order-tracking-202309",
            schema(true, 0, 0, "order_id", false),
            raw(object("type", "array", "items", row("action_code", "update_time_millis"))),
            60));
    catalog.operation(
        read(
            "tiktok.returns.list",
            "按更新时间读取退货退款",
            "售后",
            "search-returns-202602",
            schema(true, 10, 50, null, true),
            paged(
                row(
                    "return_id",
                    "order_id",
                    "return_type",
                    "return_status",
                    "return_reason",
                    "return_tracking_number",
                    "return_provider_name",
                    "create_time",
                    "update_time")),
            60));
  }

  private static PlatformCredentials.Variable secret(String suffix, String label, boolean masked) {
    return new PlatformCredentials.Variable(
        "TIKTOK_{ACCOUNT}_" + suffix, label, true, masked, "", "", false, "", true);
  }

  private static BuiltinOperation read(
      String key,
      String label,
      String category,
      String docs,
      ObjectNode schema,
      JsonNode response,
      int quota) {
    return operation(key)
        .summary(label)
        .description(label + "，按店铺凭据读取 TikTok Shop。")
        .category(category)
        .tags("read")
        .sensitive(true)
        .quota(quota + "/min")
        .timeout(18)
        .officialDocs(DOCS + docs)
        .params(schema)
        .response(response)
        .review(
            new CapabilityReview(
                null,
                label,
                "按店铺配置凭据，并给实际调用方授予精确操作授权。",
                null,
                "retain",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "统一签名、输入校验及上游错误处理。",
                "可读取的店铺及数据由卖家授权决定。",
                List.of(new CapabilityReview.Evidence(ADAPTER, 18, "class TikTokAdapter"))))
        .build();
  }

  private static ObjectNode schema(
      boolean commerce, int minPage, int maxPage, String id, boolean window) {
    var properties =
        object(
            "account",
            commerce
                ? object(
                    "type",
                    "string",
                    "enum",
                    array("epomaker-keyboard", "epomaker-usa", "linsoul", "mechlands", "kiiboom"),
                    "description",
                    "已在 Common 启用交易读取的店铺别名")
                : object(
                    "type",
                    "string",
                    "minLength",
                    1,
                    "maxLength",
                    60,
                    "examples",
                    array("epomaker-keyboard"),
                    "pattern",
                    "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                    "description",
                    "Common 登记的店铺别名"));
    var required = array("account");
    if (maxPage > 0) {
      properties.set(
          "page_size",
          object("type", "integer", "minimum", minPage, "maximum", maxPage, "default", maxPage));
      properties.set(
          "page_token",
          object(
              "type",
              "string",
              "maxLength",
              4096,
              "default",
              "",
              "description",
              "上一页返回的 next_page_token"));
    }
    if (id != null) {
      properties.set(
          id, object("type", "string", "pattern", "^[0-9]{1,32}$", "examples", array("123456789")));
      required.add(id);
    }
    if (window) {
      properties.set(
          "updated_from",
          object(
              "type",
              "integer",
              "minimum",
              0,
              "maximum",
              4102444799L,
              "examples",
              array(1795478400),
              "description",
              "Unix 秒，包含本秒"));
      properties.set(
          "updated_until",
          object(
              "type",
              "integer",
              "minimum",
              1,
              "maximum",
              4102444799L,
              "examples",
              array(1795482000),
              "description",
              "Unix 秒，排除本秒"));
      required.add("updated_from").add("updated_until");
    }
    return object(
        "type",
        "object",
        "additionalProperties",
        false,
        "properties",
        properties,
        "required",
        required);
  }

  private static ObjectNode row(String... fields) {
    var properties = object();
    for (String field : fields) {
      JsonNode value =
          switch (field) {
            case "create_time", "update_time", "update_time_millis" -> object("type", "integer");
            case "action_code" -> object("type", array("integer", "string"));
            case "payment" ->
                object(
                    "type",
                    "object",
                    "properties",
                    object(
                        "currency",
                        object("type", "string"),
                        "total_amount",
                        object("type", "string")));
            case "line_items", "skus", "category_chains", "main_images" ->
                object("type", "array", "items", object("type", "object"));
            default -> object("type", "string");
          };
      properties.set(field, value);
    }
    return object("type", "object", "properties", properties);
  }

  private static ObjectNode raw(JsonNode raw) {
    return object("type", "object", "properties", object("raw", raw), "required", array("raw"));
  }

  private static ObjectNode paged(JsonNode item) {
    return object(
        "type",
        "object",
        "properties",
        object(
            "raw",
            object("type", "array", "items", item),
            "next_page_token",
            object("type", "string", "description", "空字符串表示最后一页")),
        "required",
        array("raw", "next_page_token"));
  }
}
