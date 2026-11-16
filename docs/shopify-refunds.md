# Shopify 原币退款与店铺 Credit

Java `CYT-Common` 与 Python `cyt-common-api` 的原币退款接口保持相同参数、响应字段及方案指纹。Python 配套提交为 `a65dedb`，客服系统配套提交为 `242f0d5`。

## 退款流程

1. 关联准确店铺、订单和售后商品行，调用预计算接口。
2. 主显示客户原币及实际实付金额；Shopify 本位币金额单独用于内部统计。
3. 商品数量和运费选择变化时重新预计算折扣、税费、关税和可退余额；含税运费使用 Shopify 两次建议金额的差值，避免重复计税。
4. 客服填写客户原币金额或比例；业务系统按同币种累计已退、待执行和本次申请进行审批。
5. 唯一执行方使用审批方案执行；余额、商品金额或支付流水变化时须重新预览审批。
6. 分别核对订单退款和额外 Credit 的真实资金回执。信息不足可先建售后单，暂停退款执行。

## 接口

`POST /integrations/shopify/refunds/quote`（只读）

- 必填：`store`、`order_id`。
- `target`：`original` 或 `store_credit`，默认按原支付方式退回。
- `items`：`line_item_id` 与 `quantity`；商品行必须来自关联订单。
- `refund_shipping`：是否退全部可退运费，默认不退。
- `amount`、`currency`：可选。金额只能采用客户实际支付原币，不能把本位币数字改标成另一币种。
- 混合支付的自定义部分退款使用 `allocations` 明确各原支付流水的 `parent_id`、`amount`。

返回客户原币的 `paid`、`refunded`、`remaining`、`amount`，本位币 `shop_money`，预计算明细 `calculation`，原支付渠道 `payments`、可退渠道 `channels`、实际分配 `allocations` 和 `quote_hash`。

`POST /integrations/shopify/refunds/execute-plan`（资金写入）

- 使用预计算的订单、原币金额、商品、运费选择与渠道分配。
- 必须提供 `currency`、`amount`、`quote_hash`、`confirm=true` 及稳定的 `idempotency_key`。
- 原路全退保留现金和原生 Store Credit 各自的原支付流水；全部退 Credit 使用 `refundMethods.storeCreditRefund`。
- 只执行订单可退范围内的金额；超额 Credit 必须拆分，不能把整笔补偿当作订单退款。
- 人工收款渠道由财务核对实际出款，不能仅凭 Shopify 记账确认资金成功。

`POST /integrations/shopify/store-credit/credit`（独立资金写入）

- 必填：`store`、`order_id`、准确的 `customer_id`、原币 `currency`、正数 `amount`、`confirm=true`。
- 请求头必须提供稳定的 `Idempotency-Key`。请求先持久化占位，再调用上游；执行中或结果未知的请求禁止重新发放。
- 例如实付 100、总发放 120 Credit：先执行订单退款 100，再独立发放补偿 Credit 20，分别保存凭证和执行状态。
- Shopify 应用需要 `write_store_credit_account_transactions` 权限。

## 接入与验收

授权遵循原网关规则：读取操作需允许，资金写入需精确授权；本接口不自动开放正式店铺写权限。店铺凭据和测试目标仍由既有凭据管理与 `TestWritePolicy` 核对。

模拟模式明确返回 `mock=true`，不生成真实退款方案和资金成功凭证。存在 Refund ID 不等于钱已退回，业务系统仍须核对原回执中各笔交易的成功状态、金额、币种、原支付流水及运费。

固定的 GraphQL 模板位于 `shopify-refund-queries.json`，不接受任意 GraphQL 透传。无需新增数据库表；复用已有持久化幂等记录，网关重启后仍阻止重复资金操作。

本地运行：

```powershell
cd CYT-Common-API
mvn '-Dtest=ShopifyRefundMethodsTest,ShopifyRefundDispatchTest,OperationContractTest' test
```

`shopify-refund-parity.json` 包含 12 组由 Python 配套实现生成的合成对照数据，验证 Java/Python 的金额、渠道分配和 `quote_hash` 一致；持久化测试使用独立内存数据库和模拟上游，不发起真实退款或 Credit 发放。正式账号、资金及财务中台回推联调需另行完成。
