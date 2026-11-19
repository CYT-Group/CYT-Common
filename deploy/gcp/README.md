# Google CLI 发布 Common Java

参考 CYT-OA 的发布流程：本地 Python 入口调用 Google CLI，由 Cloud Build 构建同源应用镜像及
独立 Flyway 镜像；Cloud Run Job 完成数据库升级后，才发布 Java Cloud Run 服务。
Vue 管理页面随 JAR 一起交付，无需另建前端站点。

```text
本地 SQL/参数校验 → 构建并推送 JAR 镜像 + SQL 镜像
                 → 本次 BUILD_ID 专属迁移 Job → 等待成功
                 → 无流量 revision → 校验镜像 digest / 构建时间 → etag 条件切流
```

这个入口仅发布独立 Java 服务，默认名称为 `cyt-common-java`；拒绝覆盖旧 Python 服务
`cyt-common-api`。数据复制和业务流量切换使用现有迁移流程，不随发布自动发生。

## 首次准备

准备独立 PostgreSQL 数据库、同区域 VPC 连接器和已授权的运行账号。数据库 Secret 存放
PostgreSQL JDBC URL，例如 `jdbc:postgresql://数据库私网地址:5432/cyt_common_java`；账号与口令分开保存。
迁移 Job 与 Java 服务使用同一组数据库 Secret，运行时统一注入 `SPRING_DATASOURCE_*`，
避免升级和运行指向不同库。发布时移除相关旧 `POSTGRES_*` 别名和可能覆盖生产配置的导入/JVM 参数；
更新 Secret 前先移除同名 literal，支持从旧明文环境配置升级为 Secret。

必须提供以下 Secret Manager 引用，格式为 `名称:版本`，支持固定版本或 `latest`：

- 数据库 JDBC URL、数据库用户名、数据库密码。
- `ADMIN_PASSWORD`、`ADMIN_JWT_SECRET`、`CREDENTIAL_MASTER_KEY`。
- 可选 `REDIS_URL`；指定多于一个实例时必须显式提供。

引用可沿用原服务已有的管理员及主密钥 Secret，迁移密文时必须保持原主密钥值。
主密钥和数据库数据应分别备份。真实值不通过命令行、源码或镜像传递。

Cloud Build 执行账号需要推送镜像、创建和执行 Cloud Run Job、更新 Java 服务，以及使用指定服务账号
的权限，还需读取当前构建的创建时间及服务/revision，切流使用 Cloud Run v2 API；
迁移和运行账号需要读取各自引用的 Secret，并通过 VPC 访问数据库。
默认仓库为 `gcr.io/项目`，也可指定已有 Artifact Registry 仓库。

生产可使用固定数字 Secret 版本，将迁移与应用绑定到同一配置版本；使用 `latest` 时，
同一次发布期间不要轮换数据库连接 Secret。

默认 `--vpc-egress all-traffic` 沿用 Common 原部署的 VPC/Cloud NAT 出口方式，适合供应商 IP 白名单。
须先准备 NAT 及联网路径；不需要固定公网出口时可显式使用 `--vpc-egress private-ranges-only`。

## Windows 使用

在仓库根目录先预览计划。以下仅为 Secret 名称示例，运行账号替换为实际已授权账号：

```powershell
python deploy/gcp/deploy.py `
  --project cyt-digital `
  --region us-central1 `
  --service cyt-common-java `
  --service-account common@cyt-digital.iam.gserviceaccount.com `
  --db-url-secret COMMON_JAVA_POSTGRES_URL:latest `
  --db-user-secret COMMON_JAVA_POSTGRES_USER:latest `
  --db-password-secret COMMON_JAVA_POSTGRES_PASSWORD:latest `
  --admin-password-secret COMMON_API-ADMIN_PASSWORD:latest `
  --admin-jwt-secret COMMON_API-ADMIN_JWT_SECRET:latest `
  --credential-master-key-secret COMMON_API-CREDENTIAL_MASTER_KEY:latest `
  --public `
  --dry-run
```

`--dry-run` 只校验本地 SQL 和参数并输出 Secret 引用与计划，不查账号、不调用 Google CLI、不连接数据库。
核对后去掉 `--dry-run` 才提交构建。Linux / Cloud Shell 使用相同参数。
Windows 自动调用 Google Cloud SDK 的 Python 入口，避免 `.cmd` 二次解释 JSON 或分隔符。

`--public` 允许浏览器或携带网关 API Key 的业务系统到达服务；管理登录和操作授权继续由应用执行。
省略时 Google IAM 会拒绝只携带网关 API Key 的请求；保留私有模式需要调用方另外具备 Cloud Run IAM 凭据，
现有 API Key 客户端不会自动补充这层认证。Google ID token 调用还需通过 `--env` 显式配置
`GATEWAY_AUDIENCE`（接收服务 URL）、服务账号白名单及 `GATEWAY_SA_SYSTEM_MAP`；入口不会自动推断这三项。
不能依赖旧 Python 网关按邮箱名称推断系统。

## 平台配置与运行方式

非敏感变量使用多个 `--env NAME=VALUE`，凭据使用多个 `--secret ENV_NAME=SECRET_NAME:VERSION`。
例如：

```text
--env CORS_ALLOW_ORIGINS=https://common.example.com,https://portal.example.com
--env WEWORK_PROXY_BASE=https://proxy.example.com
--secret WEWORK_PROXY_TOKEN=WEWORK_PROXY_TOKEN:latest
--redis-secret REDIS_URL:latest --max-instances 2
```

已有 Java 服务的无关环境变量、Secret 和存储挂载通过 `--update-env-vars` / `--update-secrets` 保留。
受入口管理的生产环境、数据库、profile、端口和 JVM 配置禁止通过额外参数覆盖；模拟、开发身份和演示种子关闭。
已有平台和资源级 `*_MOCK` 覆盖在发布时清除，统一回落到全局真实模式，避免保留旧测试开关导致生产启动失败。
省略可选 Redis 参数不会删除已有 Redis Secret。新服务默认单实例上限，避免进程内配额被多副本放大。

Discord REST 操作可按原网关契约使用。若需要在 Cloud Run 运行后台 Gateway 监听，显式增加
`--discord-listener`，同时配置 `DISCORD_ACCOUNTS`、`DISCORD_LISTENER_SYSTEMS_JSON` 和对应账号凭据。
该选项启用至少一个常驻实例和持续 CPU，避免没有 HTTP 请求时心跳暂停；会产生常驻资源费用。
默认监听关闭，普通 API 可以缩到零实例。多副本监听由 PostgreSQL 租约协调。

## 数据库与失败边界

SQL 仍来自 `CYT-Common-API/src/main/resources/db/migration/`，保留已有 V1–V7。
Cloud Run 不在应用启动时升级数据库；独立 Job 使用与测试服一致的 Flyway `11.20.2-alpine`、
历史校验、禁止自动 baseline、乱序和 clean 的策略。新空库执行全部版本，已纳管的 PostgreSQL 库只执行新增版本。
有业务表但无 Flyway 历史的库必须先核验接管方案，不能通过自动 baseline 跳过结构检查。

镜像和迁移 Job 名称包含本次 Cloud Build `BUILD_ID`，不同构建不会互相覆盖迁移 Job。
应用按推送后的镜像 digest 发布，revision 名称使用构建摘要并控制在 63 字符内，完整构建 ID 保存在 revision 环境中。
已有服务先创建 `--no-traffic` revision，随后检查该 revision 仍为最新就绪版本、镜像 digest 与构建 ID 相符。
构建时间从 Cloud Build 服务端读取；服务保留 `deploy.cyt.group/build-created-at` 和 `deploy.cyt.group/build-id` 注解，
较旧构建不能覆盖更新的已发布构建。流量和这两个注解通过同一次带 etag 的 Cloud Run v2 PATCH 更新；
发生 409/412 冲突立即停止，不重新读取后强行覆盖。原有流量 tag 保留为零常规流量。

首次创建服务时 Cloud Run 不支持 `--no-traffic`，首个 revision 会直接接收流量，因此首次创建必须串行。
这套校验不等同于全流程发布互斥；同一环境仍应串行发布，迁移与应用需保持向后兼容。
构建或迁移失败将返回非零状态，不执行后续服务更新；成功后的应用发布失败也不会自动回退数据库。
回退只切换到兼容的旧应用版本。
迁移 Job 和两个镜像保留用于排查和人工回退，按团队保留策略清理。

本入口不上传本机 `application-local.properties`、`.env`、`work/` 中的数据库备份或本地构建产物。
云端仅打包本目录无凭据的 `application-prod.yml`；所有数据库和管理员密钥由 Secret Manager 注入。

## 本地验证

下列测试仅检查发布计划、阶段顺序和打包策略，不调用 Google Cloud 或数据库。测试依赖 PyYAML：

```powershell
python -B -m unittest discover -s deploy/gcp/tests -v
```

真实 Cloud Build、容器构建和 Cloud Run 发布需在配置好账号、Secret 和网络后另行执行。

CLI 行为依据：[环境变量增量更新](https://docs.cloud.google.com/sdk/gcloud/reference/run/deploy)、
[Cloud Run Service etag](https://docs.cloud.google.com/run/docs/reference/rest/v2/projects.locations.services)、
[流量 PATCH](https://docs.cloud.google.com/run/docs/reference/rest/v2/projects.locations.services/patch)。
