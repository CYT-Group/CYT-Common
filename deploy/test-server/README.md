# Java 网关 PostgreSQL 测试服

测试编排使用专用 PostgreSQL 17、新卷 `common_java_postgres` 和 Java 网关。原 Python 服务与原 MySQL 卷保持独立；不会自动复制旧业务数据。

1. 首先备份原库。已有 MySQL 部署需要先准备独立 PG 库并完成数据核对，再安排切换。
2. 参考 `.env.example` 配置 `POSTGRES_DATABASE`、`POSTGRES_USERNAME`、`POSTGRES_PASSWORD` 及现有管理密码、JWT 和凭据主密钥。保留原环境标识与主密钥。
3. 按原入口执行 `build-on-server.sh` 构建镜像，再执行 `redeploy.sh`。首次会生成随机密钥；已有 `.env` 不覆盖。只有旧 MySQL 配置时会在替换编排、重建容器前拒绝继续。
4. 本测试编排显式开启启动期 Flyway；生产 `prod` profile 默认关闭自动迁移，使用独立数据库升级流程。
5. `/health` 应返回 `database=postgresql`。测试 compose 的环境默认为 `test`，上游模拟与调用身份沿用 `.env` 的显式设置。

Jenkins 的 `SKIP_DEPLOY=true` 仅构建并推送带源码 SHA 的候选镜像，不重启服务；`1` 也保持兼容。构建与发布通过 `COMMON_JAVA_IMAGE` 使用同一镜像。公网来源通过 `.env` 的 `CORS_ALLOW_ORIGINS` 传入容器，切换时应保留现有精确域名。

编排不公开数据库端口。修改源码或部署文件并不等于已部署；本次验收只创建本机临时 PG 实例，没有修改测试服或生产数据。

新旧网关能力与 SQL 变更见 [PG 修正记录](../../docs/postgresql-main-correction.md) 和 [数据迁移](../../docs/migration.md)。
