# PostgreSQL 数据库变更

Common 使用 Flyway 管理结构、索引和随版本交付的数据修正。测试服可在部署前手动执行迁移。
本地 `local` 配置保留启动时迁移，方便日常开发。

## 已有数据库与新增版本

既有 `V1__gateway.sql` 至 `V7__payment_dispute_closures.sql` 已用于 PostgreSQL 初始化和升级，
文件名、版本和内容保持冻结。不要把它们改成日期格式，也不要复制旧 MySQL 的
`flyway_schema_history` 到 PostgreSQL。已执行历史和校验值由各 PostgreSQL 环境自行维护。

新变更直接放在本目录，命名为 `VYYYYMMDD_NNN__英文说明.sql`：

- 日期按北京时间；同一天序号从 `001` 递增，例如 `V20261112_001__add_external_reference.sql`。
- 文件使用 UTF-8 无 BOM、LF 换行；不建立 SQL 子目录，不使用 `R__` 重复迁移。
- 使用当前连接的数据库和 schema；不写固定环境地址、数据库密码或密钥。
- 延续 Common 的 PostgreSQL Boolean 和 UTC 时区约定，时间字段使用带时区时间戳。
- SQL 与业务代码从同一次源码构建发布，并在隔离 PostgreSQL 库验证。

共享环境成功执行后，不能修改、重命名或删除该版本，修正应新增版本。发布入口禁止
自动 baseline、clean、repair、乱序执行和忽略缺失历史。缺失迁移或校验值不一致会阻断发布。
新增结构应兼容仍在运行的旧应用；回退应用镜像不会自动回退数据库。删列、收紧非空约束
和大表回填按兼容结构、回填、再收紧分步执行。

## 初始化与日常升级

首次 PostgreSQL 空库初始化和日常版本升级使用同一个 Flyway 入口。数据库迁移只执行
未安装的版本，不重复执行已有脚本，也不灌入本机的演示业务数据。

在 PostgreSQL 容器启动后，从仓库根目录执行：

```bash
ROOT="$PWD"
bash deploy/test-server/db-upgrade.sh "$ROOT" lint
bash deploy/test-server/db-upgrade.sh "$ROOT" info
bash deploy/test-server/db-upgrade.sh "$ROOT" validate
bash deploy/test-server/db-upgrade.sh "$ROOT" migrate
```

`db-upgrade.sh` 默认连接容器 `cyt-common-java-db`，需要时通过
`COMMON_DATABASE_CONTAINER` 或 Python 参数 `--database-container` 指定。
`lint` 只检查本地 SQL，不依赖 Docker 或数据库。

迁移使用固定的 `flyway/flyway:11.20.2-alpine`，通过数据库容器网络连接同一 PostgreSQL。
口令从容器环境读取，只通过子进程环境传递，不进入命令行或构建日志。
执行记录保存在 `flyway_schema_history`。迁移失败时应用更新停止；保留失败日志并核对
事务状态，不通过篡改历史校验值绕过。

## 一次性数据搬迁

`CYT-Common-API/tools/migrate_data.py` 用于从旧 MySQL 或 Python 网关向独立空 PostgreSQL
库复制业务数据，和上面的日常增量 SQL 升级是两个步骤。先在新库执行 Flyway，关闭演示
数据初始化，再执行只读预检和明确的复制操作。保留源数据库、凭据主密钥及原环境名称。
复制工具不会在日常发版中自动运行，参见项目的 `docs/migration.md`。

## 验证

```bash
python3 -B -m unittest discover -s deploy/test-server/tests -v
# 仅操作自动创建的一次性 PostgreSQL 容器，不连接业务数据库。
COMMON_RUN_FLYWAY_INTEGRATION=1 python3 -B deploy/test-server/tests/test_flyway_integration.py -v
```

单元测试覆盖版本排序、重复版本、编码、受限操作、凭据传递和失败退出。
显式开启的容器测试覆盖实际 V1–V7 空库初始化、重复执行、历史篡改、缺失文件、SQL 失败
停止后续版本，以及 PostgreSQL 事务回滚。
