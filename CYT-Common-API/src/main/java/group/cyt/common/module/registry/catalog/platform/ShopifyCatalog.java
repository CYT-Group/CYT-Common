package group.cyt.common.module.registry.catalog.platform;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import group.cyt.common.module.registry.catalog.*;
import java.util.List;

/** Built-in shopify contracts. Edit metadata, parameters and review together here. */
public final class ShopifyCatalog implements PlatformCatalog {
  @Override
  public void register(CatalogBuilder catalog) {
    catalog.platform("shopify", "Shopify Admin", "120/min");
    catalog.credentials(credentials());
    catalog.alias("shopify.order.get", "shopify.orders.get");
    catalog.alias("shopify.product.list", "shopify.products.list");
    catalog.alias("shopify.product.get", "shopify.products.get");
    catalog.operation(inventorySet());
    catalog.operation(ordersCreate());
    catalog.operation(ordersGet());
    catalog.operation(ordersList());
    catalog.operation(ordersUpdate());
    catalog.operation(ordersCancel());
    catalog.operation(productsGet());
    catalog.operation(productsList());
    catalog.operation(refundsCreate());
    catalog.operation(refundsList());
    catalog.operation(returnsList());
    catalog.operation(ordersCollect());
    catalog.operation(productsCollect());
  }

  private static PlatformCredentials credentials() {
    return new PlatformCredentials(
        "shopify",
        "Shopify Admin",
        "https://shopify.dev/docs/api/admin-rest",
        "店铺域名和 token 都按系统分：不同业务线常对接不同店铺",
        "inherit",
        true,
        false,
        "",
        "",
        "",
        List.of(
            new PlatformCredentials.Variable(
                "SHOPIFY_STORE",
                "店铺域名",
                true,
                false,
                "xxx.myshopify.com",
                "epomaker.myshopify.com",
                true,
                "SHOPIFY_{SYSTEM}_STORE",
                false),
            new PlatformCredentials.Variable(
                "SHOPIFY_ACCESS_TOKEN",
                "Admin API Token",
                true,
                true,
                "自定义应用 → Admin API access token",
                "",
                true,
                "SHOPIFY_{SYSTEM}_ACCESS_TOKEN",
                false),
            new PlatformCredentials.Variable(
                "SHOPIFY_API_VERSION",
                "API 版本",
                false,
                false,
                "默认 2025-01",
                "2025-01",
                false,
                "",
                false)));
  }

  private static BuiltinOperation inventorySet() {
    return operation("shopify.inventory.set")
        .summary("设置可售库存（直接覆盖）")
        .description("会把库存**覆盖**为给定值，不是增减。属于高危操作，需单独授权。")
        .quota("30/min")
        .sensitive(true)
        .since("2026-10")
        .category("库存")
        .order(10)
        .cost("none")
        .tags("write", "danger")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "store",
                    object(
                        "anyOf",
                        array(object("maxLength", 255, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "目标店铺，必须与本实例实际授权店铺一致",
                        "title",
                        "Store"),
                    "inventory_item_id",
                    object(
                        "description",
                        "gid://shopify/InventoryItem/123",
                        "title",
                        "Inventory Item Id",
                        "type",
                        "string"),
                    "location_id",
                    object(
                        "description",
                        "gid://shopify/Location/123",
                        "title",
                        "Location Id",
                        "type",
                        "string"),
                    "available",
                    object(
                        "description",
                        "设置后的可售数量",
                        "minimum",
                        0,
                        "title",
                        "Available",
                        "type",
                        "integer"),
                    "reason",
                    object(
                        "default",
                        "correction",
                        "maxLength",
                        64,
                        "title",
                        "Reason",
                        "type",
                        "string")),
                "required",
                array("inventory_item_id", "location_id", "available"),
                "title",
                "InventorySetParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                "将库存覆盖转换为 GraphQL mutation。",
                "增加库存预检、可选 compareQuantity 并发校验，显示库位与变动量。",
                "覆盖值与增量不同，不能自动猜测库位。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "把库存条目、库位和覆盖数量转为 mutation。",
                "仍需官方 ID；没有 SKU/库位解析、差异预览和比较版本前置条件。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                        86,
                        "case \"inventory.set\""))))
        .build();
  }

  private static BuiltinOperation ordersCreate() {
    return operation("shopify.orders.create")
        .summary("创建零金额补发订单（仅测试店铺）")
        .sensitive(true)
        .idempotent(true)
        .retries(0)
        .since("2026-10")
        .category("订单")
        .order(25)
        .cost("none")
        .tags("write", "danger")
        .params(
            object(
                "$defs",
                object(
                    "ReshipLineIn",
                    object(
                        "additionalProperties",
                        false,
                        "properties",
                        object(
                            "store",
                            object(
                                "anyOf",
                                array(
                                    object("maxLength", 255, "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "目标店铺，必须与本实例实际授权店铺一致",
                                "title",
                                "Store"),
                            "sku",
                            object(
                                "maxLength", 200, "minLength", 1, "title", "Sku", "type", "string"),
                            "quantity",
                            object(
                                "maximum",
                                100,
                                "minimum",
                                1,
                                "title",
                                "Quantity",
                                "type",
                                "integer")),
                        "required",
                        array("sku", "quantity"),
                        "title",
                        "ReshipLineIn",
                        "type",
                        "object"),
                    "ShippingAddressIn",
                    object(
                        "additionalProperties",
                        false,
                        "properties",
                        object(
                            "store",
                            object(
                                "anyOf",
                                array(
                                    object("maxLength", 255, "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "目标店铺，必须与本实例实际授权店铺一致",
                                "title",
                                "Store"),
                            "name",
                            object(
                                "anyOf",
                                array(
                                    object("maxLength", 120, "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "title",
                                "Name"),
                            "address1",
                            object(
                                "anyOf",
                                array(
                                    object("maxLength", 255, "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "title",
                                "Address1"),
                            "address2",
                            object(
                                "anyOf",
                                array(
                                    object("maxLength", 255, "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "title",
                                "Address2"),
                            "city",
                            object(
                                "anyOf",
                                array(
                                    object("maxLength", 120, "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "title",
                                "City"),
                            "province",
                            object(
                                "anyOf",
                                array(
                                    object("maxLength", 120, "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "title",
                                "Province"),
                            "zip",
                            object(
                                "anyOf",
                                array(
                                    object("maxLength", 32, "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "title",
                                "Zip"),
                            "countryCode",
                            object(
                                "anyOf",
                                array(
                                    object("maxLength", 8, "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "ISO 两位国家码，如 US",
                                "title",
                                "Countrycode"),
                            "phone",
                            object(
                                "anyOf",
                                array(
                                    object("maxLength", 40, "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "title",
                                "Phone")),
                        "title",
                        "ShippingAddressIn",
                        "type",
                        "object")),
                "additionalProperties",
                false,
                "properties",
                object(
                    "store",
                    object(
                        "anyOf",
                        array(object("maxLength", 255, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "目标店铺，必须与本实例实际授权店铺一致",
                        "title",
                        "Store"),
                    "order_id",
                    object(
                        "anyOf",
                        array(object("type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "title",
                        "Order Id"),
                    "reference",
                    object(
                        "pattern", "^[A-Za-z0-9_-]{1,80}$", "title", "Reference", "type", "string"),
                    "customer_email",
                    object(
                        "maxLength",
                        254,
                        "minLength",
                        3,
                        "title",
                        "Customer Email",
                        "type",
                        "string"),
                    "currency",
                    object(
                        "default",
                        "USD",
                        "pattern",
                        "^[A-Z]{3}$",
                        "title",
                        "Currency",
                        "type",
                        "string"),
                    "shipping_address",
                    object("$ref", "#/$defs/ShippingAddressIn"),
                    "line_items",
                    object(
                        "items",
                        object("$ref", "#/$defs/ReshipLineIn"),
                        "maxItems",
                        50,
                        "minItems",
                        1,
                        "title",
                        "Line Items",
                        "type",
                        "array"),
                    "confirm",
                    object("default", false, "title", "Confirm", "type", "boolean")),
                "required",
                array("reference", "customer_email", "shipping_address", "line_items"),
                "title",
                "OrderCreateParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                "已有业务 reference 查重、SKU 唯一匹配、地址校验和零金额补发，并限制测试店铺。",
                "补独立的补发预检计划、原订单关联和并发唯一性。",
                "测试店铺限制保留，不能宣称已开放正式订单写入。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "按业务 reference 查重，逐 SKU 唯一解析变体，校验地址并创建零金额补发单。",
                "限测试店铺；按标签查重不是并发唯一约束，尚无完整预检计划和原订单校验。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                        181,
                        "private Object create("))))
        .build();
  }

  private static BuiltinOperation ordersGet() {
    return operation("shopify.orders.get")
        .summary("查询订单")
        .quota("120/min")
        .retries(3)
        .since("2026-10")
        .category("订单")
        .order(10)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "store",
                    object(
                        "anyOf",
                        array(object("maxLength", 255, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "目标店铺，必须与本实例实际授权店铺一致",
                        "title",
                        "Store"),
                    "order_id",
                    object(
                        "description",
                        "GraphQL ID，如 gid://shopify/Order/123",
                        "title",
                        "Order Id",
                        "type",
                        "string")),
                "required",
                array("order_id"),
                "title",
                "OrderGetParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                "合并 GraphQL 订单及明细查询。",
                "增加业务字段视图和嵌套连接完整性信息。",
                "嵌套 lineItems/transactions 的 first 上限不代表全量。",
                "ready",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "返回预定义 GraphQL 的订单或商品节点。",
                "业务关联和嵌套连接完整性仍需调用方处理。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                        63,
                        "case \"orders.get\""),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                        69,
                        "case \"products.get\""))))
        .build();
  }

  private static BuiltinOperation ordersList() {
    return operation("shopify.orders.list")
        .summary("按更新时间增量拉订单列表（含明细/支付流水/客户/地址）")
        .description("用 query 传 updated_at:>=时间 做增量；翻页用返回的 page_info.endCursor 传 after。")
        .quota("120/min")
        .retries(3)
        .since("2026-10")
        .category("订单")
        .order(5)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "store",
                    object(
                        "anyOf",
                        array(object("maxLength", 255, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "目标店铺，必须与本实例实际授权店铺一致",
                        "title",
                        "Store"),
                    "limit",
                    object(
                        "default",
                        50,
                        "description",
                        "每页数量，Shopify GraphQL 最大 250",
                        "maximum",
                        250,
                        "minimum",
                        1,
                        "title",
                        "Limit",
                        "type",
                        "integer"),
                    "query",
                    object(
                        "anyOf",
                        array(object("maxLength", 500, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "Shopify 搜索语法，如 updated_at:>=2026-10-01T00:00:00Z 或 status:open",
                        "title",
                        "Query"),
                    "after",
                    object(
                        "anyOf",
                        array(object("maxLength", 2048, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "上一页 pageInfo.endCursor",
                        "title",
                        "After"),
                    "sort_key",
                    object(
                        "anyOf",
                        array(
                            object(
                                "enum",
                                array("CREATED_AT", "ID", "UPDATED_AT", "TOTAL_PRICE"),
                                "type",
                                "string"),
                            object("type", "null")),
                        "default",
                        "UPDATED_AT",
                        "description",
                        "默认按更新时间升序，便于用游标增量",
                        "title",
                        "Sort Key"),
                    "reverse",
                    object(
                        "default",
                        false,
                        "description",
                        "是否反向排序",
                        "title",
                        "Reverse",
                        "type",
                        "boolean")),
                "title",
                "OrderListParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                "已整理订单结果和 GraphQL page_info。",
                "优先用 collect 自动翻根订单页；后续单独补齐大订单嵌套连接。",
                "本次 collect 的 coverage=root_records，不承诺嵌套明细全量。",
                "recommended",
                List.of("shopify.orders.collect"),
                CapabilityReview.Depth.ENHANCED,
                "将根连接转成 items 和 page_info。",
                "仅根分页；订单行和变体等嵌套连接仍受查询 first 限制。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                        128,
                        "private Object list("))))
        .build();
  }

  private static BuiltinOperation ordersUpdate() {
    return operation("shopify.orders.update")
        .summary("更新订单备注 / 标签 / 收货地址")
        .description("覆盖写：tags 传什么就是什么，不是追加。已发货订单改地址会被 Shopify 拒绝。")
        .since("2026-10")
        .category("订单")
        .order(15)
        .cost("none")
        .tags("write")
        .params(
            object(
                "$defs",
                object(
                    "ShippingAddressIn",
                    object(
                        "additionalProperties",
                        false,
                        "properties",
                        object(
                            "store",
                            object(
                                "anyOf",
                                array(
                                    object("maxLength", 255, "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "目标店铺，必须与本实例实际授权店铺一致",
                                "title",
                                "Store"),
                            "name",
                            object(
                                "anyOf",
                                array(
                                    object("maxLength", 120, "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "title",
                                "Name"),
                            "address1",
                            object(
                                "anyOf",
                                array(
                                    object("maxLength", 255, "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "title",
                                "Address1"),
                            "address2",
                            object(
                                "anyOf",
                                array(
                                    object("maxLength", 255, "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "title",
                                "Address2"),
                            "city",
                            object(
                                "anyOf",
                                array(
                                    object("maxLength", 120, "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "title",
                                "City"),
                            "province",
                            object(
                                "anyOf",
                                array(
                                    object("maxLength", 120, "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "title",
                                "Province"),
                            "zip",
                            object(
                                "anyOf",
                                array(
                                    object("maxLength", 32, "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "title",
                                "Zip"),
                            "countryCode",
                            object(
                                "anyOf",
                                array(
                                    object("maxLength", 8, "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "ISO 两位国家码，如 US",
                                "title",
                                "Countrycode"),
                            "phone",
                            object(
                                "anyOf",
                                array(
                                    object("maxLength", 40, "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "title",
                                "Phone")),
                        "title",
                        "ShippingAddressIn",
                        "type",
                        "object")),
                "additionalProperties",
                false,
                "properties",
                object(
                    "store",
                    object(
                        "anyOf",
                        array(object("maxLength", 255, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "目标店铺，必须与本实例实际授权店铺一致",
                        "title",
                        "Store"),
                    "order_id",
                    object(
                        "description",
                        "gid://shopify/Order/123",
                        "title",
                        "Order Id",
                        "type",
                        "string"),
                    "note",
                    object(
                        "anyOf",
                        array(object("maxLength", 5000, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "订单备注（覆盖写）",
                        "title",
                        "Note"),
                    "tags",
                    object(
                        "anyOf",
                        array(
                            object(
                                "items",
                                object("type", "string"),
                                "maxItems",
                                250,
                                "type",
                                "array"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "标签（覆盖写）",
                        "title",
                        "Tags"),
                    "shipping_address",
                    object(
                        "anyOf",
                        array(object("$ref", "#/$defs/ShippingAddressIn"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "改收货地址（覆盖写）；已发货订单 Shopify 会拒绝")),
                "required",
                array("order_id"),
                "title",
                "OrderUpdateParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                "转换备注、标签与收货地址更新。",
                "增加字段级变更预览；标签区分追加、删除和全量替换。",
                "覆盖标签前需读取最新状态。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "映射备注、标签、地址等更新字段。",
                "没有现状读取、标签增删语义和差异预览。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                        76,
                        "case \"orders.update\""))))
        .build();
  }

  private static BuiltinOperation ordersCancel() {
    String adapter = "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java";
    return operation("shopify.orders.cancel")
        .summary("取消未履约订单")
        .description("重新核对目标店铺、订单版本、履约状态和退款金额，再提交 Shopify 取消任务。")
        .quota("30/min")
        .sensitive(true)
        .idempotent(true)
        .retries(0)
        .category("订单")
        .order(16)
        .cost("none")
        .tags("write", "danger", "idempotency_required")
        .officialDocs("https://shopify.dev/docs/api/admin-graphql/2026-07/mutations/orderCancel")
        .params(object(
            "type", "object", "additionalProperties", false,
            "required", array("store", "order_id", "updated_at", "refund_method", "reason", "restock",
                "notify_customer"),
            "properties", object(
                "store", object("type", "string", "minLength", 1, "maxLength", 255),
                "order_id", object("type", "string", "pattern", "^gid://shopify/Order/[1-9][0-9]*$"),
                "updated_at", object("type", "string", "minLength", 1, "maxLength", 50),
                "refund_method", object("type", "string", "enum", array("original", "credit", "later")),
                "reason", object("type", "string", "enum",
                    array("CUSTOMER", "DECLINED", "FRAUD", "INVENTORY", "STAFF", "OTHER")),
                "restock", object("type", "boolean"),
                "notify_customer", object("type", "boolean"),
                "staff_note", object("type", "string", "maxLength", 255),
                "amount", object("type", "string", "pattern", "^[0-9]+(\\.[0-9]+)?$", "maxLength", 50),
                "currency", object("type", "string", "pattern", "^[A-Z]{3}$"))))
        .review(new CapabilityReview(adapter, "按实时订单状态提交取消任务。", "根据订单状态核对取消结果。",
            "退款取决于 Shopify 和支付渠道处理结果。", "enhance", List.of(),
            CapabilityReview.Depth.ENHANCED, "核对店铺、版本、履约及付款金额后执行。",
            "取消任务受理后需再次读取订单。", List.of(
                new CapabilityReview.Evidence(adapter, 109, "case \"orders.cancel\""))))
        .build();
  }

  private static BuiltinOperation productsGet() {
    return operation("shopify.products.get")
        .summary("商品详情")
        .quota("120/min")
        .retries(3)
        .since("2026-10")
        .category("商品")
        .order(20)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "store",
                    object(
                        "anyOf",
                        array(object("maxLength", 255, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "目标店铺，必须与本实例实际授权店铺一致",
                        "title",
                        "Store"),
                    "product_id",
                    object(
                        "description",
                        "gid://shopify/Product/123",
                        "title",
                        "Product Id",
                        "type",
                        "string")),
                "required",
                array("product_id"),
                "title",
                "ProductGetParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                "读取商品、变体和库存关联信息。",
                "提供按 SKU 的唯一变体解析，展示重复 SKU 与子连接截断。",
                "",
                "ready",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "返回预定义 GraphQL 的订单或商品节点。",
                "业务关联和嵌套连接完整性仍需调用方处理。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                        63,
                        "case \"orders.get\""),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                        69,
                        "case \"products.get\""))))
        .build();
  }

  private static BuiltinOperation productsList() {
    return operation("shopify.products.list")
        .summary("商品列表")
        .quota("120/min")
        .retries(3)
        .since("2026-10")
        .category("商品")
        .order(10)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "store",
                    object(
                        "anyOf",
                        array(object("maxLength", 255, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "目标店铺，必须与本实例实际授权店铺一致",
                        "title",
                        "Store"),
                    "limit",
                    object(
                        "default",
                        20,
                        "description",
                        "每页数量；要带出每个商品的变体 SKU，实际每页按 50 封顶（Shopify 单查询成本上限）",
                        "maximum",
                        250,
                        "minimum",
                        1,
                        "title",
                        "Limit",
                        "type",
                        "integer"),
                    "query",
                    object(
                        "anyOf",
                        array(object("maxLength", 200, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "Shopify 搜索语法，如 status:active",
                        "title",
                        "Query"),
                    "after",
                    object(
                        "anyOf",
                        array(object("maxLength", 2048, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "上一页 pageInfo.endCursor，用于下一页",
                        "title",
                        "After"),
                    "sort_key",
                    object(
                        "anyOf",
                        array(
                            object(
                                "enum",
                                array(
                                    "CREATED_AT",
                                    "ID",
                                    "INVENTORY_TOTAL",
                                    "PRODUCT_TYPE",
                                    "PUBLISHED_AT",
                                    "RELEVANCE",
                                    "TITLE",
                                    "UPDATED_AT",
                                    "VENDOR"),
                                "type",
                                "string"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "产品排序字段；query 有全文搜索时可用 RELEVANCE",
                        "title",
                        "Sort Key"),
                    "reverse",
                    object(
                        "default",
                        false,
                        "description",
                        "是否反向排序",
                        "title",
                        "Reverse",
                        "type",
                        "boolean")),
                "title",
                "ProductListParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                "返回商品页和 GraphQL page_info。",
                "优先用 collect 自动翻商品根页，再按需要补齐变体。",
                "不能把根分页完成等同于全部嵌套变体已读取。",
                "recommended",
                List.of("shopify.products.collect"),
                CapabilityReview.Depth.ENHANCED,
                "将根连接转成 items 和 page_info。",
                "仅根分页；订单行和变体等嵌套连接仍受查询 first 限制。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                        128,
                        "private Object list("))))
        .build();
  }

  private static BuiltinOperation refundsCreate() {
    return operation("shopify.refunds.create")
        .summary("发起退款（不可逆，必须 confirm=true）")
        .description(
            "会先查订单算「可退余额」并用原始 SALE 流水做 parentId。amount 必填且不能超过余额；restock=true 才把货退回库存。retry_max=0：退款不可重试，重试就是重复退钱。")
        .quota("30/min")
        .sensitive(true)
        .idempotent(true)
        .retries(0)
        .since("2026-10")
        .category("订单")
        .order(20)
        .cost("none")
        .tags("write", "money")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "store",
                    object(
                        "anyOf",
                        array(object("maxLength", 255, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "目标店铺，必须与本实例实际授权店铺一致",
                        "title",
                        "Store"),
                    "order_id",
                    object(
                        "description",
                        "gid://shopify/Order/123",
                        "title",
                        "Order Id",
                        "type",
                        "string"),
                    "amount",
                    object(
                        "description",
                        "退款金额（正数，字符串避免浮点误差），必须 ≤ 可退余额",
                        "title",
                        "Amount",
                        "type",
                        "string"),
                    "note",
                    object(
                        "anyOf",
                        array(object("maxLength", 255, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "退款备注（写进 Shopify 退款记录）",
                        "title",
                        "Note"),
                    "notify",
                    object(
                        "default",
                        true,
                        "description",
                        "是否邮件通知客户",
                        "title",
                        "Notify",
                        "type",
                        "boolean"),
                    "restock",
                    object(
                        "default",
                        false,
                        "description",
                        "是否把货退回库存（true 会改库存）",
                        "title",
                        "Restock",
                        "type",
                        "boolean"),
                    "confirm",
                    object(
                        "default",
                        false,
                        "description",
                        "二次确认：必须显式传 true，否则拒绝执行",
                        "title",
                        "Confirm",
                        "type",
                        "boolean"),
                    "idempotency_key",
                    object(
                        "anyOf",
                        array(object("maxLength", 64, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "幂等键（Shopify @idempotent 用）。不传则网关生成 uuid；同一 key 重复调用 Shopify 返回同一笔退款，不会重复退钱",
                        "title",
                        "Idempotency Key")),
                "required",
                array("order_id", "amount"),
                "title",
                "RefundCreateParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                "已读取订单余额及成功 SALE 流水，校验可退金额并构造退款/退库请求。",
                "补独立的退款计划、强制稳定业务编号、多支付分摊和逐项退库。",
                "退款不可逆；当前 restock=true 使用当前订单行数量，需明确业务范围。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "读取订单余额和成功 SALE 流水，检查可退金额并构建退款/退库请求。",
                "默认随机生成供应商幂等键；多支付分摊和逐项退库未封装，restock=true 使用当前订单行数量。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                        269,
                        "private Object refund("))))
        .build();
  }

  private static BuiltinOperation refundsList() {
    return operation("shopify.refunds.list")
        .summary("拉退款记录（含退款明细/原因备注/支付渠道）")
        .description(
            "Shopify GraphQL 没有顶层 refunds 查询，Refund 只能挂在 Order 下，所以这里按订单翻页再摊平成「一行一笔退款」。用 query 传 updated_at:>=时间 做增量；翻页用返回的 page_info.endCursor 传 after。")
        .quota("120/min")
        .retries(3)
        .since("2026-10")
        .category("退款")
        .order(10)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "description",
                "退款没有顶层查询：Shopify 的 Refund 只能挂在 Order 下，所以按订单翻页再摊平。",
                "properties",
                object(
                    "store",
                    object(
                        "anyOf",
                        array(object("maxLength", 255, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "目标店铺，必须与本实例实际授权店铺一致",
                        "title",
                        "Store"),
                    "limit",
                    object(
                        "default",
                        50,
                        "description",
                        "每页订单数，Shopify GraphQL 最大 250",
                        "maximum",
                        250,
                        "minimum",
                        1,
                        "title",
                        "Limit",
                        "type",
                        "integer"),
                    "query",
                    object(
                        "anyOf",
                        array(object("maxLength", 500, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "Shopify 订单搜索语法，如 updated_at:>=2026-10-01T00:00:00Z 或 financial_status:refunded",
                        "title",
                        "Query"),
                    "after",
                    object(
                        "anyOf",
                        array(object("maxLength", 2048, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "上一页 pageInfo.endCursor",
                        "title",
                        "After")),
                "title",
                "RefundListParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                "把按订单查询的退款展平成业务记录并附带订单。",
                "设计双层游标：根订单页与页内退款偏移，再提供 collect。",
                "当前游标以订单为单位；退款数量可能大于 limit，不能套用通用收集器。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "整理退货/退款查询；退款展平时附带订单信息。",
                "退款游标属于根订单且子记录有独立数量，未提供双层续传与所有内连接补齐。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                        128,
                        "private Object list("))))
        .build();
  }

  private static BuiltinOperation returnsList() {
    return operation("shopify.returns.list")
        .summary("拉退货单（含退货原因/状态/明细 SKU）")
        .description(
            "需要 Shopify 后台给应用勾 read_returns 权限，否则返回 ACCESS_DENIED。用 query 传 created_at:>=时间 做增量；翻页用 page_info.endCursor。")
        .quota("120/min")
        .retries(3)
        .since("2026-10")
        .category("退货")
        .order(10)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "store",
                    object(
                        "anyOf",
                        array(object("maxLength", 255, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "目标店铺，必须与本实例实际授权店铺一致",
                        "title",
                        "Store"),
                    "limit",
                    object(
                        "default",
                        50,
                        "description",
                        "每页数量，Shopify GraphQL 最大 250",
                        "maximum",
                        250,
                        "minimum",
                        1,
                        "title",
                        "Limit",
                        "type",
                        "integer"),
                    "query",
                    object(
                        "anyOf",
                        array(object("maxLength", 500, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "退货搜索语法，如 created_at:>=2026-10-01 或 status:OPEN；需要 read_returns 权限",
                        "title",
                        "Query"),
                    "after",
                    object(
                        "anyOf",
                        array(object("maxLength", 2048, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "上一页 pageInfo.endCursor",
                        "title",
                        "After")),
                "title",
                "ReturnListParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                "读取退货单及退货原因、状态与明细。",
                "独立处理退货根连接及明细连接，提供 coverage 和续传。",
                "嵌套连接仍有上限，本次未套通用 collect。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "整理退货/退款查询；退款展平时附带订单信息。",
                "退款游标属于根订单且子记录有独立数量，未提供双层续传与所有内连接补齐。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/shopify/ShopifyAdapter.java",
                        128,
                        "private Object list("))))
        .build();
  }

  private static BuiltinOperation ordersCollect() {
    return operation("shopify.orders.collect")
        .summary("自动分页汇总：按更新时间增量拉订单列表（含明细/支付流水/客户/地址）")
        .description(
            "自动分页、统一 items/page/errors；支持 max_items、max_pages 与签名续传游标。仅扫描根记录，嵌套资源遵循原接口的范围。读取能力独立授权，保留原接口兼容。")
        .quota("120/min")
        .timeout(90)
        .category("业务聚合")
        .order(-50)
        .sourceOperation("shopify.orders.list")
        .tags("read", "business", "collection")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "store",
                    object(
                        "anyOf",
                        array(object("maxLength", 255, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "目标店铺，必须与本实例实际授权店铺一致",
                        "title",
                        "Store"),
                    "query",
                    object(
                        "anyOf",
                        array(object("maxLength", 500, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "Shopify 搜索语法，如 updated_at:>=2026-10-01T00:00:00Z 或 status:open",
                        "title",
                        "Query"),
                    "sort_key",
                    object(
                        "anyOf",
                        array(
                            object(
                                "enum",
                                array("CREATED_AT", "ID", "UPDATED_AT", "TOTAL_PRICE"),
                                "type",
                                "string"),
                            object("type", "null")),
                        "default",
                        "UPDATED_AT",
                        "description",
                        "默认按更新时间升序，便于用游标增量",
                        "title",
                        "Sort Key"),
                    "reverse",
                    object(
                        "default",
                        false,
                        "description",
                        "是否反向排序",
                        "title",
                        "Reverse",
                        "type",
                        "boolean"),
                    "page_size",
                    object("type", "integer", "default", 50, "minimum", 1, "maximum", 1000),
                    "max_items",
                    object("type", "integer", "default", 100, "minimum", 1, "maximum", 1000),
                    "max_pages",
                    object("type", "integer", "default", 10, "minimum", 1, "maximum", 100),
                    "resume_cursor",
                    object(
                        "type",
                        "string",
                        "minLength",
                        0,
                        "maxLength",
                        16000,
                        "description",
                        "回传 page.next_cursor；绑定调用系统和筛选条件")),
                "required",
                array(),
                "examples",
                array(object())))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                "自动分页、统一 items/page/errors；支持 max_items、max_pages 与签名续传游标。仅扫描根记录，嵌套资源遵循原接口的范围。读取能力独立授权，保留原接口兼容。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "自动翻页覆盖根记录；嵌套连接、供应商可见范围和来源快照仍有各自限制。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "在固定只读能力上增加上限、分页、去重、统一返回和签名续传。",
                "只补根订单页，嵌套商品行和交易有 first 上限。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                        101,
                        "public Object invoke("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                        327,
                        "\"root_records\""))))
        .build();
  }

  private static BuiltinOperation productsCollect() {
    return operation("shopify.products.collect")
        .summary("自动分页汇总：商品列表")
        .description(
            "自动分页、统一 items/page/errors；支持 max_items、max_pages 与签名续传游标。仅扫描根记录，嵌套资源遵循原接口的范围。读取能力独立授权，保留原接口兼容。")
        .quota("120/min")
        .timeout(90)
        .category("业务聚合")
        .order(-50)
        .sourceOperation("shopify.products.list")
        .tags("read", "business", "collection")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "store",
                    object(
                        "anyOf",
                        array(object("maxLength", 255, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "目标店铺，必须与本实例实际授权店铺一致",
                        "title",
                        "Store"),
                    "query",
                    object(
                        "anyOf",
                        array(object("maxLength", 200, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "Shopify 搜索语法，如 status:active",
                        "title",
                        "Query"),
                    "sort_key",
                    object(
                        "anyOf",
                        array(
                            object(
                                "enum",
                                array(
                                    "CREATED_AT",
                                    "ID",
                                    "INVENTORY_TOTAL",
                                    "PRODUCT_TYPE",
                                    "PUBLISHED_AT",
                                    "RELEVANCE",
                                    "TITLE",
                                    "UPDATED_AT",
                                    "VENDOR"),
                                "type",
                                "string"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "产品排序字段；query 有全文搜索时可用 RELEVANCE",
                        "title",
                        "Sort Key"),
                    "reverse",
                    object(
                        "default",
                        false,
                        "description",
                        "是否反向排序",
                        "title",
                        "Reverse",
                        "type",
                        "boolean"),
                    "page_size",
                    object("type", "integer", "default", 50, "minimum", 1, "maximum", 1000),
                    "max_items",
                    object("type", "integer", "default", 100, "minimum", 1, "maximum", 1000),
                    "max_pages",
                    object("type", "integer", "default", 10, "minimum", 1, "maximum", 100),
                    "resume_cursor",
                    object(
                        "type",
                        "string",
                        "minLength",
                        0,
                        "maxLength",
                        16000,
                        "description",
                        "回传 page.next_cursor；绑定调用系统和筛选条件")),
                "required",
                array(),
                "examples",
                array(object())))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                "自动分页、统一 items/page/errors；支持 max_items、max_pages 与签名续传游标。仅扫描根记录，嵌套资源遵循原接口的范围。读取能力独立授权，保留原接口兼容。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "自动翻页覆盖根记录；嵌套连接、供应商可见范围和来源快照仍有各自限制。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "在固定只读能力上增加上限、分页、去重、统一返回和签名续传。",
                "只补商品根页，大量变体需独立翻子连接。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                        101,
                        "public Object invoke("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                        327,
                        "\"root_records\""))))
        .build();
  }
}
