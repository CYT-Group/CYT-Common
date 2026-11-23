package group.cyt.common.module.registry.catalog.platform;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import group.cyt.common.module.registry.catalog.*;
import java.util.List;

/** Built-in gdrive contracts. Edit metadata, parameters and review together here. */
public final class GdriveCatalog implements PlatformCatalog {
  @Override
  public void register(CatalogBuilder catalog) {
    catalog.platform("gdrive", "Google Drive", "300/min");
    catalog.credentials(credentials());
    catalog.alias("gdrive.folder.create", "gdrive.folders.create");
    catalog.operation(filesGet());
    catalog.operation(filesList());
    catalog.operation(filesUpload());
    catalog.operation(foldersCreate());
    catalog.operation(filesSearch());
  }

  private static PlatformCredentials credentials() {
    return new PlatformCredentials(
        "gdrive",
        "Google Drive",
        "",
        "与 Gmail 共用 OAuth 客户端",
        "inherit",
        false,
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
                "同 Gmail",
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
                "GOOGLE_REFRESH_TOKEN",
                "Drive Refresh Token",
                true,
                true,
                "scope 需含 drive",
                "",
                true,
                "GOOGLE_{SYSTEM}_REFRESH_TOKEN",
                false)));
  }

  private static BuiltinOperation filesGet() {
    return operation("gdrive.files.get")
        .summary("文件元信息")
        .quota("300/min")
        .retries(3)
        .since("2026-10")
        .category("文件")
        .order(20)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "file_id",
                    object("maxLength", 128, "minLength", 4, "title", "File Id", "type", "string"),
                    "fields",
                    object(
                        "default",
                        "id,name,mimeType,size,modifiedTime,webViewLink,parents",
                        "maxLength",
                        1000,
                        "title",
                        "Fields",
                        "type",
                        "string"),
                    "supports_all_drives",
                    object(
                        "default",
                        false,
                        "description",
                        "请求方是否支持共享云端硬盘",
                        "title",
                        "Supports All Drives",
                        "type",
                        "boolean"),
                    "include_permissions_for_view",
                    object(
                        "anyOf",
                        array(
                            object("const", "published", "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "额外返回的权限视图，目前仅 published",
                        "title",
                        "Include Permissions For View"),
                    "include_labels",
                    object(
                        "anyOf",
                        array(object("maxLength", 512, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "要包含的 Drive 标签 ID，逗号分隔",
                        "title",
                        "Include Labels")),
                "required",
                array("file_id"),
                "title",
                "FileGetParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                "支持按文件 ID 与 fields 读取元信息。",
                "提供规范化路径、权限摘要和 shortcuts 目标信息。",
                "元信息查询不等于下载文件内容。",
                "ready",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "转换文件 ID、fields、q 或分页参数，结果接近官方结构。",
                "仍需理解 Drive 字段、目录 ID 和查询语法；目录定位和业务文件解析未封装。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                        152,
                        "private Object drive("))))
        .build();
  }

  private static BuiltinOperation filesList() {
    return operation("gdrive.files.list")
        .summary("搜索文件")
        .quota("300/min")
        .retries(3)
        .since("2026-10")
        .category("文件")
        .order(10)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "query",
                    object(
                        "default",
                        "trashed = false",
                        "description",
                        "Drive 搜索语法 q",
                        "maxLength",
                        1000,
                        "title",
                        "Query",
                        "type",
                        "string"),
                    "page_size",
                    object(
                        "default",
                        20,
                        "description",
                        "每页最多 1000；服务端可能少于此数",
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
                    "order_by",
                    object(
                        "anyOf",
                        array(object("maxLength", 256, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "如 modifiedTime desc,name",
                        "title",
                        "Order By"),
                    "spaces",
                    object(
                        "default",
                        "drive",
                        "description",
                        "逗号分隔：drive,appDataFolder",
                        "maxLength",
                        64,
                        "title",
                        "Spaces",
                        "type",
                        "string"),
                    "corpora",
                    object(
                        "default",
                        "user",
                        "description",
                        "搜索范围",
                        "enum",
                        array("user", "domain", "drive", "allDrives"),
                        "title",
                        "Corpora",
                        "type",
                        "string"),
                    "drive_id",
                    object(
                        "anyOf",
                        array(object("maxLength", 256, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "corpora=drive 时的共享云端硬盘 ID",
                        "title",
                        "Drive Id"),
                    "include_items_from_all_drives",
                    object(
                        "default",
                        false,
                        "description",
                        "是否包含我的云端硬盘与共享云端硬盘项目",
                        "title",
                        "Include Items From All Drives",
                        "type",
                        "boolean"),
                    "supports_all_drives",
                    object(
                        "default",
                        false,
                        "description",
                        "请求方是否支持共享云端硬盘",
                        "title",
                        "Supports All Drives",
                        "type",
                        "boolean"),
                    "include_permissions_for_view",
                    object(
                        "anyOf",
                        array(
                            object("const", "published", "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "额外返回的权限视图，目前仅 published",
                        "title",
                        "Include Permissions For View"),
                    "include_labels",
                    object(
                        "anyOf",
                        array(object("maxLength", 512, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "要包含的 Drive 标签 ID，逗号分隔",
                        "title",
                        "Include Labels"),
                    "fields",
                    object(
                        "default",
                        "nextPageToken,incompleteSearch,files(id,name,mimeType,size,modifiedTime,webViewLink)",
                        "description",
                        "partial response 字段；默认已包含 nextPageToken 供翻页",
                        "maxLength",
                        1000,
                        "title",
                        "Fields",
                        "type",
                        "string")),
                "title",
                "FilesListParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                "保留供应商筛选和分页参数，适合作为单页原子接口。",
                "优先使用新增业务接口：有界自动分页、统一结果与签名续传游标。",
                "原接口继续兼容；items 只代表当前页。",
                "recommended",
                List.of("gdrive.files.search"),
                CapabilityReview.Depth.BASIC,
                "转换文件 ID、fields、q 或分页参数，结果接近官方结构。",
                "仍需理解 Drive 字段、目录 ID 和查询语法；目录定位和业务文件解析未封装。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                        152,
                        "private Object drive("))))
        .build();
  }

  private static BuiltinOperation filesUpload() {
    return operation("gdrive.files.upload")
        .summary("上传文件")
        .description("上限 20MB。更大的文件走 GCS。")
        .timeout(60)
        .since("2026-10")
        .category("文件")
        .order(30)
        .cost("none")
        .tags("write")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "name",
                    object("maxLength", 255, "minLength", 1, "title", "Name", "type", "string"),
                    "content_base64",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "文件内容 base64（≤20MB）；与 content_text 二选一",
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
                        "直接传文本内容（UTF-8）；与 content_base64 二选一",
                        "title",
                        "Content Text"),
                    "mime_type",
                    object(
                        "default",
                        "application/pdf",
                        "maxLength",
                        128,
                        "title",
                        "Mime Type",
                        "type",
                        "string"),
                    "parent_id",
                    object(
                        "anyOf",
                        array(object("maxLength", 128, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "title",
                        "Parent Id"),
                    "fields",
                    object(
                        "default",
                        "id,name,webViewLink",
                        "description",
                        "上传成功后的返回字段",
                        "maxLength",
                        1000,
                        "title",
                        "Fields",
                        "type",
                        "string"),
                    "supports_all_drives",
                    object(
                        "default",
                        false,
                        "description",
                        "请求方是否支持共享云端硬盘",
                        "title",
                        "Supports All Drives",
                        "type",
                        "boolean"),
                    "ignore_default_visibility",
                    object(
                        "default",
                        false,
                        "description",
                        "忽略网域默认可见性",
                        "title",
                        "Ignore Default Visibility",
                        "type",
                        "boolean"),
                    "use_content_as_indexable_text",
                    object(
                        "default",
                        false,
                        "description",
                        "是否将内容作为可索引文本",
                        "title",
                        "Use Content As Indexable Text",
                        "type",
                        "boolean"),
                    "keep_revision_forever",
                    object(
                        "default",
                        false,
                        "description",
                        "是否永久保留该版本（仅适用可保留版本的文件）",
                        "title",
                        "Keep Revision Forever",
                        "type",
                        "boolean")),
                "required",
                array("name"),
                "title",
                "UploadParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                "已处理文件内容、名称、MIME 与父目录。",
                "增加按业务目录定位、重复名称策略及上传后校验。",
                "Drive 名称不唯一，不能把名称当文件 ID。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "构建文件元信息与 multipart，校验内容和 MIME。",
                "父目录解析、同名策略和完成后校验仍由业务方处理。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                        203,
                        "ByteArrayOutputStream out = new ByteArrayOutputStream();"))))
        .build();
  }

  private static BuiltinOperation foldersCreate() {
    return operation("gdrive.folders.create")
        .summary("新建文件夹")
        .since("2026-10")
        .category("文件夹")
        .order(10)
        .cost("none")
        .tags("write")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "name",
                    object("maxLength", 255, "minLength", 1, "title", "Name", "type", "string"),
                    "parent_id",
                    object(
                        "anyOf",
                        array(object("maxLength", 128, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "title",
                        "Parent Id"),
                    "fields",
                    object(
                        "default",
                        "id,name,webViewLink",
                        "maxLength",
                        1000,
                        "title",
                        "Fields",
                        "type",
                        "string"),
                    "supports_all_drives",
                    object(
                        "default",
                        false,
                        "description",
                        "请求方是否支持共享云端硬盘",
                        "title",
                        "Supports All Drives",
                        "type",
                        "boolean")),
                "required",
                array("name"),
                "title",
                "FolderCreateParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                "把文件夹名称和父 ID 转成 Drive folder 创建。",
                "增加 ensure_folder：按父目录和名称查重，发生重名时明确返回候选。",
                "不能随意选择同名目录；现接口保持显式创建。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "将名称、父目录和 folder MIME 转成一次创建。",
                "没有 ensure_folder 查重或同名歧义处理。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/google/GoogleAdapter.java",
                        192,
                        "if (op.key().equals(\"gdrive.folders.create\"))"))))
        .build();
  }

  private static BuiltinOperation filesSearch() {
    return operation("gdrive.files.search")
        .summary("按文件名、目录和类型搜索文件")
        .description(
            "自动分页、统一 items/page/errors；支持 max_items、max_pages 与签名续传游标。仅扫描根记录，嵌套资源遵循原接口的范围。读取能力独立授权，保留原接口兼容。")
        .quota("300/min")
        .timeout(90)
        .category("业务聚合")
        .order(-50)
        .sourceOperation("gdrive.files.list")
        .tags("read", "business", "collection")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "query",
                    object(
                        "default",
                        "trashed = false",
                        "description",
                        "Drive 搜索语法 q",
                        "maxLength",
                        1000,
                        "title",
                        "Query",
                        "type",
                        "string"),
                    "order_by",
                    object(
                        "anyOf",
                        array(object("maxLength", 256, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "如 modifiedTime desc,name",
                        "title",
                        "Order By"),
                    "spaces",
                    object(
                        "default",
                        "drive",
                        "description",
                        "逗号分隔：drive,appDataFolder",
                        "maxLength",
                        64,
                        "title",
                        "Spaces",
                        "type",
                        "string"),
                    "corpora",
                    object(
                        "default",
                        "user",
                        "description",
                        "搜索范围",
                        "enum",
                        array("user", "domain", "drive", "allDrives"),
                        "title",
                        "Corpora",
                        "type",
                        "string"),
                    "drive_id",
                    object(
                        "anyOf",
                        array(object("maxLength", 256, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "corpora=drive 时的共享云端硬盘 ID",
                        "title",
                        "Drive Id"),
                    "include_items_from_all_drives",
                    object(
                        "default",
                        false,
                        "description",
                        "是否包含我的云端硬盘与共享云端硬盘项目",
                        "title",
                        "Include Items From All Drives",
                        "type",
                        "boolean"),
                    "supports_all_drives",
                    object(
                        "default",
                        false,
                        "description",
                        "请求方是否支持共享云端硬盘",
                        "title",
                        "Supports All Drives",
                        "type",
                        "boolean"),
                    "include_permissions_for_view",
                    object(
                        "anyOf",
                        array(
                            object("const", "published", "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "额外返回的权限视图，目前仅 published",
                        "title",
                        "Include Permissions For View"),
                    "include_labels",
                    object(
                        "anyOf",
                        array(object("maxLength", 512, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "要包含的 Drive 标签 ID，逗号分隔",
                        "title",
                        "Include Labels"),
                    "fields",
                    object(
                        "default",
                        "nextPageToken,incompleteSearch,files(id,name,mimeType,size,modifiedTime,webViewLink)",
                        "description",
                        "partial response 字段；默认已包含 nextPageToken 供翻页",
                        "maxLength",
                        1000,
                        "title",
                        "Fields",
                        "type",
                        "string"),
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
                    "name_contains",
                    object(
                        "type", "string", "minLength", 1, "maxLength", 200, "description", "文件名包含"),
                    "parent_id",
                    object(
                        "type",
                        "string",
                        "minLength",
                        1,
                        "maxLength",
                        160,
                        "description",
                        "所在文件夹 ID"),
                    "mime_type",
                    object(
                        "type",
                        "string",
                        "minLength",
                        1,
                        "maxLength",
                        160,
                        "description",
                        "文件 MIME 类型"),
                    "modified_since",
                    object()),
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
                "自动转义文件名/目录/类型筛选并有界翻页。",
                "目录仍需官方 ID；不解析业务路径、同名歧义或文件所属业务。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                        382,
                        "public static String driveQuery("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                        166,
                        "while (more && pages < maxPages"))))
        .build();
  }
}
