package group.cyt.common.module.registry.catalog.platform;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import group.cyt.common.module.registry.catalog.*;
import java.util.List;

/** Built-in gcs contracts. Edit metadata, parameters and review together here. */
public final class GcsCatalog implements PlatformCatalog {
  @Override
  public void register(CatalogBuilder catalog) {
    catalog.platform("gcs", "Google Cloud Storage", "300/min");
    catalog.credentials(credentials());
    catalog.alias("gcs.object.upload", "gcs.objects.upload");
    catalog.alias("gcs.object.metadata", "gcs.objects.get_metadata");
    catalog.alias("gcs.object.list", "gcs.objects.list");
    catalog.alias("gcs.object.delete", "gcs.objects.delete");
    catalog.operation(objectsDelete());
    catalog.operation(objectsGetMetadata());
    catalog.operation(objectsList());
    catalog.operation(objectsUpload());
    catalog.operation(objectsCollect());
  }

  private static PlatformCredentials credentials() {
    return new PlatformCredentials(
        "gcs",
        "Google Cloud Storage",
        "",
        "用服务账号；本地可用 ADC（gcloud auth application-default login）",
        "inherit",
        false,
        false,
        "",
        "",
        "",
        List.of(
            new PlatformCredentials.Variable(
                "GCS_BUCKET",
                "存储桶",
                true,
                false,
                "桶名，如 cyt-contracts",
                "",
                true,
                "GCS_{SYSTEM}_BUCKET",
                false),
            new PlatformCredentials.Variable(
                "GCS_SA_JSON",
                "服务账号 JSON",
                false,
                true,
                "与 GOOGLE_APPLICATION_CREDENTIALS 二选一",
                "",
                true,
                "GCS_{SYSTEM}_SA_JSON",
                false),
            new PlatformCredentials.Variable(
                "GOOGLE_APPLICATION_CREDENTIALS",
                "SA 文件路径",
                false,
                false,
                "本地指到 json 文件路径",
                "",
                false,
                "",
                false)));
  }

  private static BuiltinOperation objectsDelete() {
    return operation("gcs.objects.delete")
        .summary("删除对象（不可恢复）")
        .description("直接删除，没有回收站。高危操作，需单独授权。")
        .quota("30/min")
        .sensitive(true)
        .since("2026-10")
        .category("对象")
        .order(40)
        .cost("none")
        .tags("write", "danger")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "path",
                    object("maxLength", 512, "minLength", 1, "title", "Path", "type", "string"),
                    "bucket",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "title",
                        "Bucket")),
                "required",
                array("path"),
                "title",
                "ObjectParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                "对象路径转换为 Cloud Storage 删除请求。",
                "提供可选代际 generation 条件，删除前校验版本，避免覆盖并发更新。",
                "删除不可恢复；业务侧需确定保留策略。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "按对象路径执行单项删除并返回 deleted。",
                "没有 generation 版本条件、业务保留策略或删除预检。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                        280,
                        "if (op.key().equals(\"gcs.objects.delete\"))"))))
        .build();
  }

  private static BuiltinOperation objectsGetMetadata() {
    return operation("gcs.objects.get_metadata")
        .summary("对象元信息")
        .quota("300/min")
        .retries(3)
        .since("2026-10")
        .category("对象")
        .order(20)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "path",
                    object("maxLength", 512, "minLength", 1, "title", "Path", "type", "string"),
                    "bucket",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "title",
                        "Bucket")),
                "required",
                array("path"),
                "title",
                "ObjectParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                "可读取对象的原始元信息。",
                "统一 size、content_type、etag、generation、updated_at 字段。",
                "",
                "ready",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "按路径直接返回 Storage 元信息。",
                "元信息仍为供应商结构，没有业务文件实体或统一属性模型。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                        239,
                        "private Object gcs("))))
        .build();
  }

  private static BuiltinOperation objectsList() {
    return operation("gcs.objects.list")
        .summary("按前缀列出对象")
        .quota("120/min")
        .retries(3)
        .since("2026-10")
        .category("对象")
        .order(30)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "prefix",
                    object(
                        "default",
                        "",
                        "description",
                        "仅返回此前缀开头的对象",
                        "maxLength",
                        512,
                        "title",
                        "Prefix",
                        "type",
                        "string"),
                    "page_size",
                    object(
                        "default",
                        50,
                        "description",
                        "maxResults；官方建议上限 1000",
                        "maximum",
                        1000,
                        "minimum",
                        1,
                        "title",
                        "Page Size",
                        "type",
                        "integer"),
                    "page_token",
                    object(
                        "anyOf",
                        array(object("maxLength", 2048, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "上一页返回的 nextPageToken",
                        "title",
                        "Page Token"),
                    "delimiter",
                    object(
                        "anyOf",
                        array(object("maxLength", 8, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "常用 '/'；开启类似文件夹的目录列表，响应会有 prefixes[]",
                        "title",
                        "Delimiter"),
                    "include_trailing_delimiter",
                    object(
                        "default",
                        false,
                        "description",
                        "对象以 delimiter 结尾时也在 items[] 中返回其元信息",
                        "title",
                        "Include Trailing Delimiter",
                        "type",
                        "boolean"),
                    "include_folders_as_prefixes",
                    object(
                        "default",
                        false,
                        "description",
                        "包含空/托管文件夹；要求 delimiter='/'",
                        "title",
                        "Include Folders As Prefixes",
                        "type",
                        "boolean"),
                    "match_glob",
                    object(
                        "anyOf",
                        array(object("maxLength", 1024, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "对象名 glob，例如 contracts/**/*.pdf",
                        "title",
                        "Match Glob"),
                    "start_offset",
                    object(
                        "anyOf",
                        array(object("maxLength", 1024, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "按对象名词典序的起始位置（含）",
                        "title",
                        "Start Offset"),
                    "end_offset",
                    object(
                        "anyOf",
                        array(object("maxLength", 1024, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "按对象名词典序的结束位置（不含）",
                        "title",
                        "End Offset"),
                    "projection",
                    object(
                        "default",
                        "noAcl",
                        "description",
                        "返回属性集合；full 会包含 ACL",
                        "enum",
                        array("noAcl", "full"),
                        "title",
                        "Projection",
                        "type",
                        "string"),
                    "versions",
                    object(
                        "default",
                        false,
                        "description",
                        "是否把对象的各版本分别列出",
                        "title",
                        "Versions",
                        "type",
                        "boolean"),
                    "soft_deleted",
                    object(
                        "default",
                        false,
                        "description",
                        "仅列出软删除对象；不能与 versions=true 同用",
                        "title",
                        "Soft Deleted",
                        "type",
                        "boolean"),
                    "bucket",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "不填用默认桶",
                        "title",
                        "Bucket")),
                "title",
                "ListObjectsParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                "保留供应商筛选和分页参数，适合作为单页原子接口。",
                "优先使用新增业务接口：有界自动分页、统一结果与签名续传游标。",
                "原接口继续兼容；items 只代表当前页。",
                "recommended",
                List.of("gcs.objects.collect"),
                CapabilityReview.Depth.BASIC,
                "映射官方筛选字段及单页 token。",
                "调用方仍管理原生分页；可选用 collect，但后者也不补齐业务关联。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                        246,
                        "if (op.key().equals(\"gcs.objects.list\"))"))))
        .build();
  }

  private static BuiltinOperation objectsUpload() {
    return operation("gcs.objects.upload")
        .summary("上传对象")
        .timeout(120)
        .since("2026-10")
        .category("对象")
        .order(10)
        .cost("none")
        .tags("write")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "path",
                    object(
                        "description",
                        "对象路径，如 contracts/2026/abc.pdf",
                        "maxLength",
                        512,
                        "minLength",
                        1,
                        "title",
                        "Path",
                        "type",
                        "string"),
                    "content_base64",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "文件内容 base64；与 content_text 二选一",
                        "title",
                        "Content Base64"),
                    "content_text",
                    object(
                        "anyOf",
                        array(
                            object("maxLength", 5000000, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "直接传文本内容（UTF-8），适合 CSV/JSON/日志；与 content_base64 二选一",
                        "title",
                        "Content Text"),
                    "content_type",
                    object(
                        "default",
                        "application/octet-stream",
                        "description",
                        "MIME 类型；用 content_text 时默认 text/plain",
                        "maxLength",
                        128,
                        "title",
                        "Content Type",
                        "type",
                        "string"),
                    "bucket",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "不填用默认桶",
                        "title",
                        "Bucket")),
                "required",
                array("path"),
                "title",
                "UploadObjectParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                "处理 Base64 内容、对象路径和 MIME 上传。",
                "增加防覆盖条件和上传结果 checksum 校验；大文件改用签名直传。",
                "Base64 更适合小文件；不应默认覆盖同名对象。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "支持文本/Base64、大小校验和 MIME 上传。",
                "缺少防覆盖版本条件、校验和确认及大文件直传流程。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                        264,
                        "if (op.key().equals(\"gcs.objects.upload\"))"),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                        287,
                        "public static byte[] content("))))
        .build();
  }

  private static BuiltinOperation objectsCollect() {
    return operation("gcs.objects.collect")
        .summary("自动分页汇总：按前缀列出对象")
        .description(
            "自动分页、统一 items/page/errors；支持 max_items、max_pages 与签名续传游标。仅扫描根记录，嵌套资源遵循原接口的范围。读取能力独立授权，保留原接口兼容。")
        .quota("120/min")
        .timeout(90)
        .category("业务聚合")
        .order(-50)
        .sourceOperation("gcs.objects.list")
        .tags("read", "business", "collection")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "prefix",
                    object(
                        "default",
                        "",
                        "description",
                        "仅返回此前缀开头的对象",
                        "maxLength",
                        512,
                        "title",
                        "Prefix",
                        "type",
                        "string"),
                    "delimiter",
                    object(
                        "anyOf",
                        array(object("maxLength", 8, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "常用 '/'；开启类似文件夹的目录列表，响应会有 prefixes[]",
                        "title",
                        "Delimiter"),
                    "include_trailing_delimiter",
                    object(
                        "default",
                        false,
                        "description",
                        "对象以 delimiter 结尾时也在 items[] 中返回其元信息",
                        "title",
                        "Include Trailing Delimiter",
                        "type",
                        "boolean"),
                    "include_folders_as_prefixes",
                    object(
                        "default",
                        false,
                        "description",
                        "包含空/托管文件夹；要求 delimiter='/'",
                        "title",
                        "Include Folders As Prefixes",
                        "type",
                        "boolean"),
                    "match_glob",
                    object(
                        "anyOf",
                        array(object("maxLength", 1024, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "对象名 glob，例如 contracts/**/*.pdf",
                        "title",
                        "Match Glob"),
                    "start_offset",
                    object(
                        "anyOf",
                        array(object("maxLength", 1024, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "按对象名词典序的起始位置（含）",
                        "title",
                        "Start Offset"),
                    "end_offset",
                    object(
                        "anyOf",
                        array(object("maxLength", 1024, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "按对象名词典序的结束位置（不含）",
                        "title",
                        "End Offset"),
                    "projection",
                    object(
                        "default",
                        "noAcl",
                        "description",
                        "返回属性集合；full 会包含 ACL",
                        "enum",
                        array("noAcl", "full"),
                        "title",
                        "Projection",
                        "type",
                        "string"),
                    "versions",
                    object(
                        "default",
                        false,
                        "description",
                        "是否把对象的各版本分别列出",
                        "title",
                        "Versions",
                        "type",
                        "boolean"),
                    "soft_deleted",
                    object(
                        "default",
                        false,
                        "description",
                        "仅列出软删除对象；不能与 versions=true 同用",
                        "title",
                        "Soft Deleted",
                        "type",
                        "boolean"),
                    "bucket",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "不填用默认桶",
                        "title",
                        "Bucket"),
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
                "没有业务文件关联或对象版本策略。",
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
