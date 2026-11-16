# 迁移到 Java / PostgreSQL

## 可行性结论

原后端的认证、授权、配额、幂等、外部适配器和管理端边界相对明确；前端是独立 Vue 应用。Java 后端当前统一使用 PostgreSQL，支持从原 Python / PostgreSQL 或迁移前的 Java / MySQL 复制业务数据。

这次建立独立 Java 服务与新的管理前端，原项目和原数据库保留。Java 使用同一批操作契约，重新实现执行流程和平台请求，不在后台启动 Python 或转发到旧服务。

## 已处理的迁移点

- 业务表由 PostgreSQL Flyway 迁移建立；文本匹配保留大小写敏感语义。
- 标识符统一使用 PostgreSQL 语法，不再使用 MySQL 反引号。
- 数据库和 JDBC 会话固定 UTC，接口时间带时区。
- Fernet 的版本字节、时间戳、AES-CBC 和 HMAC 格式兼容，已有密文可保留。
- API Key 的摘要与 `cyt_<prefix>_<secret>` 格式兼容。
- 老操作名称通过别名映射继续接受。
- 自定义接口保留 `status_code / data / mock` 的结果封装，支持旧 `param / size / size_param / start / next_path` 分页配置。
- 幂等记录增加状态列，在上游操作前原子占位，防止并发重复执行。

## 切换顺序

1. 备份原数据库，在 PostgreSQL 创建 UTF-8 新库及独立账号，配置 `POSTGRES_URL`、`POSTGRES_USERNAME`、`POSTGRES_PASSWORD`，先启动模拟环境。
2. 比对操作目录、系统、授权、环境和动态接口配置。
3. 需要迁移数据时关闭新库的演示种子，通过独立 Flyway CLI 或既有数据库发布 Job 执行 Flyway，目标业务表保持为空。先运行迁移工具的只读预检，再执行复制。`prod` 应用启动不再自动建表，本地 `local` 保持自动迁移。
4. 配置与原部署一致的主密钥和专用测试账号凭据，在测试环境逐平台验证真实只读操作。
5. 验证需要的回调域名、DocuSign HMAC、Portal 集成和系统 API Key。
6. 用专用测试店铺/邮箱验收允许的写操作。核对调用方、目标身份、幂等、失败重试和回调。
7. 压测和并行对照通过后，停止旧服务写入，准备已执行 Flyway 的独立空目标库，执行最终全量复制并核对，再切换反向代理或调用方地址。复制工具只支持空目标，不支持向已有数据追加增量。

本次未对现有业务数据执行迁移，也未部署或切换流量。

Flyway 脚本已切换为 PostgreSQL 语法，不能直接在原 MySQL 库执行，也不能把原 MySQL 的 `flyway_schema_history` 复制到新库。新库独立执行当前全部迁移后，再复制业务数据。

测试服编排使用 `common_java_postgres` 新卷，原 `common_java_mysql` 卷保留。已有 `.env` 需要显式补充 PostgreSQL 配置；`redeploy.sh` 缺少 `POSTGRES_PASSWORD` 时会在同步编排及重建容器前退出。配置密码并不代表数据已经迁移，执行发版前应先完成数据复制与核对。保留原库备份，切换窗口内停止旧服务写入，避免两个数据库继续分叉。

## 数据复制工具

这里是一次性跨库复制；日常结构变更使用独立 Flyway 发布流程，
不要在每次发版时重复运行复制工具。V1–V6 作为已执行历史保留，后续变更新增版本。

工具位于 `CYT-Common-API/tools/migrate_data.py`，依赖见同目录 `requirements-migration.txt`。连接串从 `SOURCE_DATABASE_URL` / `TARGET_DATABASE_URL` 环境变量读取，避免密码进入命令行和历史记录。源可为 PostgreSQL、SQLite 或旧 MySQL；目标必须是独立空 PostgreSQL 库，格式为 `postgresql://<user>:<password>@<host>:5432/cyt_common_<target>`。

```sh
python tools/migrate_data.py          # 只读预检，不复制
python tools/migrate_data.py --apply  # 明确执行复制
```

可用 `SOURCE_DB_SCHEMA` 指定 PostgreSQL schema。目标必须已执行全部 Flyway 迁移且设置 `JAVA_SEED_ENABLED=false`，业务表必须全部为空，库名必须以 `cyt_common` 开头；工具不会创建库、删表、清空目标、更新已有记录，也不会改写源数据。源库存在企微审批或 Discord 表时会一起复制。复制在一个目标事务中执行，异常回滚；保留原主键后会校准 PostgreSQL 自增序列。先对真实数据快照预检并核对兼容性，再安排生产迁移窗口。

旧幂等指纹使用 Python 参数序列化，新指纹基于规范化后的 JSON。历史记录仍保留以阻止重复写入；复用旧键且指纹不一致会得到 409，不能把删除历史记录当作重试手段。应先核对旧调用结果。

## 行为上的收紧

- 敏感操作在执行时要求精确授权，通配授权不能授予敏感权限。
- 真实模式缺少配置返回 503，不自动返回模拟成功。
- 写操作在网关层不自动重试，避免超时后重复写入；只读操作按可重试性与次数重试。
- 未验签的本地模拟回调不会转发到业务系统。
- 服务账号必须显式映射到调用系统。
- 模拟开关作为部署进程配置，凭据值仍可在页面加密更新并立即生效。

Shopify 和 Gmail 的真实写操作继续受测试开关、目标白名单及邮箱收件人白名单限制。真实生产业务数据不能通过调试台绕过这些规则。
