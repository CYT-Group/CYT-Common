package group.cyt.common.module.registry.catalog.platform;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import group.cyt.common.module.registry.catalog.*;
import java.util.List;

/** Built-in gmail contracts. Edit metadata, parameters and review together here. */
public final class GmailCatalog implements PlatformCatalog {
  @Override
  public void register(CatalogBuilder catalog) {
    catalog.platform("gmail", "Gmail", "600/min");
    catalog.credentials(credentials());
    catalog.operation(labelsList());
    catalog.operation(messagesGet());
    catalog.operation(messagesList());
    catalog.operation(messagesRestoreFromSpam());
    catalog.operation(messagesSend());
    catalog.operation(profileGet());
    catalog.operation(messagesSearch());
  }

  private static PlatformCredentials credentials() {
    return new PlatformCredentials(
        "gmail",
        "Gmail",
        "",
        "与 Google Drive 共用同一套 OAuth 客户端，refresh_token 各自一份",
        "inherit",
        true,
        false,
        "",
        "",
        "",
        List.of(
            new PlatformCredentials.Variable(
                "GOOGLE_CLIENT_ID",
                "OAuth Client ID",
                true,
                false,
                "GCP → API 和服务 → 凭据",
                "",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "GOOGLE_CLIENT_SECRET",
                "OAuth Client Secret",
                true,
                true,
                "",
                "",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "GMAIL_REFRESH_TOKEN",
                "Gmail Refresh Token",
                true,
                true,
                "scope 需含 gmail.modify / gmail.send",
                "",
                true,
                "GMAIL_{SYSTEM}_REFRESH_TOKEN",
                false)));
  }

  private static BuiltinOperation labelsList() {
    return operation("gmail.labels.list")
        .summary("列出标签")
        .quota("120/min")
        .retries(3)
        .since("2026-10")
        .category("标签")
        .order(10)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "授权邮箱地址；非空时必须与实际身份一致",
                        "maxLength",
                        254,
                        "title",
                        "Account",
                        "type",
                        "string")),
                "title",
                "LabelsParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                "读取当前授权邮箱的标签。",
                "可为业务查询建立标签名到 ID 的唯一映射，并展示系统标签。",
                "",
                "ready",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "读取标签、单封原始邮件或 ID 列表，保留官方格式与 token。",
                "业务方仍要解析标签/邮件结构或逐封补齐；search 提供了更高一层能力。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                        46,
                        "private Object gmail("))))
        .build();
  }

  private static BuiltinOperation messagesGet() {
    return operation("gmail.messages.get")
        .summary("读取单封邮件")
        .quota("600/min")
        .retries(3)
        .since("2026-10")
        .category("消息")
        .order(20)
        .cost("none")
        .tags("read", "hot")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "授权邮箱地址；非空时必须与实际身份一致",
                        "maxLength",
                        254,
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "message_id",
                    object(
                        "maxLength", 128, "minLength", 4, "title", "Message Id", "type", "string"),
                    "format",
                    object(
                        "default",
                        "metadata",
                        "description",
                        "响应格式",
                        "enum",
                        array("minimal", "full", "raw", "metadata"),
                        "title",
                        "Format",
                        "type",
                        "string"),
                    "metadata_headers",
                    object(
                        "description",
                        "format=metadata 时只返回指定 header，例如 From、To、Subject",
                        "items",
                        object("type", "string"),
                        "maxItems",
                        100,
                        "title",
                        "Metadata Headers",
                        "type",
                        "array",
                        "default",
                        array())),
                "required",
                array("message_id"),
                "title",
                "GetMessageParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                "可选 metadata/full/raw 格式，读取单封邮件。",
                "新增 search 已自动读取详情并整理主题、发件人、MIME 正文和附件元信息。",
                "附件内容仍需独立下载能力；HTML 正文需要下游安全渲染。",
                "recommended",
                List.of("gmail.messages.search"),
                CapabilityReview.Depth.BASIC,
                "读取标签、单封原始邮件或 ID 列表，保留官方格式与 token。",
                "业务方仍要解析标签/邮件结构或逐封补齐；search 提供了更高一层能力。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                        46,
                        "private Object gmail("))))
        .build();
  }

  private static BuiltinOperation messagesList() {
    return operation("gmail.messages.list")
        .summary("按条件列出邮件")
        .quota("600/min")
        .retries(3)
        .since("2026-10")
        .category("消息")
        .order(10)
        .cost("none")
        .tags("read", "hot")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "授权邮箱地址；非空时必须与实际身份一致",
                        "maxLength",
                        254,
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "query",
                    object(
                        "default",
                        "",
                        "description",
                        "Gmail 搜索语法，如 from:x@y.com is:unread",
                        "maxLength",
                        500,
                        "title",
                        "Query",
                        "type",
                        "string"),
                    "max_results",
                    object(
                        "default",
                        20,
                        "description",
                        "最多 500；Gmail 官方默认 100",
                        "maximum",
                        500,
                        "minimum",
                        1,
                        "title",
                        "Max Results",
                        "type",
                        "integer"),
                    "page_token",
                    object(
                        "anyOf",
                        array(object("maxLength", 512, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "上一页返回的 nextPageToken",
                        "title",
                        "Page Token"),
                    "label_ids",
                    object(
                        "description",
                        "只返回包含所有指定标签的邮件",
                        "items",
                        object("type", "string"),
                        "maxItems",
                        20,
                        "title",
                        "Label Ids",
                        "type",
                        "array",
                        "default",
                        array()),
                    "include_spam_trash",
                    object(
                        "default",
                        false,
                        "description",
                        "是否把垃圾邮件和垃圾箱邮件也纳入搜索",
                        "title",
                        "Include Spam Trash",
                        "type",
                        "boolean")),
                "title",
                "ListMessagesParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                "保留供应商筛选和分页参数，适合作为单页原子接口。",
                "优先使用新增业务接口：有界自动分页、统一结果与签名续传游标。",
                "原接口继续兼容；items 只代表当前页。",
                "recommended",
                List.of("gmail.messages.search"),
                CapabilityReview.Depth.BASIC,
                "读取标签、单封原始邮件或 ID 列表，保留官方格式与 token。",
                "业务方仍要解析标签/邮件结构或逐封补齐；search 提供了更高一层能力。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                        46,
                        "private Object gmail("))))
        .build();
  }

  private static BuiltinOperation messagesRestoreFromSpam() {
    return operation("gmail.messages.restore_from_spam")
        .summary("测试邮箱垃圾邮件移回收件箱")
        .description("仅显式测试邮箱；校验实际身份和最新 SPAM 标签，仅移除 SPAM、添加 INBOX。")
        .sensitive(true)
        .idempotent(true)
        .since("2026-10")
        .category("消息")
        .order(40)
        .cost("none")
        .tags("write")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "account",
                    object("maxLength", 254, "minLength", 3, "title", "Account", "type", "string"),
                    "message_id",
                    object(
                        "maxLength",
                        128,
                        "minLength",
                        4,
                        "pattern",
                        "^[A-Za-z0-9_-]+$",
                        "title",
                        "Message Id",
                        "type",
                        "string")),
                "required",
                array("account", "message_id"),
                "title",
                "RestoreSpamParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                "核实测试邮箱身份，且邮件必须在 SPAM、不在 TRASH。",
                "保留严格目标校验，可增加操作前后标签差异。",
                "仅测试白名单邮箱可写。",
                "ready",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "先验证邮箱和 SPAM/TRASH 状态，再移除垃圾标签并移回收件箱。",
                "仅适用于明确配置的测试邮箱；不等于通用邮件标签工作流。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                        84,
                        "case \"gmail.messages.restore_from_spam\""))))
        .build();
  }

  private static BuiltinOperation messagesSend() {
    return operation("gmail.messages.send")
        .summary("发送邮件（不可撤回）")
        .description("直接以授权账号发出。属于高危操作，需单独授权。")
        .sensitive(true)
        .since("2026-10")
        .category("消息")
        .order(30)
        .cost("none")
        .tags("write", "danger")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "授权邮箱地址；非空时必须与实际身份一致",
                        "maxLength",
                        254,
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "to",
                    object(
                        "description",
                        "收件人；只有一个时可直接写 \"a@b.com\"",
                        "items",
                        object("type", "string"),
                        "maxItems",
                        50,
                        "minItems",
                        1,
                        "title",
                        "To",
                        "type",
                        "array"),
                    "subject",
                    object("maxLength", 200, "minLength", 1, "title", "Subject", "type", "string"),
                    "body_text",
                    object(
                        "description",
                        "纯文本正文",
                        "maxLength",
                        100000,
                        "minLength",
                        1,
                        "title",
                        "Body Text",
                        "type",
                        "string"),
                    "body_html",
                    object(
                        "anyOf",
                        array(
                            object("maxLength", 200000, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "HTML 正文；给了就优先于 body_text",
                        "title",
                        "Body Html"),
                    "cc",
                    object(
                        "description",
                        "抄送",
                        "items",
                        object("type", "string"),
                        "title",
                        "Cc",
                        "type",
                        "array",
                        "default",
                        array()),
                    "bcc",
                    object(
                        "description",
                        "密送",
                        "items",
                        object("type", "string"),
                        "title",
                        "Bcc",
                        "type",
                        "array",
                        "default",
                        array()),
                    "thread_id",
                    object(
                        "anyOf",
                        array(object("maxLength", 128, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "回复到已有会话时填",
                        "title",
                        "Thread Id")),
                "required",
                array("to", "subject", "body_text"),
                "title",
                "SendMessageParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                "已组装 UTF-8 MIME，验证邮箱及全部收件人白名单。",
                "补草稿预检、模板变量检查和稳定业务发送编号。",
                "目前仅测试白名单可写；发送超时不代表未发送。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "从业务文本构建 UTF-8 MIME，核对邮箱和全部收件人。",
                "没有消息模板预检、强制业务防重和发送结果追踪；当前受测试白名单约束。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                        96,
                        "case \"gmail.messages.send\""))))
        .build();
  }

  private static BuiltinOperation profileGet() {
    return operation("gmail.profile.get")
        .summary("读取实际授权邮箱身份")
        .quota("120/min")
        .retries(3)
        .since("2026-10")
        .category("账号")
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "授权邮箱地址；非空时必须与实际身份一致",
                        "maxLength",
                        254,
                        "title",
                        "Account",
                        "type",
                        "string")),
                "title",
                "LabelsParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                "返回实际 OAuth 授权邮箱，并验证 account 匹配。",
                "适合作为接入后的身份核对步骤。",
                "account 参数不能切换到未授权邮箱。",
                "ready",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "核对实际授权邮箱与 account 参数，返回授权身份。",
                "这是身份检查，不能算完整邮件业务流程。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                        52,
                        "var profile = http.object("))))
        .build();
  }

  private static BuiltinOperation messagesSearch() {
    return operation("gmail.messages.search")
        .summary("按业务条件搜索邮件并整理内容")
        .description(
            "自动分页、统一 items/page/errors；支持 max_items、max_pages 与签名续传游标。仅扫描根记录，嵌套资源遵循原接口的范围。读取能力独立授权，保留原接口兼容。")
        .quota("600/min")
        .timeout(90)
        .category("业务聚合")
        .order(-50)
        .sourceOperation("gmail.messages.list")
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
                    object(
                        "default",
                        "",
                        "description",
                        "授权邮箱地址；非空时必须与实际身份一致",
                        "maxLength",
                        254,
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "query",
                    object(
                        "default",
                        "",
                        "description",
                        "Gmail 搜索语法，如 from:x@y.com is:unread",
                        "maxLength",
                        500,
                        "title",
                        "Query",
                        "type",
                        "string"),
                    "label_ids",
                    object(
                        "description",
                        "只返回包含所有指定标签的邮件",
                        "items",
                        object("type", "string"),
                        "maxItems",
                        20,
                        "title",
                        "Label Ids",
                        "type",
                        "array",
                        "default",
                        array()),
                    "include_spam_trash",
                    object(
                        "default",
                        false,
                        "description",
                        "是否把垃圾邮件和垃圾箱邮件也纳入搜索",
                        "title",
                        "Include Spam Trash",
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
                        "回传 page.next_cursor；绑定调用系统和筛选条件"),
                    "sender",
                    object(
                        "type", "string", "minLength", 1, "maxLength", 200, "description", "发件人"),
                    "subject",
                    object(
                        "type", "string", "minLength", 1, "maxLength", 200, "description", "主题关键词"),
                    "since",
                    object(),
                    "before",
                    object(),
                    "unread",
                    object("type", "boolean", "default", false),
                    "has_attachment",
                    object("type", "boolean", "default", false),
                    "include_details",
                    object("type", "boolean", "default", true),
                    "include_body",
                    object("type", "boolean", "default", false)),
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
                "组合业务筛选、自动分页、逐封详情补齐和 MIME 正文/附件元信息解析。",
                "不自动下载附件或关联订单，单封错误仍须业务方处理。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                        361,
                        "public static String gmailQuery("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                        263,
                        "var enriched = new ArrayList<Object>();"),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                        409,
                        "private static Map<String, Object> mail("))))
        .build();
  }
}
