UPDATE gateway_custom_api
SET response_schema_json = $contract$
{
  "type": "object",
  "description": "本页的 CS 售后单和已同步的 TikTok Shop 退货退款记录。",
  "required": ["items", "next_cursor", "from", "to", "platforms"],
  "properties": {
    "from": {
      "type": ["string", "null"],
      "format": "date-time",
      "description": "本次查询的时间起点，包含该时刻。null 表示查询授权范围内的历史记录，起点不限。",
      "examples": [null, "2026-11-01T00:00:00+08:00"]
    },
    "to": {
      "type": "string",
      "format": "date-time",
      "description": "本次查询的时间终点，不包含该时刻。首次未传则取请求时刻，翻页时沿用此值。",
      "examples": ["2026-11-26T15:00:00+08:00"]
    },
    "next_cursor": {
      "type": ["integer", "null"],
      "description": "下一页的游标。null 表示已到最后一页。翻页时原样传入 cursor，并沿用当前的 from、to 和 platforms。",
      "examples": [1024, null]
    },
    "platforms": {
      "type": "array",
      "description": "本次查询的平台范围。全量授权且未指定平台时，items 也可能包含平台未归属的记录。",
      "examples": [["shopify", "tiktok"]],
      "items": {"type": "string", "enum": ["shopify", "lingxing", "aliexpress", "tiktok"]}
    },
    "items": {
      "type": "array",
      "description": "当前页的售后记录。没有匹配条件的记录时返回空数组。",
      "examples": [[]],
      "items": {
        "type": "object",
        "required": ["rma_no", "created_at", "platform", "business_line", "store_domain", "source_order_no", "sku", "quantity", "type", "demand", "fulfillment_status", "procurement_state", "source", "store_account", "platform_return_id"],
        "properties": {
          "rma_no": {
            "type": "string",
            "description": "售后记录编号。CS 返回原售后单号，TikTok 使用 TIKTOK:<店铺别名>:<平台售后单号>。",
            "examples": ["RMA-CS-20261101-01", "TIKTOK:epomaker-keyboard:1234567890123456789"]
          },
          "created_at": {
            "type": "string",
            "format": "date-time",
            "description": "用于时间筛选的建单时间，按北京时间返回。若 TikTok 没有提供建单时间，则使用平台更新时间。",
            "examples": ["2026-11-01T09:30:00+08:00"]
          },
          "platform": {
            "type": ["string", "null"],
            "description": "原订单的交易平台。shopify 为 Shopify，lingxing 为经领星接入的亚马逊，aliexpress 为速卖通，tiktok 为 TikTok Shop；历史单未归属时为 null。",
            "examples": ["shopify", null]
          },
          "business_line": {
            "type": ["string", "null"],
            "description": "业务线标识，例如 keyboard（键盘）或 earphone（耳机）。未登记时为 null。",
            "examples": ["keyboard"]
          },
          "store_domain": {
            "type": ["string", "null"],
            "description": "CS 记录的原订单店铺域名。未归属时为 null。TikTok 店铺通过 store_account 标识。",
            "examples": ["sample-store.myshopify.com", null]
          },
          "source_order_no": {
            "type": ["string", "null"],
            "description": "原交易订单号。CS 优先取平台订单号，其次取已登记的订单号。未登记时为 null，按字符串处理。",
            "examples": ["ORDER-10001"]
          },
          "sku": {
            "type": ["string", "null"],
            "description": "CS 售后单登记的商品 SKU。历史单未登记或 TikTok 退货退款记录未提供时为 null。",
            "examples": ["SKU-DEMO-01"]
          },
          "quantity": {
            "type": ["integer", "null"],
            "description": "CS 售后单登记的商品数量，单位为件。历史单未登记或 TikTok 退货退款记录未提供时为 null。",
            "examples": [1]
          },
          "type": {
            "type": ["string", "null"],
            "description": "售后记录分类。CS 返回原分类；TikTok 退货退款为 return_refund，仅退款为 refund。",
            "examples": ["refund", "return_refund"]
          },
          "demand": {
            "type": ["string", "null"],
            "description": "实际售后诉求。例如 CS 的 refund（退货退款）、refund_only（仅退款）、exchange（换货）、repair（维修）、reship（补发配件）。TikTok 退货退款返回 return_refund，仅退款返回 refund_only。",
            "examples": ["refund_only"]
          },
          "fulfillment_status": {
            "type": ["string", "null"],
            "description": "履约状态。CS 返回工作台状态码，例如 pending_review（待审核）；TikTok 返回平台原始退货退款状态码。未登记时为 null。",
            "examples": ["pending_review", "AWAITING_BUYER_SHIP"]
          },
          "procurement_state": {
            "type": ["string", "null"],
            "description": "CS 记录的采购处理状态。例如 none（尚无采购状态）、pending（待采购）、accepted（采购已接单）。TikTok 退货退款记录此字段为 null。",
            "examples": ["none", null]
          },
          "source": {
            "type": "string",
            "enum": ["cs", "tiktok_shop"],
            "description": "记录来源。cs 表示 CS 售后工作台，tiktok_shop 表示从 TikTok Shop 同步的退货退款记录。",
            "examples": ["cs"]
          },
          "store_account": {
            "type": ["string", "null"],
            "description": "TikTok Shop 店铺在 Common 中登记的别名。CS 工作台记录此字段为 null。",
            "examples": ["epomaker-keyboard", null]
          },
          "platform_return_id": {
            "type": ["string", "null"],
            "description": "TikTok Shop 原始退货退款单号，按字符串处理。CS 工作台记录此字段为 null。",
            "examples": ["1234567890123456789", null]
          }
        }
      }
    }
  }
}
$contract$
WHERE key = 'cs.rma.list' AND path = '/api/cs/rma/external/list';
