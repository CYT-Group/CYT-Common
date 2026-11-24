# CYT Common API 对外接入文档

面向需要通过 CYT Common 调用外部平台的业务系统。本指南说明如何领取调用身份、发起请求和处理结果。接口参考以生产 OpenAPI 为准，逐项提供 HTTP 方法、路径、请求字段、响应 Schema 和调用示例。

## 五分钟接入

1. 向管理员提供调用系统标识、所需操作标识和预计调用量，领取网关基础地址及独立 API Key。
2. 在业务后端设置 `CYT_API_BASE` 和 `CYT_API_KEY`。浏览器和移动端通过自己的业务后端访问网关。
3. 从网关登录页点击“在线文档”，或解压“下载接入包”后打开 `START_HERE.html`。按平台、模块或操作标识查找接口，也可将 `openapi.json` 导入 API 客户端。
4. 使用已授权的只读接口联调。以下例子使用 `docusign.templates.list`，地址和授权以管理员交付为准。

```bash
curl --fail-with-body --request POST \
  "$CYT_API_BASE/integrations/docusign/templates/list" \
  --header "Authorization: Bearer ${CYT_API_KEY}" \
  --header 'Content-Type: application/json' \
  --data '{"page_size":1}'
```

生产网关为 `https://cyt-common-wmemf5uxya-uc.a.run.app`，也可使用管理员交付的访问域名。`GET /health` 无需业务 API Key，可检查服务和数据库连通状态。业务授权需单独调用只读操作确认。本地 Java 服务默认使用 `http://127.0.0.1:8189`。

## 鉴权与调用权限

- API Key 通过 `Authorization: Bearer <API Key>` 发送，由网关解析所属系统。将 Key 保存于业务后端的密钥存储或环境变量。
- 已配置 Google 服务账号身份的调用方也可在同一请求头发送 Google ID token。`aud` 使用网关管理员指定的 audience，服务账号须映射到已启用的调用系统。
- 接口目录表示网关可提供的能力。实际调用仍检查系统状态、操作授权、拒绝规则、配额和上游凭据。只读操作也需要授权。
- `X-System-Id` 适用于显式启用开发身份的环境。生产接入使用 API Key 或已配置的 Google 服务账号身份。

## 通用请求与返回

大多数操作使用 `POST /integrations/{platform}/{resource}/{action}`，请求体直接传 JSON 参数。无参数的操作传 `{}`。请求字段、枚举、默认值和长度限制以接口 Schema 为准。

- `X-Trace-Id` 可选，用来关联业务日志和网关记录。
- 支持幂等的操作可传 `Idempotency-Key`。需要幂等键的操作应使用稳定的业务请求号，长度最多 128 字符。接口同时要求 `request_id` 时，两者传同一值。重试时沿用原键和原参数。
- `PUT /integrations/nas/objects/upload` 使用原始文件字节。`filename`、`folder`、`key`、`bucket` 放在 query，提供准确的 `Content-Length` 及文件 `Content-Type`。该操作要求独立精确授权，单次上传超时后先核对目标对象。

成功时从 `data` 读取业务结果。SDK 的 `call` 直接返回 `data`。以下为统一响应结构示例，具体字段随操作变化。

```json
{
  "ok": true,
  "data": {},
  "meta": {
    "trace_id": "example-trace-id",
    "latency_ms": 42,
    "platform": "docusign",
    "quota": "30/min",
    "mock": false
  }
}
```

`meta.mock=true` 表示模拟响应。若 `data` 有 `partial` 或 `errors`，逐项核对执行结果。普通分页按对应接口返回的游标续查。聚合接口返回 `page.has_more` 为 `true` 时，按该接口协议将 `page.next_cursor` 传给后续请求，并保持原筛选条件。

失败时返回 `ok=false` 和 `error`。`error.type`、`message`、`retryable` 表明错误类型和提示，部分错误还有 `code`、`trace_id`、`detail` 或 `retry_after`。排障时提供操作标识、时间、HTTP 状态和 `trace_id`。

- `401 / 403` 检查凭据、调用系统状态及接口授权。
- `404 / 422` 检查接口路径、资源是否存在、字段和值。代理上传缺长度或超出大小限制时还可能返回 `411 / 413`。
- `409` 检查幂等键是否复用到不同请求。请求仍在执行或结果待确认时，先确认原请求结果。
- `429` 按 `Retry-After` 或 `error.retry_after` 等待，再按接口适用的重试策略执行。
- `502 / 503 / 504` 检查上游状态。写入超时或连接断开时，先核对业务状态，再决定是否重试。

## 批量调用

`POST /integrations/batch` 使用同样的 Bearer 认证和 JSON 请求。`calls` 可包含 1–100 项，每项的 `operation` 和 `params` 取自接口文档，支持幂等的项可传 `idempotency_key`。网关依次执行并逐项检查授权与配额。`stop_on_error=true` 在第一次失败后停止后续项。

```json
{
  "calls": [
    {"operation": "docusign.templates.list", "params": {"page_size": 1}},
    {"operation": "nas.objects.list", "params": {"max_keys": 10}}
  ],
  "stop_on_error": false
}
```

响应 `data` 内有 `batch_id`、`total`、`succeeded`、`failed` 和 `results[]`。`total` 是原请求项数。外层 `ok=true` 表示批次请求已处理，须检查 `results[]` 每一项的 `ok` 和 `error`。已执行项不会随其他项失败而回滚。代理上传需独立发起 PUT。

## Python 和 TypeScript

Python 3.10+ 可将接入包中的 `sdk/python/cyt_gateway.py` 加入模块搜索路径。

```python
import os
from cyt_gateway import Gateway

gateway = Gateway(os.environ["CYT_API_BASE"], os.environ["CYT_API_KEY"])
data = gateway.call("docusign.templates.list", {"page_size": 1})
```

TypeScript 在业务后端导入接入包中的 `sdk/typescript/index.ts`。

```typescript
import { Gateway } from "./sdk/typescript/index";

const gateway = new Gateway(process.env.CYT_API_BASE!, process.env.CYT_API_KEY!);
const data = await gateway.call("docusign.templates.list", { page_size: 1 });
```

原始文件上传使用 Python 的 `upload_file` 或 TypeScript 的 `uploadFile`。

## 全量接口目录

网关在 `/api-reference` 展示随应用发布的内置操作，`/api-reference/download` 下载同一版离线接入包，`/api-reference/openapi.json` 下载同一目录的 OpenAPI。实际数量以网关页面为准。

- 协作与签署：企业微信、Discord、DocuSign。
- 电商、支付与评价：Shopify、Stripe、PayPal、Judge.me、Trustpilot。
- 邮件、存储与产品数据：Gmail、Google Drive、Google Cloud Storage、群晖 NAS MinIO、RD 产品目录。
- 客服、ERP 与仓配：QuickCEP、领星、马帮、Winit。

`START_HERE.html` 支持按平台、模块、操作名称和请求路径检索，提供 HTTP、Python、TypeScript 示例和完整 Schema。`openapi.json` 保留生产目录的操作标识与契约，可用于导入客户端和生成代码。未登记的业务响应字段以对应上游契约和联调结果为准。

管理员可从网关“接入文档”选择调用方并导出专属接入包。公开目录用于查阅内置接口，专属接入包按授权提供调用方可用的内置和自定义接口。

## 专用业务路由

`POST /integrations/shopify/stores/list` 供客服系统 `cs` 核对店铺，依赖 `shopify.orders.list` 授权。请求传 `{"stores":["example.myshopify.com"]}`，每次最多 250 个域名。`data.stores[]` 按店铺返回核对状态，可取 `authorized`、`pending` 或 `unavailable`。授权店铺还可返回店铺 ID、名称、币种、时区和权限范围等字段。

## 供应商回调

以下地址供上游平台配置事件推送，由管理员按系统或账号配置验签凭据。回调使用对应平台的签名协议。

- `POST /integrations/docusign/webhooks/events` 或 `POST /integrations/docusign/webhooks/{system}/events` 接收 DocuSign 事件。传原始请求体及 `X-DocuSign-Signature-1`，响应包含 `ok`、标准化事件 `data` 和转发结果 `forward`。
- `GET /integrations/wecom/webhooks/{system}/events` 用于企业微信验证地址。Query 包含 `msg_signature`、`timestamp`、`nonce`、`echostr`，返回解密后的挑战文本。
- `POST /integrations/wecom/webhooks/{system}/events` 接收企业微信事件。Query 包含 `msg_signature`、`timestamp`、`nonce`，请求体为企业微信加密 XML，成功返回文本 `success`。
- `POST /integrations/stripe/webhooks/{account}/dispute-closed` 接收 Stripe 争议关单事件，使用原始 JSON 和 `Stripe-Signature` 验签。响应 `accepted` 表示事件是否属于接收范围。
- `POST /integrations/paypal/webhooks/{account}/dispute-closed` 接收 PayPal 争议关单事件，使用原始 JSON 和 PayPal 传输签名头验签。响应 `accepted` 表示事件是否属于接收范围。

## 上线核对

- 用生产网关和业务系统自己的身份验证一个已授权的只读操作，确认 `meta.mock=false`。
- 核对调用系统的操作权限和配额，针对写入、资金或删除操作使用接口规定的确认字段与幂等协议。
- 验证分页续传、部分成功、幂等冲突和超时后的核对流程。
- 业务日志保留 `trace_id` 和稳定业务编号，密钥留在后端密钥管理中。
