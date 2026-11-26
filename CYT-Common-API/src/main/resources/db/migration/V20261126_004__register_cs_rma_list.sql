INSERT INTO gateway_custom_api
    (key, module_key, name, description, base_url, method, path, auth_type, secret_ref,
     params_schema_json, response_schema_json, response_path, retry_max, timeout_s, sensitive,
     enabled, category, created_by)
VALUES ('cs.rma.list', 'cs', '查询 CS 售后单',
        '按建单时间和交易平台查询已授权店铺的 CS 售后单。返回业务字段和处理状态。',
        '', 'POST', '/api/cs/rma/external/list', 'bearer', 'CUSTOM_CS_RMA_READ_TOKEN',
        '[{"name":"from","type":"string","description":"含起点，带时区的 ISO 8601。省略时查询已授权的历史范围。"},
          {"name":"to","type":"string","description":"不含终点，带时区的 ISO 8601。首次查询省略时使用当前时间。翻页沿用响应的 to。"},
          {"name":"platforms","type":"array","description":"shopify、lingxing 或 aliexpress。省略时使用授权范围。"},
          {"name":"cursor","type":"int","description":"翻页时传上一页的 next_cursor，同步沿用 from、to 和 platforms。"},
          {"name":"limit","type":"int","description":"每页 1 至 100 条，默认 50。"}]',
        '{"type":"object","properties":{
           "items":{"type":"array","items":{"type":"object","properties":{
             "rma_no":{"type":"string"},"created_at":{"type":"string"},"platform":{"type":"string"},
             "business_line":{"type":"string"},"store_domain":{"type":"string"},
             "source_order_no":{"type":["string","null"]},"sku":{"type":["string","null"]},
             "quantity":{"type":["integer","null"]},"type":{"type":["string","null"]},
             "demand":{"type":["string","null"]},"fulfillment_status":{"type":["string","null"]},
             "procurement_state":{"type":["string","null"]}}}},
           "next_cursor":{"type":["integer","null"]},"from":{"type":["string","null"]},
           "to":{"type":"string"},"platforms":{"type":"array","items":{"type":"string"}}}}',
        'data', 1, 10, true, false, '售后联动', 'migration')
ON CONFLICT (key) DO NOTHING;
