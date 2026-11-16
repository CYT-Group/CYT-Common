# PostgreSQL 主分支修正与验收

日期：2026-11-16。

此前第三方能力提交基于仍使用 MySQL 的旧 Java `main`；工作目录里的 PostgreSQL 迁移尚未进入该分支。此前“391 项测试，其中 25 项跳过”的结果不代表 PostgreSQL 验收。本次在隔离工作树中整合数据库迁移，不包含目录重构、代理或其他未完成改动。

## 运行基线

- Java 使用 PostgreSQL JDBC、Flyway 11.20.2 与 `flyway-database-postgresql`，移除 MySQL 运行依赖。V1–V6 与工作目录已准备的 PG SQL 内容一致，后续变更应新增迁移版本。
- SQL 使用 PostgreSQL 标识符、`ON CONFLICT`、`TO_CHAR`；重复回调不会因捕获唯一约束异常而令整个事务失效。插入只请求 `id` 作为生成主键。
- 数据库会话统一 UTC，带时区时间按 UTC 输出。退款幂等与回执继续持久保存，公开 171 项能力及请求契约保持一致。
- Python 保持独立 PostgreSQL 实现，新增可重复的真实 PG 原生业务验收入口。

## 环境与迁移

本地默认 profile 为 `local`，允许读取忽略的 `application-local.properties`。配置使用 `POSTGRES_URL`、`POSTGRES_USERNAME`、`POSTGRES_PASSWORD`，也支持标准 `SPRING_DATASOURCE_*` 覆盖。

生产使用 `SPRING_PROFILES_ACTIVE=prod`，必须显式提供连接配置，不导入本地属性文件，默认关闭 Flyway、种子、模拟和开发登录。生产启动前，通过独立 Flyway CLI 或既有数据库发布 Job 执行同一发布版本的迁移；使用与应用一致的 Flyway 11.20.2，开启校验、关闭 clean 和 baseline-on-migrate。具体数据切换见[迁移说明](migration.md)。

现有 `deploy/test-server` 入口已改为 PostgreSQL 17 和独立 `common_java_postgres` 卷，测试服明确开启启动迁移。`JAVA_SEED_ENABLED=false` 可关闭种子。旧 `.env` 不会覆盖；缺少 PG 配置或编排校验失败时，不替换原编排、不启动新容器。原 MySQL 数据卷仍需保留，不能把更换镜像当成数据迁移，也不能复制 MySQL 的 Flyway 历史。

数据复制工具默认只读预检，目标必须是独立、业务表为空的 PostgreSQL 库；支持旧 PG、SQLite、MySQL 来源，转换布尔值和 UTC 时间，复制后校准自增序列。没有对真实或共享业务数据执行复制。

## 本次验证

专用 PostgreSQL 18.6 实例仅监听本机 `127.0.0.1:55496`，测试创建随机数据库并清理，未使用系统 PostgreSQL 服务或业务数据库。

- Java Maven `package`：400 项测试，0 失败、0 错误、0 跳过。其中 26 项完整服务 PG 集成测试、5 项真实 PG 退款回执/幂等/并发测试，另有 8 项 profile 配置测试。
- 退款的无 PG 配置分支：5 项 H2 PostgreSQL 兼容模式用例通过。
- Python 全量：294 项测试通过。真实 PG 入口另外执行全量 Alembic 升级至 `2026111601`、核验新增审批表，并通过 16 项业务场景。
- 公开能力对照：两版 171 项操作的名称、路径、参数约束、配额、敏感标记和重试设置一致。
- Python 迁移/凭据工具：11 项测试通过。部署入口：3 项隔离命令测试通过，不调用真实 Docker。
- 在两个随机 PG 临时库之间执行数据复制冒烟验证：只读预检保持目标为空，复制保留大小写、布尔值及 UTC 时间，下一条自增 ID 正确，重复向非空目标复制被拒绝。
- 打包 JAR 只含 PostgreSQL 驱动及对应 Flyway 模块；V1–V6 SQL 与准备的 PG 版本内容一致。契约导出工具不再覆盖数据库迁移文件。

## 复现入口

Java 仓库根目录，连接仅用于测试的回环实例，URL 不含业务数据库名：

```powershell
$env:TEST_PG_URL = 'jdbc:postgresql://127.0.0.1:55496/'
$env:TEST_PG_USERNAME = 'postgres'
$env:TEST_PG_PASSWORD = '<测试实例密码>'
mvn -f CYT-Common-API/pom.xml package
```

Python 仓库根目录：

```powershell
python -m unittest discover -s tests -q
$env:TEST_NATIVE_PG_CLUSTER = 'postgresql://<user>:<password>@127.0.0.1:55496/postgres'
python scripts/test_postgres_native.py
```

这些检查使用模拟上游，不执行真实退款、发放 Store Credit、发送邮件或创建物流订单。代码提交不等于已部署，也不会自动迁移共享或生产数据库。
