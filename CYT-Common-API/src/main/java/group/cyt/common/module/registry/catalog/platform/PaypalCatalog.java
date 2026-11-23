package group.cyt.common.module.registry.catalog.platform;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import group.cyt.common.module.registry.catalog.*;
import java.util.List;

/** Built-in paypal contracts. Edit metadata, parameters and review together here. */
public final class PaypalCatalog implements PlatformCatalog {
  @Override
  public void register(CatalogBuilder catalog) {
    catalog.platform("paypal", "PayPal", "60/min");
    catalog.credentials(credentials());
    catalog.operation(disputesList());
    catalog.operation(disputesCollect());
    catalog.operation(PaymentDisputeCatalog.feed("paypal", false));
    catalog.operation(PaymentDisputeCatalog.feed("paypal", true));
  }

  private static PlatformCredentials credentials() {
    return new PlatformCredentials(
        "paypal",
        "PayPal 争议（只读）",
        "https://developer.paypal.com/api/customer-disputes/v1/disputes-list",
        "只读归集，不提供退款、回复或其他业务写入。多账号均需独立配置。Trustpilot API_KEY 与 ACCESS_TOKEN 至少配置一个。PayPal ENV 默认 sandbox。",
        "inherit",
        false,
        false,
        "PAYPAL_ACCOUNTS",
        "",
        "",
        List.of(
            new PlatformCredentials.Variable(
                "PAYPAL_{ACCOUNT}_CLIENT_ID", "CLIENT_ID", true, true, "", "", false, "", true),
            new PlatformCredentials.Variable(
                "PAYPAL_{ACCOUNT}_CLIENT_SECRET",
                "CLIENT_SECRET",
                true,
                true,
                "",
                "",
                false,
                "",
                true),
            new PlatformCredentials.Variable(
                "PAYPAL_{ACCOUNT}_ENV", "ENV", false, false, "", "", false, "", true),
            new PlatformCredentials.Variable(
                "PAYPAL_{ACCOUNT}_WEBHOOK_ID", "WEBHOOK_ID", false, true, "", "", false, "", true)));
  }

  private static BuiltinOperation disputesList() {
    return operation("paypal.disputes.list")
        .summary("读取 PayPal 争议及取证信息")
        .description("只读拉取、账号独立路由、标准分页；缺授权明确失败，不返回模拟数据。")
        .quota("30/min")
        .timeout(120)
        .since("2026-10")
        .category("只读归集")
        .cost("none")
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object("default", "", "maxLength", 254, "title", "Account", "type", "string"),
                    "cursor",
                    object(
                        "additionalProperties",
                        true,
                        "title",
                        "Cursor",
                        "type",
                        "object",
                        "default",
                        object()),
                    "limit",
                    object(
                        "default", 100, "maximum", 100, "minimum", 1, "title", "Limit", "type",
                        "integer")),
                "title",
                "Page",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/source/CustomerSourcesAdapter.java",
                "读取争议并补齐取证详情，归一客服字段。",
                "使用 collect 汇总；进一步可生成取证缺项清单。",
                "详情补齐存在额外调用成本；当前来源详情失败会使该页报错，后续可深化为逐条失败保留。",
                "recommended",
                List.of("paypal.disputes.collect"),
                CapabilityReview.Depth.DEEP,
                "自动从列表逐项补齐争议详情，整理买家、订单、金额和响应期限。",
                "任一详情失败仍会使当页失败；多笔争议交易只选首笔，不是完整取证工作流。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/source/CustomerSourcesAdapter.java",
                        298,
                        "private Map<String, Object> paypalDisputes("))))
        .build();
  }

  private static BuiltinOperation disputesCollect() {
    return operation("paypal.disputes.collect")
        .summary("自动分页汇总：读取 PayPal 争议及取证信息")
        .description(
            "自动分页、统一 items/page/errors；支持 max_items、max_pages 与签名续传游标。仅扫描根记录，嵌套资源遵循原接口的范围。读取能力独立授权，保留原接口兼容。")
        .quota("30/min")
        .timeout(90)
        .category("业务聚合")
        .order(-50)
        .sourceOperation("paypal.disputes.list")
        .tags("read", "business", "collection")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "account",
                    object("default", "", "maxLength", 254, "title", "Account", "type", "string"),
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
                CapabilityReview.Depth.DEEP,
                "在逐笔详情补齐和争议业务字段整理之上增加有界分页。",
                "继承来源单条失败导致当页失败的限制，也未实现取证准备或争议处置。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                        101,
                        "public Object invoke("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/source/CustomerSourcesAdapter.java",
                        298,
                        "private Map<String, Object> paypalDisputes("))))
        .build();
  }
}
