# 新旧网关第三方 API 能力对齐

对照日期：2026-11-16。Java 项目为 `CYT-Common/CYT-Common-API`，Python 项目为 `cyt-common-api`；两版各自直接调用供应商，可独立运行。

## 范围

对照起点已包含本次 Shopify 退款功能：Java 169 项、Python 97 项，原有差集 72 项。补齐后两版均注册 **17 个平台、171 项操作**。Python 增加 74 项，Java 增加 2 项。操作名、公开路径、完整入参约束、配额、敏感/幂等标记和重试设置通过同一份契约快照验证；22 个旧别名保持兼容。

- Python 增加企微审批模板、预检/提单/追踪、审批流程与假期、附件和回调收件箱，以及显式字段的花名册读取。
- Python 增加 Winit 的 12 项仓库、库存、入出库、退货、面单与轨迹能力；领星增加订单、站内信、产品、库存、售后相关读取及评价。
- Python 增加 17 项有界归集/搜索操作，以及 DocuSign 模板详情、分页位置、角色标签预填和签署进度；Discord 服务器发现支持完整分页。
- Java 增加 `gmail.attachments.get`、`lingxing.reviews.list`，Gmail 发信支持附件和回复引用；公开请求约束与 Python 一致。
- 两版均包含 Shopify 原币预计算、原路/原生 Store Credit、混合支付分配、独立额外 Credit，沿用不可变预览校验和执行回执。

## 执行与数据边界

新增操作沿用调用系统身份、默认拒绝授权、配额与审计。归集游标绑定环境、调用系统、运行模式及筛选，支持半页续传、分页停滞检测和页面变化检测；不会通过缩小供应商页大小静默跳过记录。归集覆盖根记录，商品/订单内嵌子列表仍按相应供应商原生接口的分页约定处理。

企微模板别名和提单回执按环境、模拟/真实模式、调用系统持久保存。提单使用稳定的 `request_id` 或 `Idempotency-Key`；先提交幂等占位，再发送上游。超时或结果未知时保留占位并阻止重发，调用方应核对原单。写操作不自动重试。

企微回调使用 SHA-1 验签与 AES 解密，校验企业接收标识，限制 XML 大小、深度并拒绝 DTD/实体。事件按提交回执确定所有者，加密保存，持久去重；转发使用可恢复租约，失败重试，未配目标时可轮询。事件具有至少一次投递语义，消费者按 `event_id` 去重。

两版保留领星只读策略：`lingxing.workorder.add`、`lingxing.workorder.audit`、`lingxing.refund.create` 有兼容契约，实际调用拒绝，环境变量不能打开。Winit、Shopify、Gmail 写入继续受各自测试目标白名单控制；审批修改受显式授权、确认和幂等控制。

Gmail 附件读取核验实际邮箱、原邮件 MIME 归属、类型、文件头和大小。发送支持最多 5 个附件、单个 10 MiB、合计 20 MiB；不从调用方 URL 或文件路径下载附件。邮件正文、附件内容和花名册数据按敏感字段脱敏。

Store Credit 超过订单已付金额时，订单退款和额外赠额分开执行与记账。真实供应商账号的权限、凭据、网络与资金通道仍由部署环境验收；本次检查没有执行真实退款、发 Credit、发信或建物流单。

## Python 数据库升级

新增 Alembic 版本 `2026111601`，增加 `gateway_wecom_form`、`gateway_wecom_submission`、`gateway_wecom_event`。部署 Python 版本前，在目标环境按原有迁移流程执行：

```powershell
python -m alembic upgrade head
```

代码提交不会自动对共享或生产数据库执行迁移。临时 SQLite 库已验证升级、回退与原有数据保留；Java 使用已有的审批表结构，本次没有新增 Java 数据库迁移。

## 可重复检查

Python 仓库执行：

```powershell
python -m unittest discover -s tests -q
python scripts/check_gateway_parity.py --java-root D:/code/CYT-Common
```

Java 仓库执行：

```powershell
mvn -f CYT-Common-API/pom.xml test package
python CYT-Common-API/tools/check_gateway_parity.py --python-root D:/code/cyt-common-api --java-root D:/code/CYT-Common
```

两版各自的测试资源包含相同的 `public_capabilities.json` / `public-capabilities.json` 快照，覆盖全部公开操作和输入约束；退款另外保留 12 组逐字段金额、分配和哈希对照。Java PostgreSQL 集成验证使用隔离实例 `TEST_PG_URL`；退款持久化用例在配置该变量时也连接独立 PG 临时库。此前 MySQL 用例跳过记录属于修正前基线，不作为 PG 验收结果。

原 Java 测试中要求邮件消息不含 `issue_type` 的断言与实际既有行为不一致；本次保留 CS 归集需要的 `issue_type=usage`，将断言校正为该业务契约。

官方参考：[Gmail 附件接口](https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.messages.attachments/get)、[领星评价明细](https://apidoc.lingxing.com/new/api-docs/Service/reviewV2/)。
2026-11-16 补充：Java 运行依赖、Flyway V1–V6、SQL 和 local/prod 配置已统一为 PostgreSQL；Python 保持 PostgreSQL 支持，并增加 `scripts/test_postgres_native.py`，在随机临时库执行真实 Alembic 升级和 16 项原生业务测试。未迁移共享或生产数据库。

Python 真实 PG 验证需设置 `TEST_NATIVE_PG_CLUSTER=postgresql://<user>:<password>@127.0.0.1:<测试端口>/postgres` 后执行 `python scripts/test_postgres_native.py`。入口只接受回环测试实例，仅创建和删除自己生成的随机临时库。
