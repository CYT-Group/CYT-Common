# 群晖 NAS 接口文档

业务系统通过 Common API 访问群晖上的 MinIO。本文覆盖网关的 `nas.*` 操作，包括在 MinIO 桶内批量创建多级目录，以及群晖 File Station 公开 API 的调用方式。

## Common API 快速接入

- `COMMON_API_BASE` 使用分配给业务系统的网关地址，本地 Java 服务默认为 `http://127.0.0.1:8189`。
- 业务后端通过 `Authorization: Bearer <业务系统 API Key>` 鉴权。网关按操作授权并解析调用方的 MinIO 凭据。`nas.objects.upload` 要求精确的 allow 规则。
- 除代理上传使用原始二进制 PUT，其余操作均使用 POST 和 `Content-Type: application/json`。请求体直接传业务参数，无参数时传 `{}`。
- 对象接口的桶参数省略时使用调用方的 `NAS_MINIO_BUCKET`，再回落到 `cyt-training`。复制对象使用 `source_bucket` 和 `dest_bucket`，其余对象接口使用 `bucket`。`key` 相对于桶根，最长 500 UTF-8 字节，不允许以 `/` 开头、包含 `..` 路径段或控制字符。
- 各接口列出的配额为目录默认值。实际调用还受调用方授权和平台配额限制。

```bash
curl --fail-with-body --request POST \
  "$COMMON_API_BASE/integrations/nas/objects/list" \
  --header "Authorization: Bearer ${CYT_API_KEY}" \
  --header 'Content-Type: application/json' \
  --data '{"bucket":"example-docs","prefix":"projects/","recursive":false,"max_keys":50}'
```

```json
{
  "ok": true,
  "data": {
    "bucket": "example-docs",
    "items": [],
    "prefixes": ["projects/demo/"],
    "count": 0,
    "truncated": false,
    "next_token": null
  },
  "meta": {"trace_id": "example-trace", "platform": "nas", "mock": false}
}
```

失败时返回 `ok=false` 和 `error`，包括 `type`、`message`、`retryable`，适用时另含 `code` 和 `trace_id`。常见 HTTP 状态包括 `401` 鉴权失败、`403` 未授权、`404` 资源不存在、`422` 参数校验失败、`429` 配额耗尽、`502` 上游调用失败、`503` 配置缺失和 `504` 上游超时。排障时保留 `trace_id`。遇到写入超时，先核对目标对象再决定是否重试。

## 桶接口

### 列出可访问的桶

`POST /integrations/nas/buckets/list` · `nas.buckets.list` · `120/min`

- 请求 `{}`。
- `data` 返回 `buckets[]` 和 `count`。每个桶含 `name`、`creation_date`，可见范围由 MinIO 凭据权限决定。

### 新建桶

`POST /integrations/nas/buckets/create` · `nas.buckets.create` · `10/min` · 敏感写入

- 必填 `bucket`。长度 3–63，仅小写字母、数字、点、连字符，首尾为字母或数字，并须通过 S3 桶名校验。
- 可选 `region`，默认使用配置的 `NAS_MINIO_REGION`，再回落到 `us-east-1`。
- 请求示例 `{"bucket":"example-docs","region":"us-east-1"}`。
- `data` 返回 `bucket`、`created`、`region`。

### 删除空桶

`POST /integrations/nas/buckets/delete` · `nas.buckets.delete` · `10/min` · 敏感写入

- 必填 `bucket`。上游只允许删除空桶。
- 请求示例 `{"bucket":"example-empty"}`。
- `data` 返回 `bucket`、`deleted`。

## 对象与目录查询

### 列目录或对象

`POST /integrations/nas/objects/list` · `nas.objects.list` · `30/min`

- 可选 `prefix`（默认空）、`recursive`（默认 `true`）、`delimiter`、`max_keys`（1–1000，默认 1000）、`continuation_token`、`bucket`。
- 查询单层时设置 `recursive=false`，默认用 `/` 折叠目录。`delimiter` 仅在 `recursive=false` 时生效。
- 请求示例 `{"bucket":"example-docs","prefix":"projects/","recursive":false,"max_keys":50}`。
- `data` 返回 `bucket`、`items[]`、`prefixes[]`、`count`、`truncated`、`next_token`。每个对象含 `key`、`size_bytes`、`last_modified`、`etag`。`count` 是本页对象数。`truncated=true` 时将 `next_token` 传入下一次请求的 `continuation_token`。

### 自动分页汇总

`POST /integrations/nas/objects/collect` · `nas.objects.collect` · `30/min`

- 可选 `bucket`、`prefix`、`recursive`、`delimiter`，语义同列表接口。
- 可选 `page_size`（1–1000，默认 50）、`max_items`（1–1000，默认 100）、`max_pages`（1–100，默认 10）和 `resume_cursor`。单层查询时，目录和对象共同计入 `max_items`。
- 请求示例 `{"bucket":"example-docs","prefix":"projects/","max_items":100}`。
- `data` 含 `items[]`、`folders[]`、`errors[]`、`partial` 和 `page`。`page` 含 `has_more`、`next_cursor`、`count`、`pages`。仍有结果时将 `page.next_cursor` 作为 `resume_cursor` 并保持调用系统、筛选参数和 `page_size` 一致。

### 查询对象元信息

`POST /integrations/nas/objects/stat` · `nas.objects.stat` · `300/min`

- 必填 `key`，可选 `bucket`。
- 请求示例 `{"bucket":"example-docs","key":"projects/demo/plan.pdf"}`。
- `data` 返回 `bucket`、`key`、`size_bytes`、`content_type`、`etag`、`last_modified`。对象不存在时返回 `404`。

### 读取文件头

`POST /integrations/nas/objects/read-head` · `nas.objects.read_head` · `120/min`

- 必填 `key`，可选 `bucket` 和 `head_bytes`（1–1,048,576，默认 65,536）。
- 请求示例 `{"bucket":"example-docs","key":"projects/demo/plan.pdf","head_bytes":4096}`。
- `data` 返回 `bucket`、`key`、`size_bytes`、`returned_bytes`、`content_type`、`content_base64` 和 `inspection`。`inspection` 按实际读取的文件头识别格式及可解析的图片尺寸。

## 下载与上传

### 批量签发读取链接

`POST /integrations/nas/objects/presign-get` · `nas.objects.presign_get` · `300/min`

- 必填 `keys`，数组长度 1–200。可选 `bucket` 和 `expires`（60–86,400 秒，默认 3,600 秒）。
- 请求示例 `{"bucket":"example-docs","keys":["projects/demo/plan.pdf","projects/demo/notes.txt"],"expires":600}`。
- `data` 返回 `bucket`、`items[]`、`count`、`expires_in`。每个 `item` 含 `key` 和临时 `url`。签名阶段不会查询对象是否存在，下载以实际 GET 结果为准。

### 签发上传链接

`POST /integrations/nas/objects/presign-put` · `nas.objects.presign_put` · `60/min` · 敏感写入

- 必填 `filename`（1–200 字符），可选 `folder`（最长 200 字符）、`key`、`bucket` 和 `expires`（60–3,600 秒，默认 900 秒）。
- `key` 指定完整对象键。省略时按目录、UTC 日期、UUID 和文件扩展名生成，`filename` 须含扩展名。
- 请求示例 `{"bucket":"example-docs","filename":"plan.pdf","key":"projects/demo/plan.pdf"}`。
- `data` 返回 `bucket`、`key`、`upload_url`、`method`、`content_type`、`expires_in`。将文件原始字节 PUT 到 `upload_url`，以该次 PUT 的结果判断是否上传成功。

### 通过网关代理上传

`PUT /integrations/nas/objects/upload` · `nas.objects.upload` · `60/min` · 精确授权

- Query 必填 `filename`（1–200 字符），可选 `folder`、`key`、`bucket`。`key` 优先。省略时按目录、UTC 日期、UUID 和文件扩展名生成。
- Body 为原始文件字节，请求需提供准确的 `Content-Length`，允许 `0`。`Content-Type` 默认 `application/octet-stream`。默认最大 512 MiB，总超时 300 秒，可由网关配置调整。
- `data` 返回 `bucket`、`key`、`size_bytes`、`content_type`、`etag`。上传不支持 JSON 批量调用，也不自动重试或重放。详情见 [NAS 统一文件代理上传](nas-upload.md)。

```bash
curl --fail-with-body --request PUT \
  "$COMMON_API_BASE/integrations/nas/objects/upload?bucket=example-docs&filename=plan.pdf&key=projects%2Fdemo%2Fplan.pdf" \
  --header "Authorization: Bearer ${CYT_API_KEY}" \
  --header 'Content-Type: application/pdf' \
  --data-binary @plan.pdf
```

## 复制、移动与删除

### 复制对象

`POST /integrations/nas/objects/copy` · `nas.objects.copy` · `30/min`

- 必填 `source_key`、`dest_key`。可选 `source_bucket`、`dest_bucket`，省略时各自使用配置的默认桶。
- 请求示例 `{"source_bucket":"example-docs","source_key":"projects/demo/plan.pdf","dest_bucket":"example-archive","dest_key":"demo/plan.pdf"}`。
- `data` 返回 `source`、`bucket`、`key`、`copied`、`etag`。上游在存储端执行复制，单对象上限 5 GiB。

### 按前缀复制或移动目录

`POST /integrations/nas/objects/copy-prefix` · `nas.objects.copy_prefix` · `20/min` · 敏感写入

- 必填 `source_prefix`、`dest_prefix`，可选 `bucket`、`delete_source`（默认 `false`）。目标前缀不能等于源前缀或位于其下。单次最多处理 50,000 个对象。
- 请求示例 `{"bucket":"example-docs","source_prefix":"projects/demo/","dest_prefix":"archive/demo/","delete_source":false}`。
- `delete_source=true` 时，所有对象复制成功后再批量删除源对象。操作可能部分完成，调用方需核对 `failed` 和计数。
- `data` 返回 `bucket`、`source_prefix`、`dest_prefix`、`total`、`copied`、`deleted`、`failed[]`。

### 删除对象

`POST /integrations/nas/objects/delete` · `nas.objects.delete` · `60/min` · 敏感写入

- 必填 `key`，可选 `bucket`。删除操作没有网关回收站。
- 请求示例 `{"bucket":"example-docs","key":"projects/demo/obsolete.txt"}`。
- `data` 返回 `bucket`、`key`、`deleted`。对象本来就不存在时，上游接受删除的结果同样可为成功。

### 批量删除对象

`POST /integrations/nas/objects/delete-batch` · `nas.objects.delete_batch` · `60/min` · 敏感写入

- 必填 `keys`，数组长度 1–200。可选 `bucket`。删除目录前，先按前缀列举全部目标键，包括存在的目录标记。
- 请求示例 `{"bucket":"example-docs","keys":["projects/demo/old-notes.txt","projects/demo/obsolete.txt"]}`。
- `data` 返回 `bucket`、`deleted[]`、`errors[]`、`count`。每条错误含 `key`、`code`、`message`。即使外层 `ok=true`，仍须检查 `errors`。

## 通用 JSON 批量调用

`POST /integrations/batch` 接受 1–100 项 `calls`，每项含 `operation` 和 `params`。`stop_on_error` 默认 `false`。每项单独执行授权与配额检查，批次没有事务回滚。结果见 `data.results[]`，并返回 `batch_id`、`total`、`succeeded`、`failed`。

```json
{
  "calls": [
    {"operation": "nas.objects.stat", "params": {"bucket": "example-docs", "key": "projects/demo/plan.pdf"}},
    {"operation": "nas.objects.list", "params": {"bucket": "example-docs", "prefix": "projects/", "recursive": false}}
  ],
  "stop_on_error": false
}
```

## 批量建目录

### MinIO 桶内的目录

`POST /integrations/nas/folders/create-batch` · `nas.folders.create_batch` · `20/min` · 精确授权

- `bucket` 可选，默认使用调用方的配置。MinIO 凭据需具备目标目录的 `s3:ListBucket` 和 `s3:PutObject` 权限。
- `base_prefix` 可选，指定相对桶根的目标前缀。省略时在桶根创建。网关只在该前缀下创建目录标记。
- `paths` 必填，传 1–100 条相对路径，用 `/` 表示层级。支持中文、空格和可选末尾斜杠。网关先校验整个请求，再去重并补齐父目录。补齐后最多 100 个目录，完整目录键包含末尾斜杠最多 500 UTF-8 字节。空路径段、`.`、`..`、反斜杠和控制字符会导致请求返回 `422`。
- `data.items[]` 按父目录优先返回，每项包含完整目录 `key` 和 `status`。`created` 表示已创建。`exists` 表示跳过并保留已有数据，包括已有文件的目录和已有目录标记。`failed` 附带 `error.code`、`error.message` 和 `error.retryable`。父目录失败时，其后代返回 `PARENT_FOLDER_FAILED`，独立分支继续处理。
- `data.total`、`created`、`exists`、`failed` 按去重后的目录计数。应检查 `data.failed` 判断是否全部完成。重试时可重新提交原请求，已完成的目录会返回 `exists`。并发创建同名标记时使用 MinIO 条件写入保留已有内容。 [MinIO S3 条件请求支持](https://docs.min.io/aistor/developers/s3-api-compatibility/#conditional-headers)

以下以一套产品素材目录为例，只提交末级路径即可建出 21 个目录。

```bash
curl --fail-with-body --request POST \
  "$COMMON_API_BASE/integrations/nas/folders/create-batch" \
  --header "Authorization: Bearer ${CYT_API_KEY}" \
  --header 'Content-Type: application/json' \
  --data '{
    "base_prefix": "products/SKU-001",
    "paths": [
      "产品图片/拍摄白底图",
      "产品图片/拍摄社交图",
      "产品图片/渲染白底图",
      "产品图片/渲染社交图",
      "产品视频/竖视频/ASMR纯享视频",
      "产品视频/竖视频/开箱视频",
      "产品视频/竖视频/how to use 视频",
      "产品视频/横视频/ASMR纯享视频",
      "产品视频/横视频/开箱视频",
      "产品视频/横视频/how to use 视频",
      "产品视频/横视频/品宣视频",
      "产品设计图/亚马逊平台页面设计",
      "产品设计图/其他平台页面设计",
      "产品设计图/独立站页面设计",
      "产品设计图/国内页面设计",
      "产品设计图/海报设计"
    ]
  }'
```

调用后可用 `nas.objects.list` 的 `recursive=false` 查看父前缀下的 `prefixes`。

### DSM 共享文件夹内的目录

`SYNO.FileStation.CreateFolder` 的 `create` 方法支持一次创建多个目录。`folder_path` 和 `name` 均以 JSON 数组编码为表单字段，长度必须相等，按下标配对。同一个父目录创建多个子目录时，重复填入父路径。`force_parent` 默认 `false`。设为 `true` 可补建缺失的父目录，并容许目录已存在。成功读取 `data.folders[]`，失败查看 `error.code` 和可能返回的 `error.errors[]`。 [File Station API Guide，CreateFolder](https://global.download.synology.com/download/Document/Software/DeveloperGuide/Package/FileStation/All/enu/Synology_File_Station_API_Guide.pdf#page=81)

```bash
curl --fail-with-body --request POST \
  "$SYNO_BASE/webapi/$SYNO_CREATE_FOLDER_CGI" \
  --data-urlencode 'api=SYNO.FileStation.CreateFolder' \
  --data-urlencode 'version=2' \
  --data-urlencode 'method=create' \
  --data-urlencode "_sid=$SYNO_SID" \
  --data-urlencode 'folder_path=["/projects","/projects","/archive/2026"]' \
  --data-urlencode 'name=["design","meeting-notes","handover"]' \
  --data-urlencode 'force_parent=true'
```

示例将创建 `/projects/design`、`/projects/meeting-notes`、`/archive/2026/handover`。`/projects` 和 `/archive` 是示例共享文件夹，账号须拥有相应写权限。`SYNO_CREATE_FOLDER_CGI` 取目标设备发现结果中的 `path`，例如 `entry.cgi`。需要在网关统一对业务系统开放时，须先接入 File Station 平台或适配器并配置独立授权。

```json
{
  "success": true,
  "data": {
    "folders": [
      {"path": "/projects/design", "name": "design", "isdir": true},
      {"path": "/projects/meeting-notes", "name": "meeting-notes", "isdir": true},
      {"path": "/archive/2026/handover", "name": "handover", "isdir": true}
    ]
  }
}
```

## 群晖 File Station 原生接口索引

### 发现、认证与响应

1. 请求 `GET /webapi/query.cgi?api=SYNO.API.Info&version=1&method=query&query=all`，读取目标 API 的 `path`、`minVersion`、`maxVersion`。
2. 向发现得到的 `SYNO.API.Auth` 路径调用 `login`，用 `account`、`passwd`、`session=FileStation`、`format=sid` 换取 `data.sid`。启用双重验证时，按设备支持版本提供相应验证参数。
3. 请求 `/webapi/<path>`，传 `api`、`version`、`method` 和相应业务字段。会话使用 `_sid`，或使用登录协商的 Cookie。示例版本需处于目标设备公布的区间内。
4. JSON 操作检查 `success`，成功数据通常在 `data`，失败看 `error.code` 和可能出现的逐项 `error.errors[]`。上传使用 multipart 表单，下载和缩略图成功时返回二进制。使用完毕调用 `SYNO.API.Auth` 的 `logout`，传同一 `session` 和会话凭据。

基础 API 的完整会话与错误码说明见 [群晖 File Station API Guide](https://global.download.synology.com/download/Document/Software/DeveloperGuide/Package/FileStation/All/enu/Synology_File_Station_API_Guide.pdf#page=16) 和 [DSM Login Web API Guide](https://kb.synology.com/en-us/DG/DSM_Login_Web_API_Guide/2)。以下版本为 File Station 指南所列的版本，字段为各方法关键参数，完整可选字段与可用版本见各组链接。

### 浏览与查询

- `SYNO.FileStation.Info` v2 · `get` 无业务参数，返回 NAS 主机名和当前账户的功能能力。
- `SYNO.FileStation.List` v2 · `list_share` 用 `offset`、`limit`、`onlywritable` 列出共享文件夹，返回 `shares[]`。`list` 用 `folder_path`、`offset`、`limit`、`pattern`、`additional` 列目录，返回 `files[]`。`getinfo` 用 `path`、`additional` 查元信息，返回 `files[]`。
- `SYNO.FileStation.Search` v2 · `start` 用 `folder_path` 及可选 `recursive`、`pattern`、`extension`、`filetype` 等条件发起搜索，返回 `taskid`。`list` 用 `taskid` 和分页参数查询 `files[]`、`finished`。`stop`、`clean` 用 `taskid` 停止任务或清理结果。
- `SYNO.FileStation.VirtualFolder` v2 · `list` 按 `type` 和分页参数查询挂载目录，返回 `folders[]`。
- `SYNO.FileStation.Favorite` v2 · `list` 按 `offset`、`limit`、`status_filter` 查 `favorites[]`。`add` 用 `path`、`name`、可选 `index`，`delete` 用 `path`，`edit` 用 `path`、`name`，`clear_broken` 清理失效收藏，`replace_all` 用配对的 `path`、`name` 替换收藏。

[File Station API Guide，浏览与收藏](https://global.download.synology.com/download/Document/Software/DeveloperGuide/Package/FileStation/All/enu/Synology_File_Station_API_Guide.pdf#page=22)

### 文件信息、权限与传输

- `SYNO.FileStation.Thumb` v2 · `get` 用 `path`、`size`、`rotate` 获取二进制缩略图。
- `SYNO.FileStation.DirSize` v2 · `start(path)` 返回 `taskid`。`status(taskid)` 返回 `finished`、`num_dir`、`num_file`、`total_size`。`stop(taskid)` 停止任务。
- `SYNO.FileStation.MD5` v2 · `start(file_path)` 返回 `taskid`。`status(taskid)` 返回 `finished` 与 `md5`。`stop(taskid)` 停止任务。
- `SYNO.FileStation.CheckPermission` v3 · `write` 用 `path`、`filename` 及可选 `overwrite`、`create_only` 检查能否写入。
- `SYNO.FileStation.Upload` v2 · `upload` 用 multipart 表单发送 `path`、可选 `create_parents`、`overwrite`，`file` 文件字段放在其他字段之后。检查 JSON `success` 和 `data.files[]`。
- `SYNO.FileStation.Download` v2 · `download` 用 `path` 和可选 `mode` 下载文件，成功返回文件流或归档流。
- `SYNO.FileStation.Sharing` v3 · `getinfo(id)` 查询分享链接；`list` 按分页返回 `links[]`；`create` 用 `path`、可选密码与生效日期返回 `links[]`；`delete(id)`、`edit(id)` 删除或更新指定分享；`clear_invalid` 清理无效分享。

[File Station API Guide，文件、传输与分享](https://global.download.synology.com/download/Document/Software/DeveloperGuide/Package/FileStation/All/enu/Synology_File_Station_API_Guide.pdf#page=58)

### 目录、文件变更与任务

- `SYNO.FileStation.CreateFolder` v2 · `create` 用配对的 `folder_path` 和 `name` 创建目录，返回 `folders[]`。参见上面的批量示例。
- `SYNO.FileStation.Rename` v2 · `rename` 用配对的 `path` 和 `name`，可选 `additional`、`search_taskid`，返回 `files[]`。
- `SYNO.FileStation.CopyMove` v3 · `start` 用 `path`、`dest_folder_path`、可选 `overwrite` 和 `remove_src` 创建任务，`remove_src=true` 表示移动。`status(taskid)` 查询进度和 `finished`，`stop(taskid)` 停止任务。
- `SYNO.FileStation.Delete` v2 · `start` 用 `path`、可选 `recursive` 创建删除任务；`status(taskid)` 查进度，`stop(taskid)` 停止。`delete` 用 `path`、可选 `recursive` 同步删除。
- `SYNO.FileStation.Extract` v2 · `list` 用 `file_path` 和分页字段查看压缩包条目；`start` 用 `file_path`、`dest_folder_path`、可选 `password`、`item_id` 创建解压任务；`status(taskid)` 查结果，`stop(taskid)` 停止。
- `SYNO.FileStation.Compress` v3 · `start` 用 `path`、`dest_file_path`、可选 `level`、`mode`、`format`、`password` 启动压缩；`status(taskid)` 查任务结果，`stop(taskid)` 停止。
- `SYNO.FileStation.BackgroundTask` v3 · `list` 使用分页字段及可选 `api_filter` 返回 `tasks[]`；`clear_finished` 可传 `taskid` 清理已完成任务记录，省略时按 API 的默认范围清理。

[File Station API Guide，文件变更与后台任务](https://global.download.synology.com/download/Document/Software/DeveloperGuide/Package/FileStation/All/enu/Synology_File_Station_API_Guide.pdf#page=81)

## 契约与接入包

Common API 的 [内置接口契约](../CYT-Common-API/src/main/resources/contract.json) 和 [业务接口契约](../CYT-Common-API/src/main/resources/business-contract.json) 记录完整参数约束。业务系统可从管理台的“接入文档”导出本系统的 OpenAPI 与接入包，其中包含导出时获授权的接口、HTTP 示例和 SDK。网关调用示例也见 [Python 与 TypeScript SDK](../sdk/README.md)。
