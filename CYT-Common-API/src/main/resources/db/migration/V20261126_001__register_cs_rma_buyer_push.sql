INSERT INTO gateway_module
    (key, name, description, service_kind, base_url, auth_type, enabled, created_by)
VALUES ('cs_rma', '客服中台售后单', '客服中台提供的售后单联动能力', 'internal', '', 'none', false, 'migration')
ON CONFLICT (key) DO NOTHING;

INSERT INTO gateway_custom_api
    (key, module_key, name, description, base_url, method, path, auth_type, secret_ref,
     params_schema_json, response_path, retry_max, timeout_s, sensitive, enabled, category, created_by)
VALUES ('cs_rma.buyer.push', 'cs_rma', '推送客服售后单到采购中台',
        '按建单时间和交易平台登记异步批次。省略起点需有历史权限。同一逻辑请求复用 request_id。',
        '', 'POST', '/api/cs/rma/external/buyer/push', 'bearer', 'CUSTOM_CS_RMA_BUYER_PUSH_TOKEN',
        '[{"name":"request_id","type":"string","required":true,"description":"调用方生成的 UUID。重试复用原值与原参数。"},
          {"name":"from","type":"string","description":"建单时间起点，含该时刻，带时区的 ISO 8601。省略表示从历史起点扫描。"},
          {"name":"to","type":"string","description":"建单时间终点，不含该时刻，带时区的 ISO 8601。省略为首次请求时间。"},
          {"name":"platforms","type":"array","description":"交易平台列表。shopify、lingxing（亚马逊经领星）、aliexpress。省略使用授权允许的所有平台。"}]',
        'data', 1, 10, true, false, '售后联动', 'migration')
ON CONFLICT (key) DO NOTHING;
