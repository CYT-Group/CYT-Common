package group.cyt.common.module.registry.catalog.platform;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import group.cyt.common.module.registry.catalog.*;
import java.util.List;

/** Built-in lingxing contracts. Edit metadata, parameters and review together here. */
public final class LingxingCatalog implements PlatformCatalog {
  @Override
  public void register(CatalogBuilder catalog) {
    catalog.platform("lingxing", "领星 ERP（亚马逊）", "60/min");
    catalog.credentials(credentials());
    catalog.operation(aftersalesReasons());
    catalog.operation(inventoryFba());
    catalog.operation(inventoryList());
    catalog.operation(listingList());
    catalog.operation(messagesDetail());
    catalog.operation(messagesList());
    catalog.operation(mpordersList());
    catalog.operation(ordersDetail());
    catalog.operation(ordersList());
    catalog.operation(productList());
    catalog.operation(refundCreate());
    catalog.operation(returnQty());
    catalog.operation(sellerList());
    catalog.operation(warehouseList());
    catalog.operation(workorderAdd());
    catalog.operation(workorderAudit());
    catalog.operation(workorderList());
    catalog.operation(ordersCollect());
    catalog.operation(productCollect());
  }

  private static PlatformCredentials credentials() {
    return new PlatformCredentials(
        "lingxing",
        "领星 ERP",
        "https://openapi.lingxing.com",
        "多账号平台：一个企业一套 AppId + AppSecret。⚠️ AppId 长度必须是 16/24/32 字节 —— 签名拿它当 AES 密钥，长度不对直接报错",
        "inherit",
        true,
        false,
        "LINGXING_ACCOUNTS",
        "LINGXING_ACCOUNT",
        "default",
        List.of(
            new PlatformCredentials.Variable(
                "LINGXING_ACCOUNTS",
                "账号列表",
                false,
                false,
                "逗号分隔。改这一行就会展开出对应账号的变量行",
                "default",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "LINGXING_ACCOUNT",
                "默认账号",
                false,
                false,
                "不传 account 时用它；都没配则用 default",
                "default",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "LINGXING_PROXY_BASE",
                "erp-proxy 地址",
                false,
                false,
                "配了才走代理；不配则直连 openapi（要求调用方 IP 已在领星白名单）",
                "https://erp-proxy.cyt.group",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "LINGXING_PROXY_TOKEN",
                "erp-proxy 令牌",
                false,
                true,
                "代理鉴权头 X-Proxy-Token。和 AppSecret 是两回事，别混",
                "",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "LINGXING_{ACCOUNT}_APP_ID",
                "AppId",
                true,
                false,
                "16/24/32 字节。领星后台 → 设置 → 业务配置 → 全局 → 开放接口",
                "",
                false,
                "",
                true),
            new PlatformCredentials.Variable(
                "LINGXING_{ACCOUNT}_APP_SECRET",
                "AppSecret",
                true,
                true,
                "和 AppId 配套；换 token 用 multipart，别用 JSON",
                "",
                false,
                "",
                true),
            new PlatformCredentials.Variable(
                "LINGXING_{ACCOUNT}_MAIL_EMAIL",
                "站内信店铺邮箱",
                false,
                false,
                "站内信同步必填；领星要求传店铺绑定邮箱，不填则同步报 not_configured",
                "seller@example.com",
                false,
                "",
                true)));
  }

  private static BuiltinOperation aftersalesReasons() {
    return operation("lingxing.aftersales.reasons")
        .summary("查询售后原因列表（客服模块）")
        .description("售后原因，用于退款/退货工单的明细。")
        .since("2026-10")
        .category("客服")
        .order(1)
        .cost("none")
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "领星账号名；空则用 LINGXING_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "offset",
                    object(
                        "default",
                        0,
                        "description",
                        "分页偏移量",
                        "minimum",
                        0,
                        "title",
                        "Offset",
                        "type",
                        "integer"),
                    "length",
                    object(
                        "default",
                        20,
                        "description",
                        "每页条数，最小 20、最大 200",
                        "maximum",
                        200,
                        "minimum",
                        20,
                        "title",
                        "Length",
                        "type",
                        "integer")),
                "title",
                "Paging",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                "整理原因 ID、原因文字和创建信息；无翻译和启用项过滤。",
                "按原因用途建立业务字典，保留来源 reason_id，避免按译名提交。",
                "",
                "ready",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "整理售后原因 ID、原因文字和创建信息。",
                "代码没有翻译或启用项过滤，不能把先前说明当作已有功能。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        508,
                        "private Object aftersalesReasons("))))
        .build();
  }

  private static BuiltinOperation inventoryFba() {
    return operation("lingxing.inventory.fba")
        .summary("查询 FBA 库存")
        .description(
            "路径 /basicOpen/openapi/storage/fbaWarehouseDetail。⚠️ length 只支持 20/50/100/200/500/1000/2000/5000。")
        .since("2026-10")
        .category("库存")
        .order(13)
        .cost("none")
        .tags("read", "inventory")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "领星账号名；空则用 LINGXING_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "offset",
                    object("default", 0, "minimum", 0, "title", "Offset", "type", "integer"),
                    "length",
                    object(
                        "default",
                        20,
                        "description",
                        "仅支持 20/50/100/200/500/1000/2000/5000",
                        "title",
                        "Length",
                        "type",
                        "integer"),
                    "search_field",
                    object(
                        "default",
                        "",
                        "description",
                        "sku/product_name/seller_sku/fnsku/asin/parent_asin/spu/spu_name",
                        "title",
                        "Search Field",
                        "type",
                        "string"),
                    "search_value",
                    object(
                        "default",
                        "",
                        "description",
                        "搜索值",
                        "title",
                        "Search Value",
                        "type",
                        "string"),
                    "sid",
                    object(
                        "default",
                        "",
                        "description",
                        "店铺 id，多个用逗号分隔",
                        "title",
                        "Sid",
                        "type",
                        "string"),
                    "fulfillment_channel_type",
                    object(
                        "default",
                        "",
                        "description",
                        "FBA / FBM",
                        "title",
                        "Fulfillment Channel Type",
                        "type",
                        "string"),
                    "is_hide_zero_stock",
                    object(
                        "default",
                        "0",
                        "description",
                        "0 显示零库存 1 隐藏零库存",
                        "title",
                        "Is Hide Zero Stock",
                        "type",
                        "string")),
                "title",
                "FbaStockParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                "转换店铺/SKU 条件，返回 FBA 库存。",
                "统一可售、在途、预留单位与更新时间，禁止跨店铺简单相加。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "映射官方库存/Listing/仓库条件，主要通过 rawAndTotal 返回数据。",
                "没有统一可用库存、跨仓关联、仓库解析或有界分页流程。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        701,
                        "private static Map<String, Object> rawAndTotal("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        346,
                        "public Object invoke("))))
        .build();
  }

  private static BuiltinOperation inventoryList() {
    return operation("lingxing.inventory.list")
        .summary("查询仓库库存明细")
        .description("路径 /erp/sc/routing/data/local_inventory/inventoryDetails。本地仓/海外仓。")
        .since("2026-10")
        .category("库存")
        .order(12)
        .cost("none")
        .tags("read", "inventory")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "领星账号名；空则用 LINGXING_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "wid",
                    object(
                        "default",
                        "",
                        "description",
                        "仓库 id，多个用英文逗号分隔",
                        "title",
                        "Wid",
                        "type",
                        "string"),
                    "sku",
                    object(
                        "default",
                        "",
                        "description",
                        "SKU（模糊搜索）",
                        "title",
                        "Sku",
                        "type",
                        "string"),
                    "offset",
                    object("default", 0, "minimum", 0, "title", "Offset", "type", "integer"),
                    "length",
                    object(
                        "default",
                        20,
                        "description",
                        "每页条数，上限 800",
                        "maximum",
                        800,
                        "minimum",
                        1,
                        "title",
                        "Length",
                        "type",
                        "integer")),
                "title",
                "InventoryParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                "按仓库和 SKU 读取本地库存明细。",
                "增加 availability 查询，将仓库、可售和预留合成业务视图。",
                "本地仓与 FBA 含义不同，不能混为同一库存。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "映射官方库存/Listing/仓库条件，主要通过 rawAndTotal 返回数据。",
                "没有统一可用库存、跨仓关联、仓库解析或有界分页流程。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        701,
                        "private static Map<String, Object> rawAndTotal("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        346,
                        "public Object invoke("))))
        .build();
  }

  private static BuiltinOperation listingList() {
    return operation("lingxing.listing.list")
        .summary("查询亚马逊 Listing（含库存字段）")
        .description("路径 /erp/sc/data/mws/listing。含 afn_fulfillable_quantity 等库存字段。")
        .since("2026-10")
        .category("商品")
        .order(6)
        .cost("none")
        .tags("read", "inventory")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "领星账号名；空则用 LINGXING_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "sid",
                    object(
                        "default",
                        "",
                        "description",
                        "店铺id，多个逗号分隔（对应查询亚马逊店铺列表的 sid）",
                        "title",
                        "Sid",
                        "type",
                        "string"),
                    "search_field",
                    object(
                        "default",
                        "",
                        "description",
                        "搜索字段：seller_sku/asin/sku",
                        "title",
                        "Search Field",
                        "type",
                        "string"),
                    "search_value",
                    object(
                        "description",
                        "搜索值，上限10",
                        "items",
                        object(),
                        "title",
                        "Search Value",
                        "type",
                        "array",
                        "default",
                        array()),
                    "offset",
                    object("default", 0, "minimum", 0, "title", "Offset", "type", "integer"),
                    "length",
                    object(
                        "default", 100, "maximum", 1000, "minimum", 1, "title", "Length", "type",
                        "integer")),
                "title",
                "ListingParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                "读取亚马逊 Listing 及库存字段。",
                "增加按站点/SKU 的业务查询，显式显示分页与缺失字段。",
                "Listing 库存是渠道维度，不等于仓库可用量。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "映射官方库存/Listing/仓库条件，主要通过 rawAndTotal 返回数据。",
                "没有统一可用库存、跨仓关联、仓库解析或有界分页流程。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        701,
                        "private static Map<String, Object> rawAndTotal("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        346,
                        "public Object invoke("))))
        .build();
  }

  private static BuiltinOperation messagesDetail() {
    return operation("lingxing.messages.detail")
        .summary("查询站内信正文详情")
        .description("路径 /erp/sc/data/mail/detail。返回正文 text_html/text_plain、附件。")
        .since("2026-10")
        .category("客服")
        .order(3)
        .cost("none")
        .tags("read", "message")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "领星账号名；空则用 LINGXING_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "webmail_uuid",
                    object("description", "邮件唯一标识", "title", "Webmail Uuid", "type", "string")),
                "required",
                array("webmail_uuid"),
                "title",
                "MailDetailParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                "已整理邮件主题、正文、收发地址与附件。",
                "与列表组合成有界邮件查询，并逐封返回补齐错误。",
                "",
                "ready",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "整理邮件正文、收发地址和附件字段。",
                "需要调用方先取得 webmail_uuid，未与列表组合成一次业务检索。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        671,
                        "private Object data("))))
        .build();
  }

  private static BuiltinOperation messagesList() {
    return operation("lingxing.messages.list")
        .summary("查询站内信（邮件消息）列表")
        .description(
            "路径 /erp/sc/data/mail/lists。归一化返回 {items, has_more, cursor} 供 CYT-CS requirement_connectors 同步进 cs_tickets。注意：开放平台只有列表+详情两个查询接口，没有发送/回复接口。")
        .since("2026-10")
        .category("客服")
        .order(2)
        .cost("none")
        .tags("read", "message")
        .params(
            object(
                "description",
                "站内信列表。\n\n🔴 cursor 必须是 `Any`：CYT-CS 的同步框架从 `cs_sync_job.cursor`（JSONB）\n取出来的是 **dict**，第一轮是 `{}`。声明成 `str` 会在 model_validate 阶段\n直接 ValidationError —— 表现为「同步一跑就报参数错误」，看不出是游标类型问题。\n这里同时兼容 数字偏移量 / 数字字符串 / dict 三种形态。",
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "领星账号名；空则用 LINGXING_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "cursor",
                    object(
                        "default",
                        "0",
                        "description",
                        "分页游标：偏移量数字，或 {offset,start_date,end_date}",
                        "title",
                        "Cursor"),
                    "limit",
                    object(
                        "default",
                        50,
                        "description",
                        "每页条数",
                        "maximum",
                        200,
                        "minimum",
                        1,
                        "title",
                        "Limit",
                        "type",
                        "integer"),
                    "flag",
                    object(
                        "default",
                        "receive",
                        "description",
                        "sent=发件 receive=收件",
                        "title",
                        "Flag",
                        "type",
                        "string"),
                    "email",
                    object(
                        "default",
                        "",
                        "description",
                        "店铺绑定邮箱；空则取 LINGXING_<账号>_MAIL_EMAIL",
                        "title",
                        "Email",
                        "type",
                        "string"),
                    "start_date",
                    object(
                        "default",
                        "",
                        "description",
                        "开始日期 yyyy-mm-dd；空则默认最近 30 天",
                        "title",
                        "Start Date",
                        "type",
                        "string"),
                    "end_date",
                    object(
                        "default",
                        "",
                        "description",
                        "结束日期 yyyy-mm-dd；空则默认今天",
                        "title",
                        "End Date",
                        "type",
                        "string")),
                "title",
                "MailListParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                "规范邮件条目与 offset 游标，日期范围随游标保持稳定。",
                "本次修复用户指定短日期被扩到一年的问题；后续增加列表与正文的有界聚合。",
                "已校验正向日期和最多 365 天；仍是单页查询。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "整理邮件页，固定查询日期随游标续传。",
                "不自动读取正文，未提供列表加详情的有界业务查询。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        545,
                        "private Object messagesList("))))
        .build();
  }

  private static BuiltinOperation mpordersList() {
    return operation("lingxing.mporders.list")
        .summary("查询多平台订单列表（含 Shopify/Temu/TikTok）")
        .description(
            "路径 /pb/mp/order/v2/list。🔴 退货工单 workorder.add 的必填字段（globalOrderNo/orderItemNo/platformOrderNo）只能从这里取。⚠️ 时间是秒级时间戳、跨度 ≤31 天；传了单号可不传时间。")
        .since("2026-10")
        .category("订单")
        .order(15)
        .cost("none")
        .tags("read", "order")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "领星账号名；空则用 LINGXING_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "offset",
                    object("default", 0, "minimum", 0, "title", "Offset", "type", "integer"),
                    "length",
                    object(
                        "default",
                        20,
                        "description",
                        "每页条数，上限 500",
                        "maximum",
                        500,
                        "minimum",
                        1,
                        "title",
                        "Length",
                        "type",
                        "integer"),
                    "date_type",
                    object(
                        "default",
                        "update_time",
                        "description",
                        "update_time / global_purchase_time / global_delivery_time / global_payment_time",
                        "title",
                        "Date Type",
                        "type",
                        "string"),
                    "start_time",
                    object(
                        "anyOf",
                        array(object("type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "开始时间，秒级时间戳；跨度 ≤ 31 天",
                        "title",
                        "Start Time"),
                    "end_time",
                    object(
                        "anyOf",
                        array(object("type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "结束时间，秒级时间戳",
                        "title",
                        "End Time"),
                    "platform_code",
                    object(
                        "description",
                        "10001 AMAZON / 10002 Shopify / 10011 TikTok ...",
                        "items",
                        object("type", "string"),
                        "title",
                        "Platform Code",
                        "type",
                        "array",
                        "default",
                        array()),
                    "store_id",
                    object(
                        "description",
                        "多平台店铺 id",
                        "items",
                        object("type", "string"),
                        "title",
                        "Store Id",
                        "type",
                        "array",
                        "default",
                        array()),
                    "platform_order_nos",
                    object(
                        "description",
                        "平台单号，≤200；Shopify/eBay/Newegg/Coupang 等要用 platform_order_names",
                        "items",
                        object("type", "string"),
                        "title",
                        "Platform Order Nos",
                        "type",
                        "array",
                        "default",
                        array()),
                    "platform_order_names",
                    object(
                        "description",
                        "特定平台单号，≤200",
                        "items",
                        object("type", "string"),
                        "title",
                        "Platform Order Names",
                        "type",
                        "array",
                        "default",
                        array()),
                    "global_order_nos",
                    object(
                        "description",
                        "领星系统单号",
                        "items",
                        object("type", "string"),
                        "title",
                        "Global Order Nos",
                        "type",
                        "array",
                        "default",
                        array()),
                    "tracking_nos",
                    object(
                        "description",
                        "跟踪号，≤200",
                        "items",
                        object("type", "string"),
                        "title",
                        "Tracking Nos",
                        "type",
                        "array",
                        "default",
                        array()),
                    "order_status",
                    object(
                        "anyOf",
                        array(object("type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "1同步中 2已同步 3待付款 4待审核 5待发货 6已发货 7已取消 9平台发货",
                        "title",
                        "Order Status")),
                "title",
                "MpOrderListParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                "按多平台条件读取订单，主体返回 raw/total/request_id。",
                "增加跨平台订单检索与平台单号/SKU 映射，显式标识部分行项目。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "支持按多平台单号或时间查询，主体仍为 raw/total/request_id。",
                "工单需要的 globalOrderNo/orderItemNo/PID 仍由调用方抽取并串联后续接口。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        891,
                        "private Object mpordersList("))))
        .build();
  }

  private static BuiltinOperation ordersDetail() {
    return operation("lingxing.orders.detail")
        .summary("查询亚马逊订单详情")
        .description("路径 /erp/sc/data/mws/orderDetail。order_id 支持逗号分隔批量，上限 200。")
        .since("2026-10")
        .category("订单")
        .order(10)
        .cost("none")
        .tags("read", "order")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "领星账号名；空则用 LINGXING_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "order_id",
                    object(
                        "description",
                        "亚马逊订单号，多个用英文逗号分隔，上限 200",
                        "title",
                        "Order Id",
                        "type",
                        "string")),
                "required",
                array("order_id"),
                "title",
                "OrderDetailParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                "读取亚马逊订单详情，主体保留供应商结构。",
                "增加与库存、物流、售后工单的关联查询。",
                "个人信息权限仍以授权账号为准。",
                "ready",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "将供应商订单详情包装为 items/total。",
                "明细主体未归一，未联查物流、售后和库存。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        832,
                        "private Object ordersDetail("))))
        .build();
  }

  private static BuiltinOperation ordersList() {
    return operation("lingxing.orders.list")
        .summary("查询亚马逊订单列表")
        .description(
            "路径 /erp/sc/data/mws/orders。start_date/end_date 必填且跨度不超过一年。返回已归一化字段（order_no/shop/status/amount/tracking_no/items）+ raw。")
        .since("2026-10")
        .category("订单")
        .order(9)
        .cost("none")
        .tags("read", "order")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "领星账号名；空则用 LINGXING_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "start_date",
                    object(
                        "description",
                        "查询开始时间 Y-m-d 或 Y-m-d H:i:s（左闭）",
                        "title",
                        "Start Date",
                        "type",
                        "string"),
                    "end_date",
                    object(
                        "description",
                        "查询结束时间（右开），跨度不能超过一年",
                        "title",
                        "End Date",
                        "type",
                        "string"),
                    "sid",
                    object(
                        "anyOf",
                        array(object("type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "店铺 id（单店）",
                        "title",
                        "Sid"),
                    "sid_list",
                    object(
                        "description",
                        "店铺 id 列表，最多 20 个",
                        "items",
                        object("type", "integer"),
                        "title",
                        "Sid List",
                        "type",
                        "array",
                        "default",
                        array()),
                    "date_type",
                    object(
                        "default",
                        1,
                        "description",
                        "1 订购时间(站点) 2 修改时间(北京) 3 平台更新(UTC) 10 发货时间(站点)",
                        "title",
                        "Date Type",
                        "type",
                        "integer"),
                    "order_status",
                    object(
                        "description",
                        "Pending / Unshipped / PartiallyShipped / Shipped / Canceled",
                        "items",
                        object("type", "string"),
                        "title",
                        "Order Status",
                        "type",
                        "array",
                        "default",
                        array()),
                    "sort_desc_by_date_type",
                    object(
                        "default",
                        0,
                        "description",
                        "0 不排序 1 降序 2 升序",
                        "title",
                        "Sort Desc By Date Type",
                        "type",
                        "integer"),
                    "fulfillment_channel",
                    object(
                        "anyOf",
                        array(object("type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "1 AFN(亚马逊配送) 2 MFN(自发货)",
                        "title",
                        "Fulfillment Channel"),
                    "offset",
                    object("default", 0, "minimum", 0, "title", "Offset", "type", "integer"),
                    "length",
                    object(
                        "default",
                        100,
                        "description",
                        "每页条数，上限 5000",
                        "maximum",
                        5000,
                        "minimum",
                        1,
                        "title",
                        "Length",
                        "type",
                        "integer")),
                "required",
                array("start_date", "end_date"),
                "title",
                "OrderListParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                "支持日期筛选，规范订单与分页字段。",
                "增加有界 collect 与增量同步水位；稳定排序下按订单 ID 去重。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "归一订单、金额、店铺和商品行。",
                "仍需调用方管理 offset/length，未自动汇总或做增量水位同步。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        729,
                        "private Object ordersList("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        773,
                        "private static Map<String, Object> orderRow("))))
        .build();
  }

  private static BuiltinOperation productList() {
    return operation("lingxing.product.list")
        .summary("查询本地产品列表（sku → pid / 品牌 / 状态）")
        .description(
            "路径 /erp/sc/routing/data/local_inventory/productList（文档 docs/Product/ProductLists.md）。8 个参数全可选：offset/length/sku_list/sku_identifier_list/update_time_start/update_time_end/create_time_start/create_time_end（四个时间窗都是**秒级时间戳**、左闭右开）。用途：① 退货工单 pid 兜底（return.qty 查不到商品行时按 sku 反查）；② SKU → 品牌/品类/状态 对照。⚠️ brand_name 可能是空串，业务线判断只能当辅助。")
        .since("2026-10")
        .category("商品")
        .order(17)
        .cost("none")
        .tags("read")
        .params(
            object(
                "description",
                "查询本地产品列表（文档：docs/Product/ProductLists.md）。\n\n⚠️ 文档里 8 个参数**全部可选**，都不传就是「按 id 正序全量翻页」。",
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "领星账号名；空则用 LINGXING_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "offset",
                    object(
                        "default",
                        0,
                        "description",
                        "分页偏移量，默认 0",
                        "minimum",
                        0,
                        "title",
                        "Offset",
                        "type",
                        "integer"),
                    "length",
                    object(
                        "default",
                        1000,
                        "description",
                        "分页长度，默认 1000，上限 1000",
                        "maximum",
                        1000,
                        "minimum",
                        1,
                        "title",
                        "Length",
                        "type",
                        "integer"),
                    "sku_list",
                    object(
                        "description",
                        "本地产品 SKU，精确匹配，可传多个",
                        "items",
                        object("type", "string"),
                        "title",
                        "Sku List",
                        "type",
                        "array",
                        "default",
                        array()),
                    "sku_identifier_list",
                    object(
                        "description",
                        "SKU 识别码，可传多个（和 sku 是两个字段，别混）",
                        "items",
                        object("type", "string"),
                        "title",
                        "Sku Identifier List",
                        "type",
                        "array",
                        "default",
                        array()),
                    "update_time_start",
                    object(
                        "anyOf",
                        array(object("minimum", 0, "type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "更新时间起（秒级时间戳，左闭右开）",
                        "title",
                        "Update Time Start"),
                    "update_time_end",
                    object(
                        "anyOf",
                        array(object("minimum", 0, "type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "更新时间止（秒级时间戳，左闭右开）",
                        "title",
                        "Update Time End"),
                    "create_time_start",
                    object(
                        "anyOf",
                        array(object("minimum", 0, "type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "创建时间起（秒级时间戳，左闭右开）",
                        "title",
                        "Create Time Start"),
                    "create_time_end",
                    object(
                        "anyOf",
                        array(object("minimum", 0, "type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "创建时间止（秒级时间戳，左闭右开）",
                        "title",
                        "Create Time End")),
                "title",
                "ProductListParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                "将本地 SKU、PID、品牌和状态整理成业务目录。",
                "增加唯一 SKU 解析，明确重复与无匹配错误，供工单自动补全。",
                "",
                "ready",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "整理本地 SKU、PID 等商品目录字段。",
                "没有唯一 SKU 解析以及用于工单的自动补齐。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        957,
                        "private Object productList("))))
        .build();
  }

  private static BuiltinOperation refundCreate() {
    return operation("lingxing.refund.create")
        .summary("发起订单退款（写）")
        .description(
            "路径 /basicOpen/openapi/salesOrder/refundOrder。🔴 真金白银的写操作，需 GATEWAY_ENABLE_TEST_WRITES=1 且账号在 GATEWAY_TEST_LINGXING_ACCOUNTS 白名单，否则直接 Forbidden。（当前只读接入：该操作已禁用，调用必 Forbidden）")
        .since("2026-10")
        .category("订单")
        .order(11)
        .cost("none")
        .tags("write", "refund")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "领星账号名；空则用 LINGXING_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "sid",
                    object("description", "店铺 id", "title", "Sid", "type", "integer"),
                    "amazonOrderId",
                    object("description", "亚马逊订单号", "title", "Amazonorderid", "type", "string"),
                    "purchaseDateLocal",
                    object(
                        "description",
                        "订购时间 yyyy-MM-dd HH:mm:ss（站点时间）",
                        "title",
                        "Purchasedatelocal",
                        "type",
                        "string"),
                    "data",
                    object(
                        "description",
                        "商品行退款明细；每行必填 orderItemId/asin/sellerSku/reason/itemList",
                        "items",
                        object("additionalProperties", true, "type", "object"),
                        "title",
                        "Data",
                        "type",
                        "array",
                        "default",
                        array())),
                "required",
                array("sid", "amazonOrderId", "purchaseDateLocal"),
                "title",
                "RefundCreateParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                "把订单与明细转换为退款请求。",
                "先读订单与已退款量，生成退款预检和可退金额说明。",
                "不可替调用方决定退款金额；需要真实订单验收与稳定幂等编号。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "校验测试账号后传递 sid、订单号、购买时间及官方 data。",
                "未联查订单、成功交易、已退款量或生成退款预检计划。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        848,
                        "private Object refundCreate("))))
        .build();
  }

  private static BuiltinOperation returnQty() {
    return operation("lingxing.return.qty")
        .summary("查询已退货数量（含本地产品ID）")
        .description(
            "路径 /basicOpen/customerService/returnOrder/getReturnQty。🔴 workorder.add 要的 pid 和 returnQuantity 只能从这里取，mporders.list 两个都不返回。")
        .since("2026-10")
        .category("客服")
        .order(16)
        .cost("none")
        .tags("read", "refund")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "领星账号名；空则用 LINGXING_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "globalOrderNo",
                    object(
                        "anyOf",
                        array(object("type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "系统单号，来源 mporders.list 的 global_order_no；与 orderItemNo 至少传一个",
                        "title",
                        "Globalorderno"),
                    "orderItemNo",
                    object(
                        "description",
                        "商品行号列表，来源 mporders.list 的 item_info.order_item_no",
                        "items",
                        object("type", "string"),
                        "title",
                        "Orderitemno",
                        "type",
                        "array",
                        "default",
                        array())),
                "title",
                "ReturnQtyParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                "联合返回退货数量和本地产品 ID。",
                "供退货预检校验剩余数量，明确累计值和本次值。",
                "",
                "ready",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "验证订单/行标识并整理退货数量响应。",
                "仍依赖官方订单行号，未与工单和本地商品目录编排。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        933,
                        "private Object returnQty("))))
        .build();
  }

  private static BuiltinOperation sellerList() {
    return operation("lingxing.seller.list")
        .summary("查询亚马逊店铺列表")
        .description("路径 GET /erp/sc/data/seller/lists。返回 sid ↔ 店铺名，订单/退款都要 sid。")
        .since("2026-10")
        .category("基础数据")
        .order(8)
        .cost("none")
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "领星账号名；空则用 LINGXING_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string")),
                "title",
                "_Base",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                "已规范店铺 ID、名称、国家等字段。",
                "作为其他业务接口的店铺选择字典，处理同名店铺。",
                "",
                "ready",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "归一店铺标识、名称等目录字段。",
                "没有店铺业务别名及同名候选解析。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        705,
                        "private Object sellerList("))))
        .build();
  }

  private static BuiltinOperation warehouseList() {
    return operation("lingxing.warehouse.list")
        .summary("查询仓库列表")
        .description("路径 /erp/sc/data/local_inventory/warehouse。")
        .since("2026-10")
        .category("仓储")
        .order(7)
        .cost("none")
        .tags("read", "inventory")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "领星账号名；空则用 LINGXING_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "type",
                    object(
                        "default",
                        1,
                        "description",
                        "仓库类型：1本地仓 3海外仓 4平台仓 6 AWD仓",
                        "title",
                        "Type",
                        "type",
                        "integer"),
                    "sub_type",
                    object(
                        "default",
                        0,
                        "description",
                        "海外仓子类型：1无API 2有API",
                        "title",
                        "Sub Type",
                        "type",
                        "integer"),
                    "is_delete",
                    object(
                        "default",
                        "0",
                        "description",
                        "0未删除 1已删除",
                        "title",
                        "Is Delete",
                        "type",
                        "string"),
                    "offset",
                    object("default", 0, "minimum", 0, "title", "Offset", "type", "integer"),
                    "length",
                    object(
                        "default", 1000, "maximum", 1000, "minimum", 1, "title", "Length", "type",
                        "integer")),
                "title",
                "WarehouseParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                "读取仓库目录。",
                "统一仓库编码、类型、地区和启用状态，供库存业务筛选。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "映射官方库存/Listing/仓库条件，主要通过 rawAndTotal 返回数据。",
                "没有统一可用库存、跨仓关联、仓库解析或有界分页流程。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        701,
                        "private static Map<String, Object> rawAndTotal("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        346,
                        "public Object invoke("))))
        .build();
  }

  private static BuiltinOperation workorderAdd() {
    return operation("lingxing.workorder.add")
        .summary("新增售后工单（仅退货类型）")
        .description(
            "路径 /basicOpen/customerService/workOrder/add。为中台 RMA 流程的亚马逊订单退款/退货写回领星。（当前只读接入：该操作已禁用，调用必 Forbidden）")
        .since("2026-10")
        .category("客服")
        .order(4)
        .cost("none")
        .tags("write", "refund")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "领星账号名；空则用 LINGXING_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "platformOrderNos",
                    object(
                        "description",
                        "平台订单号列表（去重）",
                        "items",
                        object("type", "string"),
                        "title",
                        "Platformordernos",
                        "type",
                        "array"),
                    "afterInfoList",
                    object(
                        "description",
                        "售后明细",
                        "items",
                        object("additionalProperties", true, "type", "object"),
                        "title",
                        "Afterinfolist",
                        "type",
                        "array",
                        "default",
                        array()),
                    "amountCurrency",
                    object("type", "string", "minLength", 1, "maxLength", 2000, "description", ""),
                    "globalPurchaseTime",
                    object("type", "string", "minLength", 1, "maxLength", 2000, "description", ""),
                    "logisticsProvider",
                    object("type", "string", "minLength", 1, "maxLength", 2000, "description", ""),
                    "platformCode",
                    object("type", "string", "minLength", 1, "maxLength", 2000, "description", ""),
                    "preArrivalTime",
                    object("type", "string", "minLength", 1, "maxLength", 2000, "description", ""),
                    "reasonUuid",
                    object("type", "string", "minLength", 1, "maxLength", 2000, "description", ""),
                    "remark",
                    object("type", "string", "minLength", 1, "maxLength", 2000, "description", ""),
                    "siteCode",
                    object("type", "string", "minLength", 1, "maxLength", 2000, "description", ""),
                    "trackingNo",
                    object("type", "string", "minLength", 1, "maxLength", 2000, "description", ""),
                    "returnWidDesc",
                    object("type", "string", "minLength", 1, "maxLength", 2000, "description", ""),
                    "logisticsTypeCode",
                    object("type", "string", "minLength", 1, "maxLength", 2000, "description", ""),
                    "logisticsProviderDesc",
                    object("type", "string", "minLength", 1, "maxLength", 2000, "description", ""),
                    "globalTrackNo",
                    object("type", "string", "minLength", 1, "maxLength", 2000, "description", ""),
                    "waybillNo",
                    object("type", "string", "minLength", 1, "maxLength", 2000, "description", ""),
                    "createManualWork",
                    object("type", "integer", "minimum", 0, "maximum", 9223372036854775807L),
                    "cutOff",
                    object("type", "integer", "minimum", 0, "maximum", 9223372036854775807L),
                    "globalOrderNo",
                    object("type", "integer", "minimum", 0, "maximum", 9223372036854775807L),
                    "hasPrediction",
                    object("type", "integer", "minimum", 0, "maximum", 9223372036854775807L),
                    "logisticsType",
                    object("type", "integer", "minimum", 0, "maximum", 9223372036854775807L),
                    "salesType",
                    object("type", "integer", "minimum", 0, "maximum", 9223372036854775807L),
                    "source",
                    object("type", "integer", "minimum", 0, "maximum", 9223372036854775807L),
                    "storeId",
                    object("type", "integer", "minimum", 0, "maximum", 9223372036854775807L),
                    "sid",
                    object("type", "integer", "minimum", 0, "maximum", 9223372036854775807L),
                    "type",
                    object("type", "integer", "minimum", 0, "maximum", 9223372036854775807L),
                    "wid",
                    object("type", "integer", "minimum", 0, "maximum", 9223372036854775807L),
                    "returnWid",
                    object("type", "integer", "minimum", 0, "maximum", 9223372036854775807L),
                    "deliveryTime",
                    object("type", "integer", "minimum", 0, "maximum", 9223372036854775807L),
                    "logisticsTypeId",
                    object("type", "integer", "minimum", 0, "maximum", 9223372036854775807L),
                    "platform",
                    object("type", "integer", "minimum", 0, "maximum", 9223372036854775807L),
                    "wpType",
                    object("type", "integer", "minimum", 0, "maximum", 9223372036854775807L),
                    "workOrderSupplement",
                    object("type", "object", "additionalProperties", true),
                    "tagList",
                    object(
                        "type",
                        "array",
                        "items",
                        object(
                            "type",
                            "string",
                            "minLength",
                            1,
                            "maxLength",
                            200,
                            "description",
                            ""))),
                "required",
                array("platformOrderNos"),
                "title",
                "WorkOrderAddParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                "限定退货类型并转换工单字段。",
                "增加 prepare：通过订单、SKU 和退货数量补齐 PID、原因和仓库。",
                "业务原因和退货数量需调用方确认，不能自动猜测。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "补齐官方顶层工单字段的传递，避免遗漏系统单号、原因、退货仓等必需信息。",
                "仍需显式提供订单行、PID、原因与退货数量；未自动编排售后审批或退款。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        464,
                        "private static Map<String, Object> workOrderPayload("))))
        .build();
  }

  private static BuiltinOperation workorderAudit() {
    return operation("lingxing.workorder.audit")
        .summary("提交售后工单审批流")
        .description(
            "路径 /basicOpen/customerService/workOrder/auditFlow/summit。（当前只读接入：该操作已禁用，调用必 Forbidden）")
        .since("2026-10")
        .category("客服")
        .order(5)
        .cost("none")
        .tags("write", "refund")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "领星账号名；空则用 LINGXING_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "businessIdList",
                    object(
                        "description",
                        "售后工单业务ID列表",
                        "items",
                        object("type", "string"),
                        "title",
                        "Businessidlist",
                        "type",
                        "array"),
                    "flowId",
                    object("description", "审批流ID", "title", "Flowid", "type", "string")),
                "required",
                array("businessIdList", "flowId"),
                "title",
                "WorkOrderAuditParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                "把已有工单提交到审批流程。",
                "核对工单状态及缺项；返回可继续操作或需人工处理的状态。",
                "提交审核与审批通过不同。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "提交官方工单明细或审批流 ID，返回 raw 和 request_id。",
                "审核流程仍需业务明确选择，未自动审批。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        360,
                        "case \"workorder.add\""),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        367,
                        "case \"workorder.audit\""))))
        .build();
  }

  private static BuiltinOperation workorderList() {
    return operation("lingxing.workorder.list")
        .summary("查询售后工单列表")
        .description("路径 /pb/mp/returns/workOrder/list。用于核对写回领星后的工单状态。")
        .since("2026-10")
        .category("客服")
        .order(14)
        .cost("none")
        .tags("read", "refund")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "领星账号名；空则用 LINGXING_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "start_time",
                    object(
                        "description",
                        "开始时间 Y-m-d H:i:s（闭区间）",
                        "title",
                        "Start Time",
                        "type",
                        "string"),
                    "end_time",
                    object(
                        "description",
                        "结束时间 Y-m-d H:i:s（闭区间）",
                        "title",
                        "End Time",
                        "type",
                        "string"),
                    "date_type",
                    object(
                        "default",
                        "create_time",
                        "description",
                        "create_time / complete_time",
                        "title",
                        "Date Type",
                        "type",
                        "string"),
                    "offset",
                    object("default", 0, "minimum", 0, "title", "Offset", "type", "integer"),
                    "length",
                    object(
                        "default",
                        20,
                        "description",
                        "每页条数，最小 20、最大 500",
                        "maximum",
                        500,
                        "minimum",
                        20,
                        "title",
                        "Length",
                        "type",
                        "integer")),
                "required",
                array("start_time", "end_time"),
                "title",
                "WorkOrderListParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                "读取售后工单并规范分页。",
                "增加按订单的售后进度视图，连接审核、退货与退款状态。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "传递日期/偏移并返回工单 raw/total。",
                "没有按业务订单整合审核、到仓、退款进度。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/lingxing/LingxingAdapter.java",
                        869,
                        "private Object workOrderList("))))
        .build();
  }

  private static BuiltinOperation ordersCollect() {
    return operation("lingxing.orders.collect")
        .summary("自动分页汇总：查询亚马逊订单列表")
        .description("有界自动分页，统一 items/page/errors 与签名续传；保持原筛选，续传时每页大小固定。")
        .timeout(90)
        .category("业务聚合")
        .order(-60)
        .tags("read", "business")
        .params(
            object(
                "type",
                "object",
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "领星账号名；空则用 LINGXING_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "start_date",
                    object(
                        "description",
                        "查询开始时间 Y-m-d 或 Y-m-d H:i:s（左闭）",
                        "title",
                        "Start Date",
                        "type",
                        "string"),
                    "end_date",
                    object(
                        "description",
                        "查询结束时间（右开），跨度不能超过一年",
                        "title",
                        "End Date",
                        "type",
                        "string"),
                    "sid",
                    object(
                        "anyOf",
                        array(object("type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "店铺 id（单店）",
                        "title",
                        "Sid"),
                    "sid_list",
                    object(
                        "description",
                        "店铺 id 列表，最多 20 个",
                        "items",
                        object("type", "integer"),
                        "title",
                        "Sid List",
                        "type",
                        "array",
                        "default",
                        array()),
                    "date_type",
                    object(
                        "default",
                        1,
                        "description",
                        "1 订购时间(站点) 2 修改时间(北京) 3 平台更新(UTC) 10 发货时间(站点)",
                        "title",
                        "Date Type",
                        "type",
                        "integer"),
                    "order_status",
                    object(
                        "description",
                        "Pending / Unshipped / PartiallyShipped / Shipped / Canceled",
                        "items",
                        object("type", "string"),
                        "title",
                        "Order Status",
                        "type",
                        "array",
                        "default",
                        array()),
                    "sort_desc_by_date_type",
                    object(
                        "default",
                        0,
                        "description",
                        "0 不排序 1 降序 2 升序",
                        "title",
                        "Sort Desc By Date Type",
                        "type",
                        "integer"),
                    "fulfillment_channel",
                    object(
                        "anyOf",
                        array(object("type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "1 AFN(亚马逊配送) 2 MFN(自发货)",
                        "title",
                        "Fulfillment Channel"),
                    "page_size",
                    object("type", "integer", "default", 50, "minimum", 1, "maximum", 1000),
                    "max_items",
                    object("type", "integer", "default", 200, "minimum", 1, "maximum", 1000),
                    "max_pages",
                    object("type", "integer", "default", 10, "minimum", 1, "maximum", 100),
                    "resume_cursor",
                    object(
                        "type", "string", "minLength", 1, "maxLength", 16000, "description", "")),
                "required",
                array("start_date", "end_date"),
                "additionalProperties",
                false,
                "examples",
                array(object("start_date", "2026-11-01", "end_date", "2026-11-06"))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                "有界自动分页，统一 items/page/errors 与签名续传；保持原筛选，续传时每页大小固定。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "自动翻页覆盖根记录；嵌套连接、供应商可见范围和来源快照仍有各自限制。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "固定原生页大小、有界自动汇总、页内续传，并绑定系统与筛选条件。",
                "自动分页不是完整业务事务；根记录范围和上游一致性仍有边界。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                        20,
                        "public static final Map<String, Spec> SPECS"),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                        223,
                        "case \"total_offset\""))))
        .build();
  }

  private static BuiltinOperation productCollect() {
    return operation("lingxing.product.collect")
        .summary("自动分页汇总：查询本地产品列表（sku → pid / 品牌 / 状态）")
        .description("有界自动分页，统一 items/page/errors 与签名续传；保持原筛选，续传时每页大小固定。")
        .timeout(90)
        .category("业务聚合")
        .order(-60)
        .tags("read", "business")
        .params(
            object(
                "type",
                "object",
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "领星账号名；空则用 LINGXING_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "sku_list",
                    object(
                        "description",
                        "本地产品 SKU，精确匹配，可传多个",
                        "items",
                        object("type", "string"),
                        "title",
                        "Sku List",
                        "type",
                        "array",
                        "default",
                        array()),
                    "sku_identifier_list",
                    object(
                        "description",
                        "SKU 识别码，可传多个（和 sku 是两个字段，别混）",
                        "items",
                        object("type", "string"),
                        "title",
                        "Sku Identifier List",
                        "type",
                        "array",
                        "default",
                        array()),
                    "update_time_start",
                    object(
                        "anyOf",
                        array(object("minimum", 0, "type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "更新时间起（秒级时间戳，左闭右开）",
                        "title",
                        "Update Time Start"),
                    "update_time_end",
                    object(
                        "anyOf",
                        array(object("minimum", 0, "type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "更新时间止（秒级时间戳，左闭右开）",
                        "title",
                        "Update Time End"),
                    "create_time_start",
                    object(
                        "anyOf",
                        array(object("minimum", 0, "type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "创建时间起（秒级时间戳，左闭右开）",
                        "title",
                        "Create Time Start"),
                    "create_time_end",
                    object(
                        "anyOf",
                        array(object("minimum", 0, "type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "创建时间止（秒级时间戳，左闭右开）",
                        "title",
                        "Create Time End"),
                    "page_size",
                    object("type", "integer", "default", 50, "minimum", 1, "maximum", 1000),
                    "max_items",
                    object("type", "integer", "default", 200, "minimum", 1, "maximum", 1000),
                    "max_pages",
                    object("type", "integer", "default", 10, "minimum", 1, "maximum", 100),
                    "resume_cursor",
                    object(
                        "type", "string", "minLength", 1, "maxLength", 16000, "description", "")),
                "required",
                array(),
                "additionalProperties",
                false,
                "examples",
                array(object())))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                "有界自动分页，统一 items/page/errors 与签名续传；保持原筛选，续传时每页大小固定。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "自动翻页覆盖根记录；嵌套连接、供应商可见范围和来源快照仍有各自限制。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "固定原生页大小、有界自动汇总、页内续传，并绑定系统与筛选条件。",
                "自动分页不是完整业务事务；根记录范围和上游一致性仍有边界。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                        20,
                        "public static final Map<String, Spec> SPECS"),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                        223,
                        "case \"total_offset\""))))
        .build();
  }
}
