# TikTok Shop 接入

CYT-Common 为每项读取提供单独的 POST 路由。将下列路径接在网关地址后，使用 `Authorization: Bearer <API Key 或服务账号 ID token>`。接入文档按调用方权限展示参数和响应。每个请求都须提供 `account`，值为 Common 登记的店铺别名。

| 操作 | Common 路径 | 业务参数 | TikTok Shop 上游 |
| --- | --- | --- | --- |
| `tiktok.conversations.list` | `/integrations/tiktok/conversations/list` | `page_size`、`page_token` | GET `/customer_service/202309/conversations` |
| `tiktok.messages.list` | `/integrations/tiktok/messages/list` | `conversation_id`、`page_size`、`page_token` | GET `/customer_service/202309/conversations/{conversation_id}/messages` |
| `tiktok.orders.list` | `/integrations/tiktok/orders/list` | 更新时间窗、分页 | POST `/order/202309/orders/search` |
| `tiktok.orders.get` | `/integrations/tiktok/orders/get` | `order_id` | GET `/order/202309/orders` |
| `tiktok.products.list` | `/integrations/tiktok/products/list` | 更新时间窗、分页 | POST `/product/202502/products/search` |
| `tiktok.products.get` | `/integrations/tiktok/products/get` | `product_id` | GET `/product/202309/products/{product_id}` |
| `tiktok.tracking.get` | `/integrations/tiktok/tracking/get` | `order_id` | GET `/fulfillment/202309/orders/{order_id}/tracking` |
| `tiktok.returns.list` | `/integrations/tiktok/returns/list` | 更新时间窗、分页 | POST `/return_refund/202602/returns/search` |

更新时间窗使用 Unix 秒，`updated_from` 包含本秒，`updated_until` 排除本秒。翻页时保持店铺、时间窗和页大小，传入上页的 `data.next_page_token`。会话页最多 20 条，消息页最多 10 条，订单及商品页最多 100 条，退货退款页为 10–50 条。列表返回 `data.raw` 数组，详情返回 `data.raw` 对象，订单详情查无记录时为 `null`。物流返回扫描状态码和时间戳数组。

## 配置与授权

1. 在 TikTok Shop Partner Center 为各店授权实际使用的客服、订单、商品、物流和退货退款能力，核对卖家身份。[订单](https://partner.tiktokshop.com/docv2/page/order-api-overview) · [商品](https://partner.tiktokshop.com/docv2/page/search-products-202502) · [物流](https://partner.tiktokshop.com/docv2/page/get-order-tracking-202309) · [退货退款](https://partner.tiktokshop.com/docv2/page/search-returns-202602)
2. 在 Common 配置 `TIKTOK_ACCOUNTS`，按店铺别名登记 `APP_KEY`、`APP_SECRET`、`ACCESS_TOKEN` 和 `SHOP_CIPHER`。例如 `epomaker-keyboard` 对应 `TIKTOK_EPOMAKER_KEYBOARD_APP_KEY`。应用密钥、卖家令牌和店铺密文使用加密凭据或 Secret Manager。
3. `TIKTOK_COMMERCE_ACCOUNTS` 是订单、商品、物流和售后的启用名单，与 CS 同项配置保持一致。默认启用 `epomaker-keyboard`，其他支持的店铺为 `epomaker-usa`、`linsoul`、`mechlands` 和 `kiiboom`。
4. 在 Common 按上表的操作标识逐项授权给实际调用系统，例如 `cs`。八项读取需要精确授权，平台通配授权无法代替。旧网关已有的授权需要在新版 Common 核对。

CS 通过 `backend/services/gateway_sync.py` 和 `backend/services/common_api.py` 调用，默认通用地址取 `COMMON_API_BASE`，也可通过 `CS_GATEWAY_ROUTES_JSON` 按 `tiktok:<店铺别名>` 或 `tiktok:*` 覆盖地址和调用凭据。CS 读取成功响应的 `ok`、`data.raw` 和分页时的 `data.next_page_token`。

这组接口用于读取。客服回复、公开评价回复、退款和卖家授权回调继续走对应的业务入口。
