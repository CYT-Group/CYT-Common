package group.cyt.common.module.registry.catalog.platform;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import group.cyt.common.module.registry.catalog.*;
import java.util.List;

/** Built-in judgeme contracts. Edit metadata, parameters and review together here. */
public final class JudgemeCatalog implements PlatformCatalog {
  @Override
  public void register(CatalogBuilder catalog) {
    catalog.platform("judgeme", "Judge.me", "60/min");
    catalog.credentials(credentials());
    catalog.operation(reviewsList());
    catalog.operation(repliesList());
    catalog.operation(reviewsReply());
    catalog.operation(reviewsCollect());
  }

  private static PlatformCredentials credentials() {
    return new PlatformCredentials(
        "judgeme",
        "Judge.me 评价与回复",
        "https://judge.me/help/en/articles/8409180-using-judge-me-api",
        "支持读取评价及确认后公开回复。需店铺 Private API Token，多账号独立配置。默认不发送回复通知邮件。",
        "inherit",
        false,
        false,
        "JUDGEME_ACCOUNTS",
        "",
        "",
        List.of(
            new PlatformCredentials.Variable(
                "JUDGEME_{ACCOUNT}_SHOP_DOMAIN",
                "SHOP_DOMAIN",
                true,
                false,
                "",
                "",
                false,
                "",
                true),
            new PlatformCredentials.Variable(
                "JUDGEME_{ACCOUNT}_API_TOKEN", "API_TOKEN", true, true, "", "", false, "", true)));
  }

  private static BuiltinOperation reviewsList() {
    return operation("judgeme.reviews.list")
        .summary("读取 Judge.me 评价")
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
                "将评价归一为客服数据结构，保留来源与游标。",
                "使用 collect 自动分页并显示部分失败；跨来源分析仍需统一评分尺度。",
                "",
                "recommended",
                List.of("judgeme.reviews.collect"),
                CapabilityReview.Depth.ENHANCED,
                "评价归一为评分、内容、客户、SKU 和日期。",
                "仍是单来源单页；无订单关联补齐、评价回复或问题归因。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/source/CustomerSourcesAdapter.java",
                        152,
                        "private Map<String, Object> judgemeReviews("))))
        .build();
  }

  private static BuiltinOperation repliesList() {
    return operation("judgeme.replies.list")
        .summary("读取 Judge.me 公开回复与状态")
        .description("官方公开评价组件 JSON 分页。公开 UUID 与管理 API 评论 ID 不同；不提供回复时间，未返回的评价不能判为未回复。")
        .quota("30/min")
        .timeout(60)
        .since("2026-10")
        .category("评价回复")
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
                "读取官方公开组件的回复正文，区分明确空值和缺字段，分页覆盖商品与店铺评价。",
                "调用方按账号和评价原文唯一关联私有 ID，保存检查点并处理平台缓存延迟。",
                "只覆盖公开评价的公开回复；UUID 不等于私有评价 ID，平台不返回回复时间。",
                "ready",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "读取并验证公开回复分页，规范 UUID、商品标识和明确回复状态，遍历商品及店铺评价。",
                "仅含公开评价的公开回复，无回复发生时间；私有 ID 关联和缓存核对由调用方完成。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/source/CustomerSourcesAdapter.java",
                        159,
                        "private Map<String, Object> judgemePublicReplies("))))
        .build();
  }

  private static BuiltinOperation reviewsReply() {
    return operation("judgeme.reviews.reply")
        .summary("确认后公开回复 Judge.me 评价")
        .description(
            "按 account 解析店铺私有 Token，使用 Judge.me 评论 ID 公开回复。confirm=true 才发布；默认不发送通知邮件。使用稳定幂等键；超时后先核对平台，禁止自动重发。评论列表不返回回复，调用方应在明确成功后保存已回复状态。")
        .quota("30/min")
        .sensitive(true)
        .idempotent(true)
        .since("2026-10")
        .category("评价回复")
        .cost("none")
        .tags("write")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "account",
                    object("default", "", "maxLength", 254, "title", "Account", "type", "string"),
                    "review_id",
                    object(
                        "description",
                        "Judge.me 评论 ID，不能使用中台主键或商品 ID",
                        "exclusiveMinimum",
                        0,
                        "title",
                        "Review Id",
                        "type",
                        "integer"),
                    "body",
                    object("maxLength", 10000, "minLength", 1, "title", "Body", "type", "string"),
                    "send_reply_email",
                    object(
                        "default",
                        false,
                        "description",
                        "是否同时邮件通知评论人，默认关闭",
                        "title",
                        "Send Reply Email",
                        "type",
                        "boolean"),
                    "confirm",
                    object(
                        "default",
                        false,
                        "description",
                        "公开发布回复必须明确传 true",
                        "title",
                        "Confirm",
                        "type",
                        "boolean")),
                "required",
                array("review_id", "body"),
                "title",
                "JudgeMeReply",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/source/CustomerSourcesAdapter.java",
                "按账号、Judge.me 评论 ID 和确认参数公开回复，默认不发通知邮件。",
                "调用方审批通过后执行，明确成功再保存已回复；超时后人工核对。",
                "公开回复可用 judgeme.replies.list 回读；不提供平台回复编辑和删除写操作。",
                "ready",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "按账号解析店铺凭据，确认后公开发布回复，规范成功结果并禁止自动重试。",
                "公开回复读取是独立操作；审批和不确定结果核对由调用方完成。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/source/CustomerSourcesAdapter.java",
                        158,
                        "private Map<String, Object> judgemeReply("))))
        .build();
  }

  private static BuiltinOperation reviewsCollect() {
    return operation("judgeme.reviews.collect")
        .summary("自动分页汇总：读取 Judge.me 评价")
        .description(
            "自动分页、统一 items/page/errors；支持 max_items、max_pages 与签名续传游标。仅扫描根记录，嵌套资源遵循原接口的范围。读取能力独立授权，保留原接口兼容。")
        .quota("30/min")
        .timeout(90)
        .category("业务聚合")
        .order(-50)
        .sourceOperation("judgeme.reviews.list")
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
                "没有评价到订单/SKU 的进一步关联和回复流程。",
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
