package group.cyt.common.module.registry.catalog.platform;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import group.cyt.common.module.registry.catalog.*;
import java.util.List;

/** Built-in winit contracts. Edit metadata, parameters and review together here. */
public final class WinitCatalog implements PlatformCatalog {
  @Override
  public void register(CatalogBuilder catalog) {
    catalog.platform("winit", "万邑通海外仓（退货 / 物流轨迹）", "120/min");
    catalog.credentials(credentials());
    catalog.operation(inboundList());
    catalog.operation(inboundTracking());
    catalog.operation(inventoryList());
    catalog.operation(outboundList());
    catalog.operation(outboundTracking());
    catalog.operation(returnsCreate());
    catalog.operation(returnsItems());
    catalog.operation(returnsLabel());
    catalog.operation(returnsList());
    catalog.operation(returnsPsc());
    catalog.operation(returnsVoid());
    catalog.operation(warehousesList());
  }

  private static PlatformCredentials credentials() {
    return new PlatformCredentials(
        "winit",
        "万邑通海外仓",
        "https://developer.winit.com.cn",
        "单租户：一个网关实例对应一套应用。platform 就是后台「应用Code」，大小写敏感；正式/沙箱两套应用不通用，用错会报「应用不存在」",
        "inherit",
        true,
        false,
        "",
        "",
        "",
        List.of(
            new PlatformCredentials.Variable(
                "WINIT_ENV",
                "环境",
                false,
                false,
                "sandbox / prod，不填默认 sandbox",
                "sandbox",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "WINIT_APP_KEY",
                "万邑联账号",
                true,
                false,
                "登录万邑联的账号；沙箱固定 rebecca",
                "rebecca",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "WINIT_TOKEN",
                "卖家 Token",
                true,
                true,
                "万邑联 → 个人中心 → 系统设置 → 获取 Token；沙箱有公共值",
                "",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "WINIT_PLATFORM",
                "应用Code",
                true,
                false,
                "开发者平台创建应用时填的 Code，同时是签名参数 platform（大小写敏感）",
                "",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "WINIT_CLIENT_ID",
                "client_id",
                true,
                false,
                "【沙箱测试→沙箱应用】或【我的应用】里查看；两个环境不通用",
                "",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "WINIT_CLIENT_SECRET",
                "client_secret",
                true,
                true,
                "和 client_id 配套，用来算 client_sign",
                "",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "WINIT_PROD_CLIENT_ID",
                "生产 client_id（可选）",
                false,
                false,
                "配了就优先于 WINIT_CLIENT_ID；WINIT_ENV=prod 时生效。在开发者平台【我的应用】里，不是【沙箱应用】",
                "",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "WINIT_PROD_CLIENT_SECRET",
                "生产 client_secret（可选）",
                false,
                true,
                "和 WINIT_PROD_CLIENT_ID 配套；不填则回落到 WINIT_CLIENT_SECRET",
                "",
                false,
                "",
                false)));
  }

  private static BuiltinOperation inboundList() {
    return operation("winit.inbound.list")
        .summary("查询入库单（退件是否到仓）")
        .description("判断退货包裹有没有真的到仓。仓库 + 订单类型 + 日期区间 + 分页都要给，缺一个就返回空列表。")
        .since("2026-10")
        .category("物流")
        .order(1)
        .cost("none")
        .tags("read")
        .params(
            object(
                "description",
                "查询入库单（判断退件是否到仓）。",
                "properties",
                object(
                    "pageNo",
                    object(
                        "default",
                        1,
                        "description",
                        "页码，从 1 开始",
                        "minimum",
                        1,
                        "title",
                        "Pageno",
                        "type",
                        "integer"),
                    "pageSize",
                    object(
                        "default",
                        50,
                        "description",
                        "每页条数，最大 200",
                        "maximum",
                        200,
                        "minimum",
                        1,
                        "title",
                        "Pagesize",
                        "type",
                        "integer"),
                    "destnationWareHouseCode",
                    object(
                        "description",
                        "目的仓，如 US0001；注意拼写是 destnation",
                        "title",
                        "Destnationwarehousecode",
                        "type",
                        "string"),
                    "inspectionWareHouseCode",
                    object(
                        "description",
                        "验货仓，如 SZ0001",
                        "title",
                        "Inspectionwarehousecode",
                        "type",
                        "string"),
                    "orderType",
                    object(
                        "default",
                        "SD",
                        "description",
                        "SD/DW/DI",
                        "enum",
                        array("SD", "DW", "DI"),
                        "title",
                        "Ordertype",
                        "type",
                        "string"),
                    "orderCreateDateStart",
                    object(
                        "description",
                        "下单时间起（yyyy-MM-dd）",
                        "title",
                        "Ordercreatedatestart",
                        "type",
                        "string"),
                    "orderCreateDateEnd",
                    object(
                        "description",
                        "下单时间止（yyyy-MM-dd）",
                        "title",
                        "Ordercreatedateend",
                        "type",
                        "string")),
                "required",
                array(
                    "destnationWareHouseCode",
                    "inspectionWareHouseCode",
                    "orderCreateDateStart",
                    "orderCreateDateEnd"),
                "title",
                "InboundList",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                "已整理入库结果、到仓状态和分页。",
                "按客户退货单提供到仓进度，关联物流和验收结果。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "映射官方条件并包装 pageParams/list 或 warehouseList。",
                "仍需要官方仓库、单号和日期字段，列表内容未形成统一业务实体或关联流程。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                        197,
                        "private Map<String, Object> buildData("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                        454,
                        "public Object invoke("))))
        .build();
  }

  private static BuiltinOperation inboundTracking() {
    return operation("winit.inbound.tracking")
        .summary("入库单轨迹（R10 物流异常）")
        .description(
            "R10 物流异常主动触达的数据源。返回 trackingList，每条含 date / trackingDesc / trackingCode，可据此判断停滞天数。")
        .since("2026-10")
        .category("物流")
        .order(2)
        .cost("none")
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "orderNo",
                    object("description", "入库单号，WI 开头", "title", "Orderno", "type", "string")),
                "required",
                array("orderNo"),
                "title",
                "InboundTracking",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                "已提取入库轨迹并处理供应商特殊响应形态。",
                "统一轨迹时间、事件码与异常提示，保留原始事件。",
                "",
                "ready",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "按入库单返回 trackingList。",
                "未将入库、物流异常和到仓状态合成业务进度。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                        249,
                        "case \"inbound.tracking\" ->"))))
        .build();
  }

  private static BuiltinOperation inventoryList() {
    return operation("winit.inventory.list")
        .summary("查询海外仓库存")
        .description("按仓库查库存。warehouseCode 必须是 US0001 这种格式，传 USWC 会报「仓库[true]不存在」。")
        .since("2026-10")
        .category("库存")
        .order(1)
        .cost("none")
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "warehouseCode",
                    object(
                        "description",
                        "仓库编码，如 US0001（不能用 USWC 这种别名）",
                        "title",
                        "Warehousecode",
                        "type",
                        "string"),
                    "pageNum",
                    object(
                        "default",
                        1,
                        "description",
                        "页码",
                        "minimum",
                        1,
                        "title",
                        "Pagenum",
                        "type",
                        "integer"),
                    "pageSize",
                    object(
                        "default",
                        100,
                        "description",
                        "每页条数",
                        "maximum",
                        500,
                        "minimum",
                        1,
                        "title",
                        "Pagesize",
                        "type",
                        "integer"),
                    "isActive",
                    object(
                        "default",
                        "",
                        "description",
                        "商品是否有效 Y/N",
                        "title",
                        "Isactive",
                        "type",
                        "string"),
                    "inReturnInventory",
                    object(
                        "default",
                        "",
                        "description",
                        "只看退货库存 Y/N",
                        "title",
                        "Inreturninventory",
                        "type",
                        "string"),
                    "isFilterZeroInventory",
                    object(
                        "default",
                        "",
                        "description",
                        "过滤库存为 0 Y/N",
                        "title",
                        "Isfilterzeroinventory",
                        "type",
                        "string")),
                "required",
                array("warehouseCode"),
                "title",
                "InventoryList",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                "规范 SKU/仓库库存页。",
                "增加可用量业务查询，明确预留、在途及库存计量单位。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "映射官方条件并包装 pageParams/list 或 warehouseList。",
                "仍需要官方仓库、单号和日期字段，列表内容未形成统一业务实体或关联流程。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                        197,
                        "private Map<String, Object> buildData("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                        454,
                        "public Object invoke("))))
        .build();
  }

  private static BuiltinOperation outboundList() {
    return operation("winit.outbound.list")
        .summary("查询出库单（发给终端客户的订单）")
        .description(
            "R10 物流异常主数据源。含收件人 / 国家 / 运单号 / 派送商 / 地址异常标志，状态里能区分派送失败(DLF)与派送完成(DLC)。⚠️ status 必须传 ALL，不传会漏掉作废单。")
        .since("2026-10")
        .category("物流")
        .cost("none")
        .tags("read")
        .params(
            object(
                "description",
                "查询出库单（发给终端客户的订单）。\n\n⚠️ 这才是 R10「物流异常主动触达」该用的数据源，不是入库单：\n入库单（winit.wh.inbound.getOrderList）是卖家往海外仓补货，\n没有收件人、没有终端客户；出库单才有 name/countryName/trackingNo，\n也才有派送失败（DLF）和地址异常（isAddressError）。",
                "properties",
                object(
                    "pageNo",
                    object(
                        "default",
                        1,
                        "description",
                        "页码，从 1 开始",
                        "minimum",
                        1,
                        "title",
                        "Pageno",
                        "type",
                        "integer"),
                    "pageSize",
                    object(
                        "default",
                        50,
                        "description",
                        "每页条数，最大 200",
                        "maximum",
                        200,
                        "minimum",
                        1,
                        "title",
                        "Pagesize",
                        "type",
                        "integer"),
                    "dateOrderedStartDate",
                    object(
                        "description",
                        "出库单提交时间起（yyyy-MM-dd）",
                        "title",
                        "Dateorderedstartdate",
                        "type",
                        "string"),
                    "dateOrderedEndDate",
                    object(
                        "description",
                        "出库单提交时间止（yyyy-MM-dd）",
                        "title",
                        "Dateorderedenddate",
                        "type",
                        "string"),
                    "status",
                    object(
                        "default",
                        "ALL",
                        "description",
                        "状态编码。默认必须传 ALL —— 不传会漏掉作废单，传 VO 则只看作废。见官方 id=275",
                        "title",
                        "Status",
                        "type",
                        "string"),
                    "trackingNo",
                    object(
                        "default",
                        "",
                        "description",
                        "快递单号",
                        "title",
                        "Trackingno",
                        "type",
                        "string"),
                    "sellerOrderNo",
                    object(
                        "default",
                        "",
                        "description",
                        "卖家订单号（可用来关联 Shopify 订单）",
                        "title",
                        "Sellerorderno",
                        "type",
                        "string"),
                    "receiverName",
                    object(
                        "default",
                        "",
                        "description",
                        "收件人姓名",
                        "title",
                        "Receivername",
                        "type",
                        "string")),
                "required",
                array("dateOrderedStartDate", "dateOrderedEndDate"),
                "title",
                "OutboundList",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                "转换出库订单条件并整理分页。",
                "增加按平台单号的关联解析与出库进度。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "映射官方条件并包装 pageParams/list 或 warehouseList。",
                "仍需要官方仓库、单号和日期字段，列表内容未形成统一业务实体或关联流程。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                        197,
                        "private Map<String, Object> buildData("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                        454,
                        "public Object invoke("))))
        .build();
  }

  private static BuiltinOperation outboundTracking() {
    return operation("winit.outbound.tracking")
        .summary("出库单轨迹（R10 物流异常）")
        .description("按运单号批量查轨迹，一次可传多个 trackingNos。")
        .since("2026-10")
        .category("物流")
        .order(3)
        .cost("none")
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "trackingNos",
                    object(
                        "description",
                        "运单号列表，一次可查多个",
                        "items",
                        object("type", "string"),
                        "minItems",
                        1,
                        "title",
                        "Trackingnos",
                        "type",
                        "array")),
                "required",
                array("trackingNos"),
                "title",
                "OutboundTracking",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                "正确拼接 trackingnos 并整理批量轨迹。",
                "增加逐单错误与异常事件归一。",
                "",
                "ready",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "统一轨迹字段并排序，兼容批量结果。",
                "缺少逐单异常解释和出库订单关联。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                        351,
                        "private static Map<String, Object> outboundTrackingResult("))))
        .build();
  }

  private static BuiltinOperation returnsCreate() {
    return operation("winit.returns.create")
        .summary("创建退货订单（取 ReturnLabel 面单）")
        .description(
            "官方 id=151。会向承运商取面单、产生实际运费，所以必须 confirm=true，并且目的仓要在 GATEWAY_TEST_WINIT_WAREHOUSES 白名单里。retry_max=0：建单重试就是可能重复建单，宁可失败后人工核对。customerOrderNo 建议传中台 case_no，让万邑通侧天然去重。")
        .quota("10/min")
        .sensitive(true)
        .idempotent(true)
        .timeout(60)
        .retries(0)
        .since("2026-10")
        .category("退货")
        .order(6)
        .cost("none")
        .tags("write", "danger")
        .params(
            object(
                "$defs",
                object(
                    "ReturnGoodsItem",
                    object(
                        "properties",
                        object(
                            "productCode",
                            object(
                                "description",
                                "商品编码（SKU）。非WINIT出库时该商品必须已在万邑通注册并审核通过，否则报「商品编码不存在」",
                                "maxLength",
                                200,
                                "minLength",
                                1,
                                "title",
                                "Productcode",
                                "type",
                                "string"),
                            "productNum",
                            object(
                                "default",
                                1,
                                "description",
                                "退货数量",
                                "maximum",
                                10000,
                                "minimum",
                                1,
                                "title",
                                "Productnum",
                                "type",
                                "integer"),
                            "handleMethod",
                            object(
                                "default",
                                "SA",
                                "description",
                                "退货策略：SA=直接上架 NS=拍照暂存 DE=销毁 QC=质检服务 WI=WINIT检查上架（QC/WI 需先联系万邑通销售开权限）",
                                "enum",
                                array("SA", "NS", "DE", "QC", "WI"),
                                "title",
                                "Handlemethod",
                                "type",
                                "string"),
                            "shelveMethod",
                            object(
                                "anyOf",
                                array(
                                    object(
                                        "enum", array("GOOD_SA", "DEFECTIVE_SA"), "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "策略=SA 时的上架类型；不传按良品上架",
                                "title",
                                "Shelvemethod"),
                            "goodMethod",
                            object(
                                "anyOf",
                                array(
                                    object("const", "SA", "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "策略=WI 时必填：检查为良品的处理方式",
                                "title",
                                "Goodmethod"),
                            "defectiveMethod",
                            object(
                                "anyOf",
                                array(
                                    object("enum", array("SA", "DE"), "type", "string"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "策略=WI 时必填：检查为不良品的处理方式",
                                "title",
                                "Defectivemethod"),
                            "specification",
                            object(
                                "default",
                                "",
                                "description",
                                "规格",
                                "maxLength",
                                200,
                                "title",
                                "Specification",
                                "type",
                                "string"),
                            "itemCodes",
                            object(
                                "description",
                                "退货单品条码列表；填了则数量必须等于 productNum（万邑通 02050240099）",
                                "items",
                                object("type", "string"),
                                "maxItems",
                                100,
                                "title",
                                "Itemcodes",
                                "type",
                                "array",
                                "default",
                                array())),
                        "required",
                        array("productCode"),
                        "title",
                        "ReturnGoodsItem",
                        "type",
                        "object")),
                "description",
                "创建退货订单（官方 id=151，`rma.returnGoodsOrder.createReturnGoodsOrder`）。",
                "properties",
                object(
                    "confirm",
                    object(
                        "default",
                        false,
                        "description",
                        "二次确认：建退货单会产生实际运费成本，必须显式传 true 才会打到万邑通",
                        "title",
                        "Confirm",
                        "type",
                        "boolean"),
                    "warehouseCode",
                    object(
                        "description",
                        "退货目的仓（万邑通退货中心仓编码，US0001 这种格式）",
                        "maxLength",
                        40,
                        "minLength",
                        1,
                        "title",
                        "Warehousecode",
                        "type",
                        "string"),
                    "customerOrderNo",
                    object(
                        "default",
                        "",
                        "description",
                        "卖家侧单号。建议传中台 case_no，万邑通可据此去重",
                        "maxLength",
                        60,
                        "title",
                        "Customerorderno",
                        "type",
                        "string"),
                    "isWinitOutbound",
                    object(
                        "default",
                        "N",
                        "description",
                        "Y=退货来源是万邑通出库单（要给 outboundOrderNo）；N=非万邑通出库，仅支持 ReturnLabel 服务",
                        "enum",
                        array("Y", "N"),
                        "title",
                        "Iswinitoutbound",
                        "type",
                        "string"),
                    "outboundOrderNo",
                    object(
                        "default",
                        "",
                        "description",
                        "万邑通出库单号 WO…",
                        "maxLength",
                        40,
                        "title",
                        "Outboundorderno",
                        "type",
                        "string"),
                    "shippingNo",
                    object(
                        "default",
                        "",
                        "description",
                        "出库单子单号（一单多子单时必填）",
                        "maxLength",
                        40,
                        "title",
                        "Shippingno",
                        "type",
                        "string"),
                    "winitProductCode",
                    object(
                        "default",
                        "",
                        "description",
                        "退货渠道 PSC。要 ReturnLabel 面单时必填，取值见 queryPSCList 里 deliveryService=Y 的那些",
                        "maxLength",
                        40,
                        "title",
                        "Winitproductcode",
                        "type",
                        "string"),
                    "expressNo",
                    object(
                        "default",
                        "",
                        "description",
                        "买家自有退货运单号；选 ReturnLabel 时不填",
                        "maxLength",
                        60,
                        "title",
                        "Expressno",
                        "type",
                        "string"),
                    "supplierName",
                    object(
                        "default",
                        "",
                        "description",
                        "运单供应商；选 ReturnLabel 时不填",
                        "maxLength",
                        60,
                        "title",
                        "Suppliername",
                        "type",
                        "string"),
                    "VATNo",
                    object(
                        "default",
                        "",
                        "description",
                        "目的仓在德国/英国且是非万邑通出库时必填（万邑通会校验）",
                        "maxLength",
                        40,
                        "title",
                        "Vatno",
                        "type",
                        "string"),
                    "userInfoSource",
                    object(
                        "default",
                        "IRI",
                        "description",
                        "ORI=沿用出库单收件人（此时 name/country/… 全部不要传）；IRI=手填退货人地址。非WINIT出库只能是 IRI",
                        "enum",
                        array("ORI", "IRI"),
                        "title",
                        "Userinfosource",
                        "type",
                        "string"),
                    "name",
                    object(
                        "default",
                        "",
                        "description",
                        "退货人姓名（IRI 必填）",
                        "maxLength",
                        120,
                        "title",
                        "Name",
                        "type",
                        "string"),
                    "email",
                    object(
                        "default",
                        "",
                        "description",
                        "退货人邮箱",
                        "maxLength",
                        255,
                        "title",
                        "Email",
                        "type",
                        "string"),
                    "phone",
                    object(
                        "default",
                        "",
                        "description",
                        "退货人电话",
                        "maxLength",
                        60,
                        "title",
                        "Phone",
                        "type",
                        "string"),
                    "country",
                    object(
                        "default",
                        "",
                        "description",
                        "退货人国家（IRI 必填）",
                        "maxLength",
                        60,
                        "title",
                        "Country",
                        "type",
                        "string"),
                    "state",
                    object(
                        "default",
                        "",
                        "description",
                        "退货人州省",
                        "maxLength",
                        60,
                        "title",
                        "State",
                        "type",
                        "string"),
                    "city",
                    object(
                        "default",
                        "",
                        "description",
                        "退货人城市（IRI 必填）",
                        "maxLength",
                        60,
                        "title",
                        "City",
                        "type",
                        "string"),
                    "postcode",
                    object(
                        "default",
                        "",
                        "description",
                        "退货人邮编（IRI 必填）",
                        "maxLength",
                        20,
                        "title",
                        "Postcode",
                        "type",
                        "string"),
                    "houseNo",
                    object(
                        "default",
                        "",
                        "description",
                        "门牌号",
                        "maxLength",
                        40,
                        "title",
                        "Houseno",
                        "type",
                        "string"),
                    "address1",
                    object(
                        "default",
                        "",
                        "description",
                        "退货人地址（IRI 必填）",
                        "maxLength",
                        200,
                        "title",
                        "Address1",
                        "type",
                        "string"),
                    "address2",
                    object(
                        "default",
                        "",
                        "description",
                        "退货人地址2",
                        "maxLength",
                        200,
                        "title",
                        "Address2",
                        "type",
                        "string"),
                    "collectionDate",
                    object(
                        "default",
                        "",
                        "description",
                        "取件日期 yyyy-MM-dd",
                        "maxLength",
                        20,
                        "title",
                        "Collectiondate",
                        "type",
                        "string"),
                    "collectionAddress",
                    object(
                        "default",
                        "",
                        "description",
                        "取件偏好：Collect from Porch / Garage / Greenhouse",
                        "maxLength",
                        60,
                        "title",
                        "Collectionaddress",
                        "type",
                        "string"),
                    "packageLength",
                    object(
                        "anyOf",
                        array(
                            object("exclusiveMinimum", 0, "type", "number"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "包裹长（非WINIT出库必填）",
                        "title",
                        "Packagelength"),
                    "packageWidth",
                    object(
                        "anyOf",
                        array(
                            object("exclusiveMinimum", 0, "type", "number"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "包裹宽（非WINIT出库必填）",
                        "title",
                        "Packagewidth"),
                    "packageHeight",
                    object(
                        "anyOf",
                        array(
                            object("exclusiveMinimum", 0, "type", "number"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "包裹高（非WINIT出库必填）",
                        "title",
                        "Packageheight"),
                    "packageWeight",
                    object(
                        "anyOf",
                        array(
                            object("exclusiveMinimum", 0, "type", "number"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "包裹重量（非WINIT出库必填）",
                        "title",
                        "Packageweight"),
                    "ReturnGoodsList",
                    object(
                        "description",
                        "退货商品列表",
                        "items",
                        object("$ref", "#/$defs/ReturnGoodsItem"),
                        "maxItems",
                        50,
                        "minItems",
                        1,
                        "title",
                        "Returngoodslist",
                        "type",
                        "array")),
                "required",
                array("warehouseCode", "ReturnGoodsList"),
                "title",
                "ReturnCreate",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                "已有退货字段校验与地址/箱尺/商品转换，返回退货单号、RMA 和地址。",
                "联查渠道、商品档案与仓库形成退货计划，再自动衔接面单查询。",
                "当前创建结果不含面单，仍需调用 returns.label；业务地址和箱尺需确认。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "验证退货字段并构造商品/包裹，返回退货单号、RMA 和退货地址。",
                "仓库、SKU、渠道和箱尺仍需人工拼装，面单还需调用 returns.label；没有一键退货计划。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                        273,
                        "private static Map<String, Object> returnCreateData("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                        477,
                        "case \"returns.create\" -> {"))))
        .build();
  }

  private static BuiltinOperation returnsItems() {
    return operation("winit.returns.items")
        .summary("查询退货商品档案（SKU 建档与箱尺来源）")
        .description(
            "万邑通侧已建档的退货商品，一条一个 RM 编码，同一 SKU 可能有多条。CS 用它做两件事：建单前判断 SKU 有没有在万邑通注册（没注册万邑通会报「商品编码不存在」），以及取 length/width/height/weight 当箱规。⚠️ 分页拼写是 pageNo；registerXxx 那组字段实测全是 0，尺寸要用不带 register 前缀的。")
        .since("2026-10")
        .category("退货")
        .order(2)
        .cost("none")
        .tags("read")
        .params(
            object(
                "description",
                "查询退货商品档案（`winit.mms.item.return.list`）。\n\n分页拼写实测（2026-11-04，CYT_DTC 账号，共 529 条）：\n- 认顶层 `pageNo` / `pageSize`：`pageSize=100` 连取 6 页（第 6 页 29 条、第 7 页 0 条），\n  去重后正好 529 条 `code`，与回显 `totalCount` 对齐。\n- 写成 `pageNum` 会被**静默忽略**——照样返回第 1 页内容、回显仍是 `pageNo:1`，\n  不报错。出库单接口用 `pageNum`、这个用 `pageNo`，两套拼写别混。\n- 嵌套 `pageParams` 同样被忽略。\n- `pageSize` 上游不设上限（实测 10000 一次返回全量）；模型仍限 200，\n  全量靠翻页，避免一次拉爆网关响应。",
                "properties",
                object(
                    "pageNo",
                    object(
                        "default",
                        1,
                        "description",
                        "页码，从 1 开始（拼写是 pageNo，不是 pageNum）",
                        "minimum",
                        1,
                        "title",
                        "Pageno",
                        "type",
                        "integer"),
                    "pageSize",
                    object(
                        "default",
                        100,
                        "description",
                        "每页条数，最大 200",
                        "maximum",
                        200,
                        "minimum",
                        1,
                        "title",
                        "Pagesize",
                        "type",
                        "integer"),
                    "skuCode",
                    object(
                        "default",
                        "",
                        "description",
                        "按 SKU 过滤。⚠️ 上游是模糊匹配：查 XENNS-Mangird-Tea-Pro 会连 XENNS-Mangird-TEA-PRO-SE 一起返回，不能当精确校验用",
                        "maxLength",
                        100,
                        "title",
                        "Skucode",
                        "type",
                        "string"),
                    "code",
                    object(
                        "default",
                        "",
                        "description",
                        "按万邑通商品编码过滤（RM 开头那个 code）",
                        "maxLength",
                        40,
                        "title",
                        "Code",
                        "type",
                        "string"),
                    "isActive",
                    object(
                        "default",
                        "",
                        "description",
                        "只看有效/失效商品 Y/N",
                        "title",
                        "Isactive",
                        "type",
                        "string")),
                "title",
                "ReturnItems",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                "读取退货商品档案和箱尺来源。",
                "增加 SKU 唯一解析，为创建退货预检补齐资料。",
                "",
                "ready",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "映射官方条件并包装 pageParams/list 或 warehouseList。",
                "仍需要官方仓库、单号和日期字段，列表内容未形成统一业务实体或关联流程。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                        197,
                        "private Map<String, Object> buildData("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                        454,
                        "public Object invoke("))))
        .build();
  }

  private static BuiltinOperation returnsLabel() {
    return operation("winit.returns.label")
        .summary("查询 ReturnLabel 面单与跟踪号")
        .description(
            "官方 id=310。label 是 Base64 PDF；退货单已取消或非 ReturnLabel 单时返回空。一次最多 200 个 RT 号。面单可能较大，超时给到 60s。")
        .timeout(60)
        .since("2026-10")
        .category("退货")
        .order(5)
        .cost("none")
        .tags("read")
        .params(
            object(
                "description",
                "查询 ReturnLabel 面单（官方 id=310）。",
                "properties",
                object(
                    "returnGoodsOrderNos",
                    object(
                        "description",
                        "WINIT 退货单号列表（RT 开头），一次最多 200 个",
                        "items",
                        object("type", "string"),
                        "maxItems",
                        200,
                        "minItems",
                        1,
                        "title",
                        "Returngoodsordernos",
                        "type",
                        "array")),
                "required",
                array("returnGoodsOrderNos"),
                "title",
                "ReturnLabelQuery",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                "将 ReturnLabel 面单链接和跟踪号规范为业务结果。",
                "显示链接到期时间与面单准备状态，必要时支持轮询。",
                "暂无链接不一定代表创建失败。",
                "ready",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "整理面单 Base64、跟踪号和 has_label。",
                "无面单准备状态轮询或与创建流程自动衔接。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                        412,
                        "private static Map<String, Object> labelResult("))))
        .build();
  }

  private static BuiltinOperation returnsList() {
    return operation("winit.returns.list")
        .summary("查询退货订单")
        .description(
            "R3 退货到仓主数据源。含 RMA 退货 / 无 RMA 退货 / 出库派送失败三类。⚠️ 日期参数拼写是 OderStartDatetime（官方原拼写，不是 Order）。")
        .since("2026-10")
        .category("退货")
        .order(1)
        .cost("none")
        .tags("read")
        .params(
            object(
                "description",
                "查询退货订单（R3 退货到仓的主数据源）。",
                "properties",
                object(
                    "pageNo",
                    object(
                        "default",
                        1,
                        "description",
                        "页码，从 1 开始",
                        "minimum",
                        1,
                        "title",
                        "Pageno",
                        "type",
                        "integer"),
                    "pageSize",
                    object(
                        "default",
                        50,
                        "description",
                        "每页条数，最大 200",
                        "maximum",
                        200,
                        "minimum",
                        1,
                        "title",
                        "Pagesize",
                        "type",
                        "integer"),
                    "OderStartDatetime",
                    object(
                        "description",
                        "退货单创建时间起（yyyy-MM-dd），注意拼写是 Oder",
                        "title",
                        "Oderstartdatetime",
                        "type",
                        "string"),
                    "OderEndDatetime",
                    object(
                        "description",
                        "退货单创建时间止（yyyy-MM-dd）",
                        "title",
                        "Oderenddatetime",
                        "type",
                        "string"),
                    "returnGoodsOrderNo",
                    object(
                        "default",
                        "",
                        "description",
                        "WINIT 退货单号，如 RT16000013812852CN",
                        "title",
                        "Returngoodsorderno",
                        "type",
                        "string"),
                    "expressNo",
                    object(
                        "default",
                        "",
                        "description",
                        "退货运单号（仓库扫描的）",
                        "title",
                        "Expressno",
                        "type",
                        "string"),
                    "outboundOrderNo",
                    object(
                        "default",
                        "",
                        "description",
                        "原出库单号",
                        "title",
                        "Outboundorderno",
                        "type",
                        "string"),
                    "customerOrderNo",
                    object(
                        "default",
                        "",
                        "description",
                        "客户订单号",
                        "title",
                        "Customerorderno",
                        "type",
                        "string"),
                    "rmaNo",
                    object(
                        "default", "", "description", "RMA 号", "title", "Rmano", "type", "string"),
                    "warehouseCode",
                    object(
                        "default",
                        "",
                        "description",
                        "退货仓库，如 US0001",
                        "title",
                        "Warehousecode",
                        "type",
                        "string")),
                "required",
                array("OderStartDatetime", "OderEndDatetime"),
                "title",
                "ReturnList",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                "规范退货订单列表与分页。",
                "增加按业务退货单的状态视图和有界 collect。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "映射官方条件并包装 pageParams/list 或 warehouseList。",
                "仍需要官方仓库、单号和日期字段，列表内容未形成统一业务实体或关联流程。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                        197,
                        "private Map<String, Object> buildData("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                        454,
                        "public Object invoke("))))
        .build();
  }

  private static BuiltinOperation returnsPsc() {
    return operation("winit.returns.psc")
        .summary("查询退货渠道（PSC，可做 ReturnLabel 的那些）")
        .description("官方 id=282。返回里混有「内部测试PSC」，必须按 deliveryService=Y 过滤，不能默认取第一条。建退货单前用它选渠道。")
        .timeout(20)
        .since("2026-10")
        .category("退货")
        .order(4)
        .cost("none")
        .tags("read")
        .params(
            object(
                "description",
                "无入参接口（PSC 渠道列表用）。",
                "properties",
                object(),
                "title",
                "NoParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                "已整理支持 ReturnLabel 的退货渠道。",
                "结合国家、邮编、箱尺过滤可用渠道并解释不适用原因。",
                "",
                "ready",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "明确标记是否支持 ReturnLabel。",
                "没有根据国家、邮编、箱尺自动选出候选渠道并解释约束。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                        393,
                        "private static Map<String, Object> pscResult("))))
        .build();
  }

  private static BuiltinOperation returnsVoid() {
    return operation("winit.returns.void")
        .summary("取消退货订单")
        .description(
            "官方 id=152。只有「草稿 / 已下单」状态的有 RMA 退货单能取消，无 RMA 与派送失败退货单不支持。warehouseCode 只用于写白名单校验。")
        .quota("10/min")
        .sensitive(true)
        .idempotent(true)
        .timeout(45)
        .retries(0)
        .since("2026-10")
        .category("退货")
        .order(7)
        .cost("none")
        .tags("write", "danger")
        .params(
            object(
                "description",
                "取消退货订单（官方 id=152，`rma.returnGoodsOrder.voidOrder`）。",
                "properties",
                object(
                    "confirm",
                    object(
                        "default",
                        false,
                        "description",
                        "二次确认：必须显式传 true",
                        "title",
                        "Confirm",
                        "type",
                        "boolean"),
                    "returnGoodsOrderNos",
                    object(
                        "description",
                        "要取消的 RT 退货单号",
                        "items",
                        object("type", "string"),
                        "maxItems",
                        100,
                        "minItems",
                        1,
                        "title",
                        "Returngoodsordernos",
                        "type",
                        "array"),
                    "warehouseCode",
                    object(
                        "default",
                        "",
                        "description",
                        "该退货单的目的仓。万邑通撤单接口不吃这个字段，网关只用它做测试仓白名单校验",
                        "maxLength",
                        40,
                        "title",
                        "Warehousecode",
                        "type",
                        "string")),
                "required",
                array("returnGoodsOrderNos"),
                "title",
                "ReturnVoid",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                "按退货单号取消退货。",
                "增加当前状态预检，明确已经使用的面单能否取消。",
                "取消是否成功以供应商最终状态为准。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "把取消结果归一为每项 ok/reason。",
                "未读取订单状态或核对已使用面单。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                        431,
                        "private static Map<String, Object> voidResult("))))
        .build();
  }

  private static BuiltinOperation warehousesList() {
    return operation("winit.warehouses.list")
        .summary("查询验货仓 / 目的仓")
        .description("R3 三仓分流用。查目的仓时 warehouseType=DEST 且要给 inspectionWarehouseCode。")
        .since("2026-10")
        .category("仓库")
        .order(1)
        .cost("none")
        .tags("read")
        .params(
            object(
                "description",
                "查询验货仓 / 目的仓。",
                "properties",
                object(
                    "winitProductCode",
                    object(
                        "default",
                        "OW01011004124",
                        "description",
                        "Winit 产品编码，如 OW01011004124",
                        "title",
                        "Winitproductcode",
                        "type",
                        "string"),
                    "warehouseType",
                    object(
                        "default",
                        "INSJ",
                        "description",
                        "INSJ=验货仓，DEST=目的仓",
                        "enum",
                        array("INSJ", "DEST"),
                        "title",
                        "Warehousetype",
                        "type",
                        "string"),
                    "orderType",
                    object(
                        "default",
                        "SD",
                        "description",
                        "SD=标准海外仓入库，DW=直发海外验入库，DI=直发自验入库",
                        "enum",
                        array("SD", "DW", "DI"),
                        "title",
                        "Ordertype",
                        "type",
                        "string"),
                    "inspectionWarehouseCode",
                    object(
                        "default",
                        "",
                        "description",
                        "验货仓编码；warehouseType=DEST 时必填",
                        "title",
                        "Inspectionwarehousecode",
                        "type",
                        "string")),
                "title",
                "WarehouseList",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                "已整理验货仓/目的仓目录。",
                "加入国家、邮编、用途和启用状态供退货预检选择。",
                "",
                "ready",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "映射官方条件并包装 pageParams/list 或 warehouseList。",
                "仍需要官方仓库、单号和日期字段，列表内容未形成统一业务实体或关联流程。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                        197,
                        "private Map<String, Object> buildData("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/winit/WinitAdapter.java",
                        454,
                        "public Object invoke("))))
        .build();
  }
}
