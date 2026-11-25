# Java 网关 PostgreSQL 测试服

测试编排使用专用 PostgreSQL 17、新卷 `common_java_postgres` 和 Java 网关。原 Python 服务与原 MySQL 卷保持独立；不会自动复制旧业务数据。

1. 首先备份原库。已有 MySQL 部署需要先准备独立 PG 库并完成数据核对，再安排切换。
2. 参考 `.env.example` 配置 `POSTGRES_DATABASE`、`POSTGRES_USERNAME`、`POSTGRES_PASSWORD` 及现有管理密码、JWT 和凭据主密钥。保留原环境标识与主密钥。
3. 首次初始化可用 `db-init.sh` 启动 PostgreSQL 并执行 Flyway。只需准备编排并等待数据库时，使用 `redeploy.sh <源码根目录> prepare`。
4. 正常发布时，`ci-build.sh` 按协议 1 校验 SQL、构建推送候选镜像，再由 `redeploy.sh` 等待 PostgreSQL、执行 Flyway 并更新应用。测试编排关闭应用启动期 Flyway。
5. 应用健康检查要求 `database=postgresql`，默认使用 Compose 实际发布的端口。配置 `PUBLIC_BASE_URL` 时，再验证公网首页和静态资源。验证成功后才将候选镜像写入 `.env`，已有密码和其他设置继续沿用。

Jenkins 的 `job-deploy.xml` 使用 Git SCM 检出所选分支，由 Git Parameter 动态列出分支。构建按 Jenkins 的检出提交生成独立镜像和版本记录。`VERIFY_ONLY=true` 只校验版本和 SQL，`SKIP_DEPLOY=true` 校验后构建并推送候选镜像。公网来源通过 `.env` 的 `CORS_ALLOW_ORIGINS` 传入应用。

### Jenkins 专用构建镜像

发布任务默认从 `192.168.1.182:5000/cyt-common-ci-r1/` 拉取 Node 22、Maven 3.9 / Java 21 和 Java 21 JRE 基础镜像。上游镜像摘要固定在 [`ci-base-images.Dockerfile`](jenkins/ci-base-images.Dockerfile)。pnpm 11 通过 `pnpm_config_registry` 使用 `https://registry.npmmirror.com`，应用镜像构建可用 `NPM_REGISTRY` 覆盖该值。Maven 基础镜像通过 [`maven-settings.xml`](jenkins/maven-settings.xml) 将 Maven Central 指向阿里云镜像。

首次搭建或重建专用基础镜像时，在有内网仓库推送权限的测试构建节点执行：

```bash
bash deploy/test-server/publish-jenkins-base-images.sh
```

切换基础镜像版本时，可先用新的 `BASE_REGISTRY` 前缀发布，再给发布任务设置相同的前缀。上游 Docker Hub 访问受限时，发布脚本也支持用 `CI_BASE_UPSTREAM` 指定可用的 Docker Hub 镜像前缀。两个前缀均以 `/` 结尾。

编排不公开数据库端口。修改源码或部署文件并不等于已部署；本次验收只创建本机临时 PG 实例，没有修改测试服或生产数据。

新旧网关能力与 SQL 变更见 [PG 修正记录](../../docs/postgresql-main-correction.md) 和 [数据迁移](../../docs/migration.md)。
