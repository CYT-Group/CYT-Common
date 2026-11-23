package group.cyt.common.module.registry.catalog.platform;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import group.cyt.common.module.registry.catalog.*;
import java.util.List;

/** Built-in nas contracts. Edit metadata, parameters and review together here. */
public final class NasCatalog implements PlatformCatalog {
  @Override
  public void register(CatalogBuilder catalog) {
    catalog.platform("nas", "群晖 NAS MinIO", "600/min");
    catalog.credentials(credentials());
    catalog.operation(bucketsCreate());
    catalog.operation(bucketsDelete());
    catalog.operation(bucketsList());
    catalog.operation(objectsCopy());
    catalog.operation(objectsCopyPrefix());
    catalog.operation(objectsDelete());
    catalog.operation(objectsDeleteBatch());
    catalog.operation(objectsList());
    catalog.operation(objectsPresignGet());
    catalog.operation(objectsPresignPut());
    catalog.operation(objectsUpload());
    catalog.operation(objectsReadHead());
    catalog.operation(objectsStat());
    catalog.operation(objectsCollect());
  }

  private static PlatformCredentials credentials() {
    return new PlatformCredentials(
        "nas",
        "群晖 NAS MinIO",
        "https://min.io/docs/minio/linux/index.html",
        "培训资料库（bucket cyt-training）。与门户培训中心同一套 NAS，变量名与门户 NAS_MINIO_* 同名同值，凭据只放网关一处",
        "inherit",
        false,
        false,
        "",
        "",
        "",
        List.of(
            new PlatformCredentials.Variable(
                "NAS_MINIO_ENDPOINT",
                "服务地址",
                true,
                false,
                "含协议与主机，如 https://oss.cyt.group 或 http://192.168.1.248:9000",
                "https://oss.cyt.group",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "NAS_MINIO_BUCKET",
                "存储桶",
                false,
                false,
                "默认 cyt-training",
                "cyt-training",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "NAS_MINIO_ACCESS_KEY",
                "Access Key",
                true,
                true,
                "MinIO 控制台的 Access Key",
                "",
                true,
                "NAS_MINIO_{SYSTEM}_ACCESS_KEY",
                false),
            new PlatformCredentials.Variable(
                "NAS_MINIO_SECRET_KEY",
                "Secret Key",
                true,
                true,
                "MinIO 控制台的 Secret Key",
                "",
                true,
                "NAS_MINIO_{SYSTEM}_SECRET_KEY",
                false),
            new PlatformCredentials.Variable(
                "NAS_MINIO_REGION",
                "Region",
                false,
                false,
                "默认 us-east-1（MinIO 单机默认值）",
                "us-east-1",
                false,
                "",
                false)));
  }

  private static BuiltinOperation bucketsCreate() {
    return operation("nas.buckets.create")
        .summary("新建桶")
        .description(
            "PUT /{bucket}。桶名走 S3 规则（3–63 位小写字母/数字/点/连字符）。MinIO 单机 region 一般是 us-east-1，非该值会带 LocationConstraint。桶已存在返回 422 code=bucket_exists。高危操作，需单独授权。")
        .quota("10/min")
        .sensitive(true)
        .timeout(60)
        .since("2026-10")
        .category("桶")
        .order(20)
        .cost("none")
        .tags("write", "danger")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "bucket",
                    object(
                        "description",
                        "桶名：3–63 位小写字母/数字/点/连字符，字母或数字开头结尾",
                        "maxLength",
                        63,
                        "minLength",
                        3,
                        "title",
                        "Bucket",
                        "type",
                        "string"),
                    "region",
                    object(
                        "anyOf",
                        array(object("maxLength", 32, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "不填用 NAS_MINIO_REGION（MinIO 单机一般 us-east-1）",
                        "title",
                        "Region")),
                "required",
                array("bucket"),
                "title",
                "CreateBucketParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                "处理 S3 桶创建和区域配置。",
                "保持管理员原子操作；业务系统通常只需被分配的桶或目录。",
                "",
                "low_level",
                List.of(),
                CapabilityReview.Depth.SUPPORTING,
                "提供 S3 桶生命周期与目录管理。",
                "按基础设施职责保留；业务应用宜使用分配后的文件能力。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                        161,
                        "case \"buckets.list\""))))
        .build();
  }

  private static BuiltinOperation bucketsDelete() {
    return operation("nas.buckets.delete")
        .summary("删除桶（必须为空）")
        .description(
            "只能删空桶；桶里还有对象时返回 422 code=bucket_not_empty，先用 nas.objects.delete 清空。高危操作，需单独授权。")
        .quota("10/min")
        .sensitive(true)
        .timeout(60)
        .since("2026-10")
        .category("桶")
        .order(30)
        .cost("none")
        .tags("write", "danger")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "bucket",
                    object(
                        "description",
                        "桶名：3–63 位小写字母/数字/点/连字符，字母或数字开头结尾",
                        "maxLength",
                        63,
                        "minLength",
                        3,
                        "title",
                        "Bucket",
                        "type",
                        "string")),
                "required",
                array("bucket"),
                "title",
                "BucketParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                "删除指定桶，供应商要求为空。",
                "增加桶非空预检，展示对象量后再决定删除。",
                "不应隐式清空桶。",
                "low_level",
                List.of(),
                CapabilityReview.Depth.SUPPORTING,
                "提供 S3 桶生命周期与目录管理。",
                "按基础设施职责保留；业务应用宜使用分配后的文件能力。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                        161,
                        "case \"buckets.list\""))))
        .build();
  }

  private static BuiltinOperation bucketsList() {
    return operation("nas.buckets.list")
        .summary("列出全部桶")
        .description("返回 [{name, created_at}]；能看到哪些桶取决于 AccessKey 的权限。")
        .quota("120/min")
        .timeout(60)
        .retries(3)
        .since("2026-10")
        .category("桶")
        .order(10)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "description",
                "列桶不需要参数。",
                "properties",
                object(),
                "title",
                "EmptyParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                "将 S3 桶目录整理为名称与创建时间。",
                "保留运维入口；可按业务授权限制可见桶。",
                "",
                "low_level",
                List.of(),
                CapabilityReview.Depth.SUPPORTING,
                "提供 S3 桶生命周期与目录管理。",
                "按基础设施职责保留；业务应用宜使用分配后的文件能力。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                        161,
                        "case \"buckets.list\""))))
        .build();
  }

  private static BuiltinOperation objectsCopy() {
    return operation("nas.objects.copy")
        .summary("服务端复制对象")
        .description(
            "在 NAS 内部复制，文件不经网关。同桶只传两个 key；跨桶再给 source_bucket / dest_bucket。单次上限 5 GiB（S3 CopyObject 限制）。")
        .quota("30/min")
        .timeout(120)
        .since("2026-10")
        .category("对象")
        .order(70)
        .cost("none")
        .tags("write")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "source_key",
                    object(
                        "description",
                        "源对象键",
                        "maxLength",
                        500,
                        "minLength",
                        1,
                        "title",
                        "Source Key",
                        "type",
                        "string"),
                    "dest_key",
                    object(
                        "description",
                        "目标对象键",
                        "maxLength",
                        500,
                        "minLength",
                        1,
                        "title",
                        "Dest Key",
                        "type",
                        "string"),
                    "source_bucket",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "不填用默认桶",
                        "title",
                        "Source Bucket"),
                    "dest_bucket",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "不填用默认桶（同桶复制只传 key 即可）",
                        "title",
                        "Dest Bucket")),
                "required",
                array("source_key", "dest_key"),
                "title",
                "CopyObjectParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                "服务端复制单对象，返回结果元信息。",
                "增加覆盖策略和源版本条件，明确 copy 与 move 的差异。",
                "并发写入期间复制不是快照事务。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "复制单个源键到目标键。",
                "没有覆盖策略、源版本校验或完整迁移任务。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                        247,
                        "private Object copy("))))
        .build();
  }

  private static BuiltinOperation objectsCopyPrefix() {
    return operation("nas.objects.copy_prefix")
        .summary("整目录复制 / 移动（前缀级）")
        .description(
            "把一个前缀下的所有对象复制到新前缀；delete_source=true 时复制完删源，即网盘的移动/重命名目录。逐个 CopyObject 在网关内部完成（并发 8），调用方只发一次 HTTP，不按文件数吃配额。源目录为空也能复制（保留空目录）。高危操作，需单独授权。")
        .quota("20/min")
        .sensitive(true)
        .timeout(600)
        .since("2026-10")
        .category("对象")
        .order(75)
        .cost("none")
        .tags("write", "danger")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "source_prefix",
                    object(
                        "description",
                        "源目录前缀，如 项目/2026",
                        "maxLength",
                        500,
                        "minLength",
                        1,
                        "title",
                        "Source Prefix",
                        "type",
                        "string"),
                    "dest_prefix",
                    object(
                        "description",
                        "目标目录前缀，如 归档/2026",
                        "maxLength",
                        500,
                        "minLength",
                        1,
                        "title",
                        "Dest Prefix",
                        "type",
                        "string"),
                    "delete_source",
                    object(
                        "default",
                        false,
                        "description",
                        "复制完成后删除源对象（网盘的移动/重命名走这个）",
                        "title",
                        "Delete Source",
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
                "required",
                array("source_prefix", "dest_prefix"),
                "title",
                "CopyPrefixParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                "已有整目录复制/移动、最多 50000 对象、逐项失败；全部复制成功才删除源。",
                "大目录改为后台作业，支持进度、取消和按对象重试。",
                "跨对象不具原子性，可能部分成功；移动后应检查 failed。",
                "ready",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "自动枚举目录、逐项复制，全部复制成功才允许删除源，并返回失败项。",
                "跨对象非事务；大任务无持久进度和续跑，复制时并发写入不提供一致性快照。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                        311,
                        "private Object copyPrefix("))))
        .build();
  }

  private static BuiltinOperation objectsDelete() {
    return operation("nas.objects.delete")
        .summary("删除对象（不可恢复）")
        .description("直接删除 NAS 上的对象，没有回收站。幂等：对象本来就不存在也返回成功。高危操作，需单独授权。")
        .sensitive(true)
        .since("2026-10")
        .category("对象")
        .order(60)
        .cost("none")
        .tags("write", "danger")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "key",
                    object(
                        "description",
                        "对象键（bucket 内路径）",
                        "maxLength",
                        500,
                        "minLength",
                        1,
                        "title",
                        "Key",
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
                array("key"),
                "title",
                "ObjectParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                "删除单对象。",
                "增加保留策略检查或软删除业务接口。",
                "当前永久删除不可恢复。",
                "low_level",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "按键执行删除。",
                "没有业务保留策略或软删除。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                        146,
                        "case \"objects.delete\""))))
        .build();
  }

  private static BuiltinOperation objectsDeleteBatch() {
    return operation("nas.objects.delete_batch")
        .summary("批量删除对象（不可恢复）")
        .description(
            "POST /{bucket}?delete，一次最多 200 个键。删非空目录时先 list 整个前缀再一次删完，避免逐个 DELETE 撞 60/min 配额。返回 deleted 与 errors（部分失败不整体报错）。没有回收站，高危操作，需单独授权。")
        .sensitive(true)
        .timeout(120)
        .since("2026-10")
        .category("对象")
        .order(65)
        .cost("none")
        .tags("write", "danger")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "keys",
                    object(
                        "description",
                        "要删除的对象键，1–200 个；删除空目录时一次清完整个前缀",
                        "items",
                        object("type", "string"),
                        "maxItems",
                        200,
                        "minItems",
                        1,
                        "title",
                        "Keys",
                        "type",
                        "array"),
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
                array("keys"),
                "title",
                "DeleteBatchParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                "已有批次拆分及逐项 deleted/errors 返回。",
                "增加 dry_run 与业务范围检查，保留每个对象失败原因。",
                "必须处理部分失败。",
                "ready",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "拆分批量删除并保留每项成功/错误。",
                "批次处理本身不是业务范围预检或可审核的删除计划。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                        277,
                        "private Object delete("))))
        .build();
  }

  private static BuiltinOperation objectsList() {
    return operation("nas.objects.list")
        .summary("列目录 / 列举对象")
        .description(
            "默认递归列出全部（导入扫描用）；recursive=false 只看一层，返回 prefixes 折叠目录。单次上限 1000 条，truncated=true 时用 next_token 翻页。")
        .quota("30/min")
        .timeout(120)
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
                        "只看这个前缀下的对象，如 产品培训/",
                        "maxLength",
                        500,
                        "title",
                        "Prefix",
                        "type",
                        "string"),
                    "recursive",
                    object(
                        "default",
                        true,
                        "description",
                        "true=递归列出全部；false=只看当前一层",
                        "title",
                        "Recursive",
                        "type",
                        "boolean"),
                    "delimiter",
                    object(
                        "anyOf",
                        array(object("maxLength", 8, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "按分隔符折叠（常用 /），只看某一层的目录与文件；与 recursive=true 互斥",
                        "title",
                        "Delimiter"),
                    "max_keys",
                    object(
                        "default",
                        1000,
                        "description",
                        "单次最多返回条数，上限 1000",
                        "maximum",
                        1000,
                        "minimum",
                        1,
                        "title",
                        "Max Keys",
                        "type",
                        "integer"),
                    "continuation_token",
                    object(
                        "anyOf",
                        array(object("maxLength", 2048, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "上一页返回的 next_token；不传 = 第一页",
                        "title",
                        "Continuation Token"),
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
                "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                "支持目录分隔、递归和 S3 续传标记。",
                "优先用新增 collect，保留 folders 与统一分页信息。",
                "目录是对象前缀，不是真实文件系统目录。",
                "recommended",
                List.of("nas.objects.collect"),
                CapabilityReview.Depth.ENHANCED,
                "整理文件、目录前缀和续传标记。",
                "仍是单页前缀检索，没有业务文件实体和目录定位。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                        209,
                        "private Object list("))))
        .build();
  }

  private static BuiltinOperation objectsPresignGet() {
    return operation("nas.objects.presign_get")
        .summary("批量签发文件读取链接")
        .description("给浏览器直接播放/预览用的临时链接（默认 1 小时）。一次最多签 200 个键，一个课时的整套图卡一次签完。纯本地计算，不产生 NAS 请求。")
        .quota("300/min")
        .timeout(10)
        .since("2026-10")
        .category("对象")
        .order(10)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "keys",
                    object(
                        "description",
                        "要签读链接的对象键，可一次传多个（如一个课时的 52 张图卡）",
                        "items",
                        object("type", "string"),
                        "maxItems",
                        200,
                        "minItems",
                        1,
                        "title",
                        "Keys",
                        "type",
                        "array"),
                    "expires",
                    object(
                        "default",
                        3600,
                        "description",
                        "链接有效期（秒），默认 1 小时",
                        "maximum",
                        86400,
                        "minimum",
                        60,
                        "title",
                        "Expires",
                        "type",
                        "integer"),
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
                array("keys"),
                "title",
                "PresignGetParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                "已批量生成带期限的临时读链接。",
                "对外作为文档下载能力，明确到期时间与逐项结果。",
                "链接属于临时访问凭证，勿写入公开日志。",
                "ready",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "批量生成带期限的读取链接。",
                "不补齐文件业务权限、有效性和下载完成结果。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                        40,
                        "case \"objects.presign_get\""))))
        .build();
  }

  private static BuiltinOperation objectsPresignPut() {
    return operation("nas.objects.presign_put")
        .summary("签发文件直传链接（自动生成键名）")
        .description(
            "调用方给目录和原文件名，网关生成 <目录>/<随机12位>.<扩展名> 并签 PUT 链接，浏览器直传 NAS，文件不经网关。只放行文档/图片/音视频/压缩包扩展名。网盘这类需要「按真实路径和文件名存」的场景，改传 key 指定完整对象键（此时不做扩展名白名单）。高危操作，需单独授权。")
        .sensitive(true)
        .timeout(10)
        .since("2026-10")
        .category("对象")
        .order(20)
        .cost("none")
        .tags("write", "danger")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "folder",
                    object(
                        "default",
                        "",
                        "description",
                        "目标目录，如 portal/12 或 培训资料/产品；留空 = 桶根",
                        "maxLength",
                        200,
                        "title",
                        "Folder",
                        "type",
                        "string"),
                    "filename",
                    object(
                        "description",
                        "原始文件名，只用来取扩展名和校验类型；键名会自动加随机后缀防重名",
                        "maxLength",
                        200,
                        "minLength",
                        1,
                        "title",
                        "Filename",
                        "type",
                        "string"),
                    "key",
                    object(
                        "anyOf",
                        array(object("maxLength", 500, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "指定完整对象键（网盘按真实路径/文件名上传时用）。不填则按 folder + filename 自动生成随机键名。",
                        "title",
                        "Key"),
                    "expires",
                    object(
                        "default",
                        900,
                        "description",
                        "链接有效期（秒），默认 15 分钟",
                        "maximum",
                        3600,
                        "minimum",
                        60,
                        "title",
                        "Expires",
                        "type",
                        "integer"),
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
                array("filename"),
                "title",
                "PresignPutParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                "自动生成键名、规范扩展名，签发直传链接。",
                "增加上传完成回执与 head 校验，确认大小和真实类型。",
                "签发成功不等于文件已上传。",
                "ready",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "自动生成日期/UUID 键名和扩展名，提供直传信息。",
                "签发链接不代表上传成功，尚无完成回执与真实类型校验。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                        61,
                        "case \"objects.presign_put\""))))
        .build();
  }

  private static BuiltinOperation objectsUpload() {
    return operation("nas.objects.upload")
        .summary("统一文件代理上传")
        .description(
            "PUT 原始二进制文件流；filename/folder/key/bucket 位于 query，Content-Type 为文件 MIME，必须提供准确 Content-Length，不支持 chunked。独立高危操作，必须精确授权 nas.objects.upload；presign_put 或通配授权不能替代。不重试、不幂等重放。默认最大 512MiB、总超时 300 秒，分别由 NAS_UPLOAD_MAX_BYTES、NAS_UPLOAD_TIMEOUT_S 配置。业务系统 Key 仅保存在其后端。")
        .quota("60/min")
        .sensitive(true)
        .timeout(300)
        .retries(1)
        .category("对象")
        .order(31)
        .cost("none")
        .tags("write", "danger", "raw_binary")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "required",
                array("filename"),
                "properties",
                object(
                    "filename",
                        object(
                            "type",
                            "string",
                            "minLength",
                            1,
                            "maxLength",
                            200,
                            "description",
                            "文件名；未提供 key 时须包含扩展名"),
                    "folder",
                        object(
                            "type",
                            "string",
                            "default",
                            "",
                            "maxLength",
                            500,
                            "description",
                            "自动生成对象键时使用的目录，默认空"),
                    "key",
                        object(
                            "type",
                            "string",
                            "maxLength",
                            500,
                            "description",
                            "可选完整对象键；提供时优先于 folder"),
                    "bucket",
                        object(
                            "type",
                            "string",
                            "maxLength",
                            63,
                            "description",
                            "可选桶名，默认 NAS_MINIO_BUCKET"))))
        .response(
            object(
                "type",
                "object",
                "required",
                array("bucket", "key", "size_bytes", "content_type", "etag"),
                "properties",
                object(
                    "bucket", object("type", "string", "description", "实际上传桶名"),
                    "key", object("type", "string", "description", "实际完整对象键"),
                    "size_bytes", object("type", "integer", "description", "经 EOF 核对的文件字节数"),
                    "content_type", object("type", "string", "description", "文件 MIME 类型"),
                    "etag", object("type", "string", "description", "S3 返回的 ETag，不保证是内容 MD5"),
                    "_mock", object("type", "boolean", "description", "仅模拟上传时为 true"))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                "通过统一网关流式上传到 NAS，核对末块与 EOF 后返回元信息。",
                "业务侧按文件用途校验内容。",
                "不扫描文件内容；失败不自动重试。",
                "ready",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "独立授权、限额、长度校验与流式代理。",
                "业务文件校验由调用方负责。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                        211,
                        "public Object upload("))))
        .build();
  }

  private static BuiltinOperation objectsReadHead() {
    return operation("nas.objects.read_head")
        .summary("读文件头（识别真实类型 / 图片尺寸）")
        .description(
            "Range GET 前 N 字节（默认 64KB）返回 base64，用于识别真实文件类型、探图片宽高比。NAS 上 .jpg 实为 webp 的情况确实存在。")
        .quota("120/min")
        .timeout(60)
        .retries(3)
        .since("2026-10")
        .category("对象")
        .order(50)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "key",
                    object(
                        "description",
                        "对象键（bucket 内路径）",
                        "maxLength",
                        500,
                        "minLength",
                        1,
                        "title",
                        "Key",
                        "type",
                        "string"),
                    "head_bytes",
                    object(
                        "default",
                        65536,
                        "description",
                        "读前多少字节；探图片尺寸 64KB 足够（JPEG 要跳过 EXIF 段）",
                        "maximum",
                        1048576,
                        "minimum",
                        1,
                        "title",
                        "Head Bytes",
                        "type",
                        "integer"),
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
                array("key"),
                "title",
                "ReadHeadParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                "读取有限字节并返回 Base64 与上游 Content-Type；未实现自动类型识别或图片尺寸解析。",
                "补 magic bytes 和图片尺寸解析，再与上传回执结合形成通用文件验收。",
                "当前文件头的业务解析仍由调用方完成。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "读取有界文件头，按签名识别常见图片/PDF/ZIP、解析图片尺寸并提示 MIME 不一致。",
                "仅检查文件头，不验证完整性、恶意内容或压缩包中的实际类型；较长 JPEG 头可能需要增加 head_bytes。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                        111,
                        "case \"objects.read_head\""),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/nas/FileHeaderInspector.java",
                        14,
                        "public static Map<String, Object> inspect("))))
        .build();
  }

  private static BuiltinOperation objectsStat() {
    return operation("nas.objects.stat")
        .summary("查对象元信息")
        .description("返回大小、MIME、ETag、最后修改时间；对象不存在返回 404。")
        .quota("300/min")
        .retries(3)
        .since("2026-10")
        .category("对象")
        .order(40)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "key",
                    object(
                        "description",
                        "对象键（bucket 内路径）",
                        "maxLength",
                        500,
                        "minLength",
                        1,
                        "title",
                        "Key",
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
                array("key"),
                "title",
                "ObjectParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                "整理对象大小、MIME、etag 和更新时间。",
                "作为上传后验证与下载前检查的基础。",
                "",
                "ready",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "把 HEAD 结果整理为大小、MIME、etag 和修改时间。",
                "仍是元信息单项读取，不验证业务上传是否完成或内容是否合规。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java",
                        94,
                        "case \"objects.stat\""))))
        .build();
  }

  private static BuiltinOperation objectsCollect() {
    return operation("nas.objects.collect")
        .summary("自动分页汇总：列目录 / 列举对象")
        .description(
            "自动分页、统一 items/page/errors；支持 max_items、max_pages 与签名续传游标。仅扫描根记录，嵌套资源遵循原接口的范围。读取能力独立授权，保留原接口兼容。")
        .quota("30/min")
        .timeout(90)
        .category("业务聚合")
        .order(-50)
        .sourceOperation("nas.objects.list")
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
                        "只看这个前缀下的对象，如 产品培训/",
                        "maxLength",
                        500,
                        "title",
                        "Prefix",
                        "type",
                        "string"),
                    "recursive",
                    object(
                        "default",
                        true,
                        "description",
                        "true=递归列出全部；false=只看当前一层",
                        "title",
                        "Recursive",
                        "type",
                        "boolean"),
                    "delimiter",
                    object(
                        "anyOf",
                        array(object("maxLength", 8, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "按分隔符折叠（常用 /），只看某一层的目录与文件；与 recursive=true 互斥",
                        "title",
                        "Delimiter"),
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
                "保留目录前缀，但不形成迁移/验收作业。",
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
