package group.cyt.common.module.registry.catalog.platform;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import group.cyt.common.module.registry.catalog.*;
import java.util.List;

/** Built-in trustpilot contracts. Edit metadata, parameters and review together here. */
public final class TrustpilotCatalog implements PlatformCatalog {
  @Override
  public void register(CatalogBuilder catalog) {
    catalog.platform("trustpilot", "Trustpilot", "60/min");
    catalog.credentials(credentials());
    catalog.operation(reviewsList());
    catalog.operation(reviewsCollect());
  }

  private static PlatformCredentials credentials() {
    return new PlatformCredentials(
        "trustpilot",
        "Trustpilot 评价（只读）",
        "https://developers.trustpilot.com/business-units-api",
        "只读归集，不提供退款、回复或其他业务写入。多账号均需独立配置。Trustpilot API_KEY 与 ACCESS_TOKEN 至少配置一个。PayPal ENV 默认 sandbox。",
        "inherit",
        false,
        false,
        "TRUSTPILOT_ACCOUNTS",
        "",
        "",
        List.of(
            new PlatformCredentials.Variable(
                "TRUSTPILOT_{ACCOUNT}_BUSINESS_UNIT_ID",
                "BUSINESS_UNIT_ID",
                true,
                false,
                "",
                "",
                false,
                "",
                true),
            new PlatformCredentials.Variable(
                "TRUSTPILOT_{ACCOUNT}_API_KEY", "API_KEY", false, true, "", "", false, "", true),
            new PlatformCredentials.Variable(
                "TRUSTPILOT_{ACCOUNT}_ACCESS_TOKEN",
                "ACCESS_TOKEN",
                false,
                true,
                "",
                "",
                false,
                "",
                true)));
  }

  private static BuiltinOperation reviewsList() {
    return operation("trustpilot.reviews.list")
        .summary("读取 Trustpilot 评价")
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
                "将评价与业务来源关联，维护分页游标。",
                "使用 collect 自动分页；后续可添加回复状态与低分原因分类。",
                "",
                "recommended",
                List.of("trustpilot.reviews.collect"),
                CapabilityReview.Depth.ENHANCED,
                "区分公开/私有读取并整理评价、客户与订单字段。",
                "无跨源订单关联和回复流程，字段可见性取决于凭据。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/source/CustomerSourcesAdapter.java",
                        196,
                        "private Map<String, Object> trustpilotReviews("))))
        .build();
  }

  private static BuiltinOperation reviewsCollect() {
    return operation("trustpilot.reviews.collect")
        .summary("自动分页汇总：读取 Trustpilot 评价")
        .description(
            "自动分页、统一 items/page/errors；支持 max_items、max_pages 与签名续传游标。仅扫描根记录，嵌套资源遵循原接口的范围。读取能力独立授权，保留原接口兼容。")
        .quota("30/min")
        .timeout(90)
        .category("业务聚合")
        .order(-50)
        .sourceOperation("trustpilot.reviews.list")
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
                "没有跨来源评价语义和订单补齐。",
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
