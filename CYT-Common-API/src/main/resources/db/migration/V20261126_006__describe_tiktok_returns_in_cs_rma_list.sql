UPDATE gateway_custom_api AS api
SET params_schema_json = COALESCE((
        SELECT jsonb_agg(
            CASE param.value->>'name'
                WHEN 'platforms' THEN param.value || jsonb_build_object(
                    'description', '可选。支持 shopify、lingxing、aliexpress、tiktok。省略时包含授权范围内的全部平台，全量授权也包含未归属单。')
                ELSE param.value
            END ORDER BY param.position)::text
        FROM jsonb_array_elements(api.params_schema_json::jsonb) WITH ORDINALITY AS param(value, position)
    ), api.params_schema_json),
    response_schema_json = jsonb_set(api.response_schema_json::jsonb,
        '{properties,items,items,properties}',
        (api.response_schema_json::jsonb #> '{properties,items,items,properties}') ||
        '{"source":{"type":"string","description":"cs 或 tiktok_shop"},
          "store_account":{"type":["string","null"],"description":"TikTok 店铺别名"},
          "platform_return_id":{"type":["string","null"],"description":"TikTok 退货退款单号"},
          "business_line":{"type":["string","null"]}}'::jsonb,
        false)::text
WHERE api.key = 'cs.rma.list' AND api.path = '/api/cs/rma/external/list';
