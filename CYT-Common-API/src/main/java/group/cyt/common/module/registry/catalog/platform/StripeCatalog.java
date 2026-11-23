package group.cyt.common.module.registry.catalog.platform;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import group.cyt.common.module.registry.catalog.*;
import java.util.List;

/** Built-in stripe contracts. Edit metadata, parameters and review together here. */
public final class StripeCatalog implements PlatformCatalog {
  @Override
  public void register(CatalogBuilder catalog) {
    catalog.platform("stripe", "Stripe", "60/min");
    catalog.credentials(credentials());
    catalog.operation(disputesList());
    catalog.operation(disputesCollect());
    catalog.operation(PaymentDisputeCatalog.feed("stripe", false));
    catalog.operation(PaymentDisputeCatalog.feed("stripe", true));
    catalog.operation(PaymentDisputeCatalog.orderReference());
  }

  private static PlatformCredentials credentials() {
    return new PlatformCredentials(
        "stripe",
        "Stripe 争议（只读）",
        "https://docs.stripe.com/api/disputes/list",
        "只读归集，不提供退款、回复或其他业务写入。多账号均需独立配置。Trustpilot API_KEY 与 ACCESS_TOKEN 至少配置一个。PayPal ENV 默认 sandbox。",
        "inherit",
        false,
        false,
        "STRIPE_ACCOUNTS",
        "",
        "",
        List.of(
            new PlatformCredentials.Variable(
                "STRIPE_{ACCOUNT}_API_KEY", "API_KEY", true, true, "", "", false, "", true),
            new PlatformCredentials.Variable(
                "STRIPE_{ACCOUNT}_WEBHOOK_SECRET", "WEBHOOK_SECRET", false, true, "", "", false, "", true)));
  }

  private static BuiltinOperation disputesList() {
    return operation("stripe.disputes.list")
        .summary("读取 Stripe 支付争议")
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
                "已整理争议状态、证据和来源单号。",
                "使用 collect 汇总；截止日期和可提交动作应以供应商状态为准。",
                "不自动提交证据或接受争议。",
                "recommended",
                List.of("stripe.disputes.collect"),
                CapabilityReview.Depth.ENHANCED,
                "统一争议金额单位、币种、客户、订单和截止日期。",
                "订单号来自已有 metadata，没有自动关联交易/订单和生成取证缺项。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/source/CustomerSourcesAdapter.java",
                        248,
                        "private Map<String, Object> stripeDisputes("))))
        .build();
  }

  private static BuiltinOperation disputesCollect() {
    return operation("stripe.disputes.collect")
        .summary("自动分页汇总：读取 Stripe 支付争议")
        .description(
            "自动分页、统一 items/page/errors；支持 max_items、max_pages 与签名续传游标。仅扫描根记录，嵌套资源遵循原接口的范围。读取能力独立授权，保留原接口兼容。")
        .quota("30/min")
        .timeout(90)
        .category("业务聚合")
        .order(-50)
        .sourceOperation("stripe.disputes.list")
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
                CapabilityReview.Depth.ENHANCED,
                "在固定只读能力上增加上限、分页、去重、统一返回和签名续传。",
                "未联查支付订单或生成取证计划。",
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
