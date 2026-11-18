# NAS 统一文件代理上传

业务后端可通过 Common Java 网关上传到现有 HTTP MinIO。HTTPS 页面不再直接请求 HTTP NAS；页面应调用自己的业务后端，由其持有 Common API Key 并转交文件。网盘后台的代理开关不控制此接口。既有 `presign-get`、`presign-put` 保持原行为。

## 请求与授权

`PUT /integrations/nas/objects/upload?filename=file.bin&folder=uploads`

- `filename`：必填，1–200 字符；没有显式 `key` 时须有扩展名。
- `folder`：默认空，用于生成 `folder/YYYY-MM-DD/UUID.ext`。
- `key`：可选完整对象键；提供时优先于 folder。禁止绝对路径、越级路径和控制字符，最多 500 UTF-8 字节。
- `bucket`：可选，默认 `NAS_MINIO_BUCKET`，再回落到 `cyt-training`。
- `Authorization: Bearer <业务系统 API Key>`；Key 仅存业务后端。
- `Content-Type`：文件 MIME，默认 `application/octet-stream`。
- `Content-Length`：准确的文件字节数，允许 0。不支持 Transfer-Encoding/chunked。
- body：原始文件二进制；不支持 JSON、multipart/form-data、表单编码。

必须为调用方单独精确 allow `nas.objects.upload`，敏感授权需管理员确认。`nas.*`、`nas.objects.*`、`*` 和 `nas.objects.presign_put` 均不能代替；任意匹配的 deny 优先。不会给既有系统自动授予上传能力。有效权限、矩阵和接入包沿用同一决策规则。

```sh
curl --request PUT 'https://gateway.example.com/integrations/nas/objects/upload?filename=file.bin&folder=uploads' \
  --header "Authorization: Bearer ${CYT_API_KEY}" \
  --header 'Content-Type: application/octet-stream' \
  --data-binary '@file.bin'
```

本地常规文件的 curl `--data-binary @文件` 会提供长度。SDK 在业务后端使用：

```python
import os
from cyt_gateway import Gateway

gateway = Gateway('https://gateway.example.com', os.environ['CYT_API_KEY'])
result = gateway.upload_file('file.bin', folder='uploads')
```

TypeScript SDK `uploadFile(stream, {filename, contentLength, contentType, folder, key, bucket})` 接收 `ReadableStream<Uint8Array>`；Node 可用 `Readable.toWeb(createReadStream(path))`，配合文件 stat 得到长度。SDK 不自动重试或跟随重定向。

成功仍返回 `ok/data/meta`，data 含 `bucket/key/size_bytes/content_type/etag`；ETag 不保证是 MD5。只认可 NAS 2xx。`meta.mock=true` 和 `data._mock=true` 明确表示模拟。

## 配置与传输边界

继续使用 `NAS_MINIO_ENDPOINT/ACCESS_KEY/SECRET_KEY/REGION/BUCKET`，通过现有 CredentialService 按业务系统解析凭据。可按系统配置 `NAS_MINIO_{SYSTEM}_ACCESS_KEY/SECRET_KEY`；采用 `CRED_SCOPE_NAS=strict` 时缺失专属凭据拒绝调用，不借用共享凭据。

- `NAS_UPLOAD_MAX_BYTES=536870912`：默认 512 MiB。
- `NAS_UPLOAD_TIMEOUT_S=300`：总超时以及上游 IO 超时，支持秒的小数，最大 86400 秒。
- 只有显式 `MOCK_ALL=true` 或 `NAS_MOCK=true` 才允许模拟上传；模拟仍消费文件并检查长度。未配置凭据的真实模式返回 503。生产环境继续禁止模拟。

鉴权、精确授权、系统与平台配额及参数校验都先于读取文件。上传源显式传递到虚拟线程，不依赖 ThreadLocal。每次读取/写入最多 64 KiB，保留最后不超过 64 KiB，正常 EOF 且长度一致后才向 NAS 发送末块；空文件在发起 NAS 请求前确认 EOF。文件体不会进入 JSON、幂等指纹或审计日志。内部使用 SigV4 Authorization 请求头和 `UNSIGNED-PAYLOAD`，签名包含 Host（含端口）、Content-Length 和 Content-Type，不使用内部预签名 query URL。

JSON invoke、批量调用与管理 JSON 调试不能执行上传；管理调试台展示调用边界并禁用执行。OpenAPI、在线下载文档、调用方接入包均标记 PUT raw binary，参数在 query，独立授权决定是否导出。

HTTP 错误：401 身份失败；403 未授权；411 缺长度或使用 Transfer-Encoding；413 声明超过上限；422 参数、长度不符或客户端断流；429 配额耗尽；502 NAS 非 2xx/连接错误；503 凭据缺失；504 总超时或上游 IO 超时。错误、异常和审计不回显 S3 错误正文、凭据、签名或 canonical request。

非法 HTTP 分帧或客户端提前断开可能由 Tomcat 在 MVC 响应前接管，此时客户端可能收到容器 400 或已无法接收响应；应用可捕获的输入流错误仍按 422 记录，失败没有成功结果或成功审计。

上传不重试、不进行幂等重放。若 NAS 已接收完整对象但响应在网络上丢失，网关不能确定对象状态；调用方应以已知 key 使用 stat 核对后再决定后续处理。末块策略防止长度错误导致完整 PUT 提交，但不提供事务回滚、内容扫描或真实 NAS 验收。

## 本地验证

在 `CYT-Common-API` 运行 `mvn -Dtest=NasUploadTest,NativeAdapterTest,BuiltinCatalogTest,IntegrationGuideTest test`。测试使用合成字节、模拟存储和回环 HTTP S3，不连接生产数据库或真实 NAS。另运行前端构建及 `tests/nas-upload.test.mjs`，SDK 测试位于 `sdk/python/test_nas_upload.py` 和 `sdk/typescript/test_nas_upload.mjs`。

部署时还需由运维协调业务后端、反向代理的请求大小和超时设置；本次代码实现与验证不包含部署或生产写入。

## 本轮文件清单与验证结果（2026-11-17）

以下文件只在原有工作区基础上增加上传相关改动；不表示文件中的其他未提交内容由本轮产生。

- `CYT-Common-API/src/main/java/group/cyt/common/core/http/BinaryUpload.java`
- `CYT-Common-API/src/main/java/group/cyt/common/core/http/BinaryUploadFilter.java`
- `CYT-Common-API/src/main/java/group/cyt/common/core/http/UpstreamHttp.java`
- `CYT-Common-API/src/main/java/group/cyt/common/module/adapter/AdapterDispatcher.java`
- `CYT-Common-API/src/main/java/group/cyt/common/module/adapter/nas/NasAdapter.java`
- `CYT-Common-API/src/main/java/group/cyt/common/module/gateway/controller/IntegrationController.java`
- `CYT-Common-API/src/main/java/group/cyt/common/module/gateway/service/GatewayExecutor.java`
- `CYT-Common-API/src/main/java/group/cyt/common/module/registry/catalog/platform/NasCatalog.java`
- `CYT-Common-API/src/main/java/group/cyt/common/module/registry/model/OperationDefinition.java`
- `CYT-Common-API/src/main/java/group/cyt/common/module/registry/controller/RegistryController.java`
- `CYT-Common-API/src/main/java/group/cyt/common/module/registry/controller/ConsumerCapabilitiesController.java`
- `CYT-Common-API/src/main/resources/templates/integration-guide.html`
- `CYT-Common-API/src/test/java/group/cyt/common/NasUploadTest.java`
- `CYT-Common-API/src/test/resources/operation-cases.json`
- `CYT-Common-API/application-local.properties.example`
- `CYT-Common-Frontend/src/api/types.ts`
- `CYT-Common-Frontend/src/pages/Docs.vue`
- `CYT-Common-Frontend/src/pages/Registry.vue`
- `CYT-Common-Frontend/src/pages/ExposedCapabilities.vue`
- `CYT-Common-Frontend/src/pages/Playground.vue`
- `CYT-Common-Frontend/src/utils/documentation.ts`
- `CYT-Common-Frontend/tests/nas-upload.test.mjs`
- `sdk/python/cyt_gateway.py`
- `sdk/python/test_nas_upload.py`
- `sdk/typescript/index.ts`
- `sdk/typescript/test_nas_upload.mjs`
- `README.md`
- `docs/nas-upload.md`

实际执行：

- `CYT-Common-API`：`mvn -q -Dtest=NasUploadTest,OperationContractTest,GatewayCoreTest,CredentialServiceTest,BuiltinCatalogTest,IntegrationGuideTest,NativeAdapterTest#nasPresignedUrlRetainsProxyPrefixAndEncodesUnicodeOnce test`：246 项通过，0 失败、0 错误，其中 NasUploadTest 47 项。
- `CYT-Common-API`：扩大回归 `mvn -q -Dtest=!GatewayIntegrationTest test` 当次运行 388 项，387 通过；`NativeAdapterTest.lingxingMailKeepsRequestedDateRangeInsteadOfExpandingItToAYear` 失败，未修改领星模块。本轮未运行需要真实 PostgreSQL 的 GatewayIntegrationTest。
- `CYT-Common-Frontend`：`node --experimental-strip-types --test tests/*.test.mjs`：32 项通过；`npm.cmd run build`：类型检查与生产构建通过。
- `CYT-Common-Frontend`：`node node_modules/typescript/bin/tsc --noEmit --target ES2022 --module ESNext --moduleResolution bundler --lib ES2022,DOM --skipLibCheck ../sdk/typescript/index.ts`：通过。
- `sdk/python`：`python -m unittest -v test_nas_upload`：2 项通过。
- 项目根目录：`node --test sdk/typescript/test_nas_upload.mjs`：1 项通过。

回环测试包含真实 HTTP 请求穿过 Servlet、AuthenticationService、GatewayExecutor 虚拟线程、SigV4 和模拟 S3；核对文件字节一致、空文件、chunked 拒绝、原始 TCP 提前断流的失败审计与无完整对象提交。MVC 过滤链测试同时覆盖编码路径、matrix 参数和 context path 下的普通/MIME/表单拒绝行为。下载接入包的 OpenAPI、离线指南及内置 SDK 也已检查。

尚未验证真实 MinIO/NAS 账号、生产 TLS/反向代理及生产吞吐；未部署、未迁移数据库、未写真实 NAS、未重启既有服务、未提交工作区改动。已保留其他会话的未提交内容。
