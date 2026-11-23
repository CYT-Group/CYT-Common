package group.cyt.common.module.registry.catalog.platform;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import group.cyt.common.module.registry.catalog.*;
import java.util.List;

/** Built-in docusign contracts. Edit metadata, parameters and review together here. */
public final class DocusignCatalog implements PlatformCatalog {
  @Override
  public void register(CatalogBuilder catalog) {
    catalog.platform("docusign", "DocuSign 电子签", null);
    catalog.credentials(credentials());
    catalog.alias("docusign.envelope.create", "docusign.envelopes.create");
    catalog.alias("docusign.envelope.get", "docusign.envelopes.get");
    catalog.alias("docusign.envelope.list", "docusign.envelopes.list");
    catalog.alias("docusign.envelope.void", "docusign.envelopes.void");
    catalog.alias("docusign.template.list", "docusign.templates.list");
    catalog.alias("docusign.mock.advance", "docusign.testing.advance");
    catalog.operation(envelopesCreate());
    catalog.operation(envelopesGet());
    catalog.operation(envelopesList());
    catalog.operation(envelopesVoid());
    catalog.operation(templatesList());
    catalog.operation(testingAdvance());
    catalog.operation(templatesGet());
    catalog.operation(envelopesCollect());
    catalog.operation(templatesCollect());
  }

  private static PlatformCredentials credentials() {
    return new PlatformCredentials(
        "docusign",
        "DocuSign 电子签",
        "https://developers.docusign.com/platform/auth/jwt/",
        "JWT Grant 需要 RSA 私钥；私钥里换行用 \\n 表示",
        "inherit",
        true,
        false,
        "",
        "",
        "",
        List.of(
            new PlatformCredentials.Variable(
                "DOCUSIGN_INTEGRATION_KEY",
                "Integration Key",
                true,
                true,
                "JWT 应用的 GUID",
                "",
                true,
                "DOCUSIGN_{SYSTEM}_INTEGRATION_KEY",
                false),
            new PlatformCredentials.Variable(
                "DOCUSIGN_USER_ID",
                "User ID",
                true,
                false,
                "被授权发起调用的 user_id",
                "",
                true,
                "DOCUSIGN_{SYSTEM}_USER_ID",
                false),
            new PlatformCredentials.Variable(
                "DOCUSIGN_ACCOUNT_ID",
                "Account ID",
                true,
                false,
                "API Account ID",
                "",
                true,
                "DOCUSIGN_{SYSTEM}_ACCOUNT_ID",
                false),
            new PlatformCredentials.Variable(
                "DOCUSIGN_PRIVATE_KEY",
                "RSA 私钥",
                true,
                true,
                "整段 PEM，换行写成 \\n",
                "",
                true,
                "DOCUSIGN_{SYSTEM}_PRIVATE_KEY",
                false),
            new PlatformCredentials.Variable(
                "DOCUSIGN_HMAC_KEY",
                "Connect HMAC Key",
                false,
                true,
                "回调验签用，不接回调可不填",
                "",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "DOCUSIGN_ENV",
                "环境",
                false,
                false,
                "demo / prod，默认 demo",
                "demo",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "DOCUSIGN_BASE_URI",
                "REST Base URI",
                false,
                false,
                "一般留空自动推导",
                "",
                false,
                "",
                false)));
  }

  private static BuiltinOperation envelopesCreate() {
    return operation("docusign.envelopes.create")
        .summary("创建并发送信封（模板或直接传文件）")
        .description(
            "用 template_id + template_roles，或 documents + recipients。status=sent 会立即发信给签署人。")
        .quota("30/min")
        .idempotent(true)
        .since("2026-10")
        .category("信封")
        .order(10)
        .cost("none")
        .tags("write", "money")
        .params(
            object(
                "$defs",
                object(
                    "InlineDocument",
                    object(
                        "additionalProperties",
                        false,
                        "properties",
                        object(
                            "name",
                            object(
                                "description",
                                "文件名，如 contract.pdf",
                                "title",
                                "Name",
                                "type",
                                "string"),
                            "document_base64",
                            object(
                                "description",
                                "文件内容的 base64",
                                "title",
                                "Document Base64",
                                "type",
                                "string"),
                            "file_extension",
                            object(
                                "default",
                                "pdf",
                                "description",
                                "扩展名",
                                "title",
                                "File Extension",
                                "type",
                                "string")),
                        "required",
                        array("name", "document_base64"),
                        "title",
                        "InlineDocument",
                        "type",
                        "object"),
                    "Recipient",
                    object(
                        "additionalProperties",
                        false,
                        "properties",
                        object(
                            "email",
                            object("description", "签署人邮箱", "title", "Email", "type", "string"),
                            "name",
                            object("description", "签署人姓名", "title", "Name", "type", "string"),
                            "recipient_id",
                            object(
                                "default",
                                "1",
                                "description",
                                "收件人编号，从 1 开始",
                                "title",
                                "Recipient Id",
                                "type",
                                "string"),
                            "routing_order",
                            object(
                                "default",
                                1,
                                "maximum",
                                99,
                                "minimum",
                                1,
                                "title",
                                "Routing Order",
                                "type",
                                "integer")),
                        "required",
                        array("email", "name"),
                        "title",
                        "Recipient",
                        "type",
                        "object"),
                    "TemplateRole",
                    object(
                        "additionalProperties",
                        false,
                        "properties",
                        object(
                            "role_name",
                            object(
                                "description",
                                "模板里的角色名，如 Signer 1",
                                "title",
                                "Role Name",
                                "type",
                                "string"),
                            "email",
                            object("description", "签署人邮箱", "title", "Email", "type", "string"),
                            "name",
                            object("description", "签署人姓名", "title", "Name", "type", "string"),
                            "routing_order",
                            object(
                                "default",
                                1,
                                "description",
                                "签署顺序",
                                "maximum",
                                99,
                                "minimum",
                                1,
                                "title",
                                "Routing Order",
                                "type",
                                "integer"),
                            "tabs",
                            object(
                                "type",
                                "object",
                                "additionalProperties",
                                true,
                                "description",
                                "模板预填字段；先通过 templates.get 核对角色和标签")),
                        "required",
                        array("role_name", "email", "name"),
                        "title",
                        "TemplateRole",
                        "type",
                        "object")),
                "additionalProperties",
                false,
                "properties",
                object(
                    "template_id",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "用模板创建时填模板 ID",
                        "title",
                        "Template Id"),
                    "template_roles",
                    object(
                        "anyOf",
                        array(
                            object(
                                "items", object("$ref", "#/$defs/TemplateRole"), "type", "array"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "模板角色与签署人",
                        "title",
                        "Template Roles"),
                    "documents",
                    object(
                        "anyOf",
                        array(
                            object(
                                "items", object("$ref", "#/$defs/InlineDocument"), "type", "array"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "不用模板时直接传文件",
                        "title",
                        "Documents"),
                    "recipients",
                    object(
                        "anyOf",
                        array(
                            object("items", object("$ref", "#/$defs/Recipient"), "type", "array"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "不用模板时的签署人",
                        "title",
                        "Recipients"),
                    "email_subject",
                    object(
                        "default",
                        "请签署合同",
                        "maxLength",
                        200,
                        "title",
                        "Email Subject",
                        "type",
                        "string"),
                    "email_blurb",
                    object(
                        "default",
                        "请查收并完成签署，谢谢。",
                        "maxLength",
                        2000,
                        "title",
                        "Email Blurb",
                        "type",
                        "string"),
                    "status",
                    object(
                        "default",
                        "sent",
                        "description",
                        "sent=立即发送；created=只建草稿",
                        "enum",
                        array("sent", "created"),
                        "title",
                        "Status",
                        "type",
                        "string")),
                "title",
                "CreateEnvelopeParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/docusign/DocusignAdapter.java",
                "已将模板角色、文件、签署顺序转换为信封请求。",
                "补充 prepare：核对模板角色、必填签署人和文件，再生成签署业务请求。",
                "发送与作废是有业务影响的写操作；超时后应核对原单。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "支持模板角色或文件构建、签署顺序和文件编号生成。",
                "未读取模板核对角色及签署必填项，业务模板名和字段还要由调用方解析。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/docusign/DocusignAdapter.java",
                        86,
                        "case \"docusign.envelopes.create\""))))
        .build();
  }

  private static BuiltinOperation envelopesGet() {
    return operation("docusign.envelopes.get")
        .summary("查询单个信封状态与签署人进度")
        .quota("120/min")
        .since("2026-10")
        .category("信封")
        .order(30)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "envelope_id",
                    object(
                        "maxLength",
                        128,
                        "minLength",
                        1,
                        "title",
                        "Envelope Id",
                        "type",
                        "string")),
                "required",
                array("envelope_id"),
                "title",
                "GetEnvelopeParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/docusign/DocusignAdapter.java",
                "已合并信封与签署人两次查询，返回签署进度。",
                "增加 terminal、下一签署人和逾期提示，保留原始状态。",
                "",
                "ready",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "合并信封与签署人，计算终态、下一批签署人和草稿/等待动作。",
                "两次读取不是快照；不代替签署人操作，过期判断仍以供应商状态为准。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/docusign/DocusignAdapter.java",
                        165,
                        "case \"docusign.envelopes.get\""))))
        .build();
  }

  private static BuiltinOperation envelopesList() {
    return operation("docusign.envelopes.list")
        .summary("按日期/状态列出信封")
        .since("2026-10")
        .category("信封")
        .order(20)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "from_date",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "起始日期，如 2026-10-01（默认近 30 天）",
                        "title",
                        "From Date"),
                    "to_date",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "截止日期，如 2026-10-31（默认今天）",
                        "title",
                        "To Date"),
                    "status",
                    object(
                        "anyOf",
                        array(
                            object(
                                "enum",
                                array(
                                    "created",
                                    "sent",
                                    "delivered",
                                    "completed",
                                    "declined",
                                    "voided"),
                                "type",
                                "string"),
                            object("type", "null")),
                        "default",
                        null,
                        "title",
                        "Status"),
                    "page_size",
                    object(
                        "default",
                        20,
                        "maximum",
                        100,
                        "minimum",
                        1,
                        "title",
                        "Page Size",
                        "type",
                        "integer"),
                    "start_position",
                    object("type", "integer", "minimum", 0, "maximum", 10000000)),
                "title",
                "ListEnvelopesParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/docusign/DocusignAdapter.java",
                "按日期/状态读取单页，返回下一页位置和可见总量。",
                "使用新增有界 collect 汇总，保留原子单页接口。",
                "上游分页不是一致性快照，业务按 ID 去重。",
                "recommended",
                List.of("docusign.envelopes.collect"),
                CapabilityReview.Depth.ENHANCED,
                "规范日期与状态，支持原生分页位置并返回可续传的下一位置。",
                "单页原子接口；有界汇总使用 envelopes.collect，不保证上游快照。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/docusign/DocusignAdapter.java",
                        236,
                        "case \"docusign.envelopes.list\""),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/docusign/DocusignAdapter.java",
                        376,
                        "private static Map<String, Object> page("))))
        .build();
  }

  private static BuiltinOperation envelopesVoid() {
    return operation("docusign.envelopes.void")
        .summary("作废信封（不可撤销）")
        .description("作废后签署链接失效。属于高危操作，需要单独授权，且会写入审计。")
        .quota("10/min")
        .sensitive(true)
        .since("2026-10")
        .category("信封")
        .order(40)
        .cost("none")
        .tags("write", "danger")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "envelope_id",
                    object(
                        "maxLength", 128, "minLength", 1, "title", "Envelope Id", "type", "string"),
                    "reason",
                    object(
                        "description",
                        "作废原因（会写入审计）",
                        "maxLength",
                        200,
                        "minLength",
                        2,
                        "title",
                        "Reason",
                        "type",
                        "string")),
                "required",
                array("envelope_id", "reason"),
                "title",
                "VoidEnvelopeParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/docusign/DocusignAdapter.java",
                "读取当前状态，重复作废返回已作废，阻止已完成、拒绝或删除的信封再作废。",
                "继续保持明确原因与稳定业务重试策略。",
                "状态读取和更新之间仍有竞争，供应商做最终校验。",
                "ready",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "先读取状态，重复作废返回已作废；阻止已完成/拒绝/删除信封再作废。",
                "状态检查和更新不是原子事务，供应商仍做最终校验；保留原接口兼容。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/docusign/DocusignAdapter.java",
                        286,
                        "case \"docusign.envelopes.void\""))))
        .build();
  }

  private static BuiltinOperation templatesList() {
    return operation("docusign.templates.list")
        .summary("列出可用模板")
        .since("2026-10")
        .category("模板")
        .order(10)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "page_size",
                    object(
                        "default",
                        20,
                        "maximum",
                        100,
                        "minimum",
                        1,
                        "title",
                        "Page Size",
                        "type",
                        "integer"),
                    "start_position",
                    object("type", "integer", "minimum", 0, "maximum", 10000000)),
                "title",
                "ListTemplatesParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/docusign/DocusignAdapter.java",
                "规范模板目录并返回真实下一页位置。",
                "用 templates.collect 汇总、templates.get 读取模板角色和字段。",
                "不自动指定签署人和签署策略。",
                "recommended",
                List.of("docusign.templates.collect", "docusign.templates.get"),
                CapabilityReview.Depth.ENHANCED,
                "规范模板目录并暴露原生下一页位置，可组合有界采集。",
                "模板角色和字段请通过 templates.get 读取。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/docusign/DocusignAdapter.java",
                        321,
                        "case \"docusign.templates.list\""))))
        .build();
  }

  private static BuiltinOperation testingAdvance() {
    return operation("docusign.testing.advance")
        .summary("【仅模拟模式】推进信封状态，用于验证回调流程")
        .description("真实模式下调用会返回 not_configured。")
        .quota("120/min")
        .sensitive(true)
        .since("2026-10")
        .category("测试")
        .order(10)
        .cost("none")
        .tags("mock")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "envelope_id",
                    object(
                        "maxLength", 128, "minLength", 1, "title", "Envelope Id", "type", "string"),
                    "status",
                    object(
                        "enum",
                        array("sent", "delivered", "completed", "declined", "voided"),
                        "title",
                        "Status",
                        "type",
                        "string")),
                "required",
                array("envelope_id", "status"),
                "title",
                "MockAdvanceParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/docusign/DocusignAdapter.java",
                "模拟器推进信封状态，便于回调演练。",
                "仅作为演练工具展示，真实签署由签署人完成。",
                "真实模式不可代替签署人完成签字。",
                "test_only",
                List.of(),
                CapabilityReview.Depth.TEST_ONLY,
                "模拟器推进信封状态。",
                "真实适配器没有代替签署人推进状态的实现。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/docusign/DocusignAdapter.java",
                        372,
                        "default -> throw GatewayException.config"))))
        .build();
  }

  private static BuiltinOperation templatesGet() {
    return operation("docusign.templates.get")
        .summary("读取签署模板、角色和字段")
        .description("合并模板与收件人字段定义，为业务签署预检提供角色和字段说明。")
        .timeout(90)
        .category("签署模板")
        .order(-60)
        .tags("read", "business")
        .params(
            object(
                "type",
                "object",
                "properties",
                object(
                    "template_id",
                    object("type", "string", "minLength", 1, "maxLength", 200, "description", "")),
                "required",
                array("template_id"),
                "additionalProperties",
                false,
                "examples",
                array(object("template_id", "demo-template"))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/docusign/DocusignAdapter.java",
                "合并模板与收件人字段定义，为业务签署预检提供角色和字段说明。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "自动翻页覆盖根记录；嵌套连接、供应商可见范围和来源快照仍有各自限制。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "联查模板定义与收件人字段，返回角色和 tabs。",
                "复杂角色、条件字段和签署策略需明确配置，不推断签署人。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/docusign/DocusignAdapter.java",
                        354,
                        "case \"docusign.templates.get\""))))
        .build();
  }

  private static BuiltinOperation envelopesCollect() {
    return operation("docusign.envelopes.collect")
        .summary("自动分页汇总：按日期/状态列出信封")
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
                    "from_date",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "起始日期，如 2026-10-01（默认近 30 天）",
                        "title",
                        "From Date"),
                    "to_date",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "截止日期，如 2026-10-31（默认今天）",
                        "title",
                        "To Date"),
                    "status",
                    object(
                        "anyOf",
                        array(
                            object(
                                "enum",
                                array(
                                    "created",
                                    "sent",
                                    "delivered",
                                    "completed",
                                    "declined",
                                    "voided"),
                                "type",
                                "string"),
                            object("type", "null")),
                        "default",
                        null,
                        "title",
                        "Status"),
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

  private static BuiltinOperation templatesCollect() {
    return operation("docusign.templates.collect")
        .summary("自动分页汇总：列出可用模板")
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
