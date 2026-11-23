package group.cyt.common.module.registry.catalog.platform;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import group.cyt.common.module.registry.catalog.*;
import java.util.List;

/** Built-in mabang contracts. Edit metadata, parameters and review together here. */
public final class MabangCatalog implements PlatformCatalog {
  @Override
  public void register(CatalogBuilder catalog) {
    catalog.platform("mabang", "马帮 ERP（订单 / 物流轨迹）", "30/min");
    catalog.credentials(credentials());
    catalog.operation(ordersList());
    catalog.operation(ordersTrack());
    catalog.operation(ordersCollect());
  }

  private static PlatformCredentials credentials() {
    return new PlatformCredentials(
        "mabang",
        "马帮 ERP（订单 / 物流轨迹）",
        "https://gwapi.mabangerp.com",
        "签名是 appkey + HMAC-SHA256（和 CYT-Buyer 同一套），必须经 erp-proxy 中转：厂商按来源 IP 鉴权，代理服务器 IP 已在白名单里。凭证可复用 CYT-Buyer 现有的。",
        "inherit",
        true,
        false,
        "",
        "",
        "",
        List.of(
            new PlatformCredentials.Variable(
                "MABANG_APP_KEY",
                "appkey",
                true,
                false,
                "马帮开放平台的应用 appkey；与 CYT-Buyer 用同一套即可",
                "",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "MABANG_SECRET",
                "签名密钥 secret",
                true,
                true,
                "与 appkey 配套，用于 HMAC-SHA256 签名",
                "",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "MABANG_PROXY_BASE",
                "erp-proxy 地址",
                false,
                false,
                "不填则回落到共用的 ERP_PROXY_BASE",
                "https://erp-proxy.cyt.group",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "MABANG_PROXY_TOKEN",
                "erp-proxy 令牌",
                false,
                true,
                "X-Proxy-Token；不填则回落到共用的 ERP_PROXY_TOKEN",
                "",
                false,
                "",
                false)));
  }

  private static BuiltinOperation ordersList() {
    return operation("mabang.orders.list")
        .summary("查询马帮订单列表（cursor 翻页）")
        .description(
            "api=order-get-order-list-new。返回 {orders, total, has_next, next_cursor}，订单含 trackNumber/expressTime/orderStatus/isTuotou/isTrackOnline/shopName 等。按发货时间窗 expressTimeStart/End 拉在途单、供 CYT-CS 物流异常扫描。")
        .timeout(60.0)
        .since("2026-10")
        .category("订单")
        .order(1)
        .cost("none")
        .tags("read", "order")
        .params(
            object(
                "properties",
                object(
                    "status",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "2配货中 3已发货 4已完成 5已作废 6所有未发货 7所有非未发货",
                        "title",
                        "Status"),
                    "expressTimeStart",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "发货时间开始 YYYY-MM-DD HH:mm:ss",
                        "title",
                        "Expresstimestart"),
                    "expressTimeEnd",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "发货时间结束",
                        "title",
                        "Expresstimeend"),
                    "createDateStart",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "创建时间开始",
                        "title",
                        "Createdatestart"),
                    "createDateEnd",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "创建时间结束",
                        "title",
                        "Createdateend"),
                    "updateTimeStart",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "更新开始时间",
                        "title",
                        "Updatetimestart"),
                    "updateTimeEnd",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "更新结束时间",
                        "title",
                        "Updatetimeend"),
                    "paidtimeStart",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "付款时间开始",
                        "title",
                        "Paidtimestart"),
                    "paidtimeEnd",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "付款时间结束",
                        "title",
                        "Paidtimeend"),
                    "canSend",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "1正常订单 2异常订单 3全部订单，默认正常",
                        "title",
                        "Cansend"),
                    "shopName",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "店铺名称，支持多个",
                        "title",
                        "Shopname"),
                    "platformOrderIds",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "订单编号，逗号分隔最多10个",
                        "title",
                        "Platformorderids"),
                    "trackNumbers",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "运单号，逗号分隔最多10个",
                        "title",
                        "Tracknumbers"),
                    "cursor",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "翻页游标，回传上一页的 nextCursor",
                        "title",
                        "Cursor"),
                    "maxRows",
                    object(
                        "anyOf",
                        array(
                            object("maximum", 1000, "minimum", 1, "type", "integer"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "分页查询数，默认100上限1000",
                        "title",
                        "Maxrows")),
                "title",
                "OrderListParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/mabang/MabangAdapter.java",
                "已封装马帮签名、筛选和 cursor 翻页。",
                "优先使用新增有界自动分页接口，减少调用方的游标处理。",
                "",
                "recommended",
                List.of("mabang.orders.collect"),
                CapabilityReview.Depth.ENHANCED,
                "统一订单页和 next_cursor/has_next，识别响应形态。",
                "订单数据仍来自供应商结构，调用方原接口需自己翻页。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/mabang/MabangAdapter.java",
                        160,
                        "case \"mabang.orders.list\""))))
        .build();
  }

  private static BuiltinOperation ordersTrack() {
    return operation("mabang.orders.track")
        .summary("查询马帮订单物流轨迹（批量 ≤100）")
        .description(
            "api=wl-query-track-info。platformOrderId 逗号分隔。返回 {track_infos, count}，每项含 status/status_text（1未上网 2运输中 3妥投 4异常 5包裹退回 6未查到 7到达待取 8投递失败 9运输过久 12派送途中）、trackInfo 轨迹节点（时间正序）、stay 停留天数、signTime、remark。")
        .timeout(60.0)
        .since("2026-10")
        .category("物流")
        .order(2)
        .cost("none")
        .tags("read", "logistics")
        .params(
            object(
                "properties",
                object(
                    "platformOrderId",
                    object(
                        "description",
                        "马帮订单号，多个用英文逗号隔开（最多100个）",
                        "title",
                        "Platformorderid",
                        "type",
                        "string"),
                    "lastUpdateTime",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "最后更新时间",
                        "title",
                        "Lastupdatetime"),
                    "page",
                    object(
                        "anyOf",
                        array(object("minimum", 1, "type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "当前页",
                        "title",
                        "Page")),
                "required",
                array("platformOrderId"),
                "title",
                "TrackParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/mabang/MabangAdapter.java",
                "支持最多 100 个订单查询，并整理物流状态。",
                "可补批量拆组和每单错误，不因一个失败丢失其他物流。",
                "物流事件不等于订单结算状态。",
                "ready",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "兼容空响应并归一批量物流状态和轨迹。",
                "未拆分超过单批上限的订单，也未提供逐单错误与订单查询联动。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/mabang/MabangAdapter.java",
                        179,
                        "case \"mabang.orders.track\""))))
        .build();
  }

  private static BuiltinOperation ordersCollect() {
    return operation("mabang.orders.collect")
        .summary("自动分页汇总：查询马帮订单列表（cursor 翻页）")
        .description(
            "自动分页、统一 items/page/errors；支持 max_items、max_pages 与签名续传游标。仅扫描根记录，嵌套资源遵循原接口的范围。读取能力独立授权，保留原接口兼容。")
        .timeout(90)
        .category("业务聚合")
        .order(-50)
        .sourceOperation("mabang.orders.list")
        .tags("read", "business", "collection")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "status",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "2配货中 3已发货 4已完成 5已作废 6所有未发货 7所有非未发货",
                        "title",
                        "Status"),
                    "expressTimeStart",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "发货时间开始 YYYY-MM-DD HH:mm:ss",
                        "title",
                        "Expresstimestart"),
                    "expressTimeEnd",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "发货时间结束",
                        "title",
                        "Expresstimeend"),
                    "createDateStart",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "创建时间开始",
                        "title",
                        "Createdatestart"),
                    "createDateEnd",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "创建时间结束",
                        "title",
                        "Createdateend"),
                    "updateTimeStart",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "更新开始时间",
                        "title",
                        "Updatetimestart"),
                    "updateTimeEnd",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "更新结束时间",
                        "title",
                        "Updatetimeend"),
                    "paidtimeStart",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "付款时间开始",
                        "title",
                        "Paidtimestart"),
                    "paidtimeEnd",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "付款时间结束",
                        "title",
                        "Paidtimeend"),
                    "canSend",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "1正常订单 2异常订单 3全部订单，默认正常",
                        "title",
                        "Cansend"),
                    "shopName",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "店铺名称，支持多个",
                        "title",
                        "Shopname"),
                    "platformOrderIds",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "订单编号，逗号分隔最多10个",
                        "title",
                        "Platformorderids"),
                    "trackNumbers",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "运单号，逗号分隔最多10个",
                        "title",
                        "Tracknumbers"),
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
                "保留原生订单结构，未联查物流或售后。",
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
