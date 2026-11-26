UPDATE gateway_custom_api AS api
SET params_schema_json = COALESCE((
        SELECT jsonb_agg(
            CASE param.value->>'name'
                WHEN 'platforms' THEN param.value || jsonb_build_object(
                    'description', '可选。传入后仅返回所选交易平台。省略时查询授权范围内的全部平台，全量授权也包含未归属单。')
                WHEN 'limit' THEN param.value || jsonb_build_object(
                    'description', '每页 1 至 100 条。全量授权默认 100，普通授权默认 50。')
                ELSE param.value
            END ORDER BY param.position)::text
        FROM jsonb_array_elements(api.params_schema_json::jsonb) WITH ORDINALITY AS param(value, position)
    ), api.params_schema_json),
    response_schema_json = jsonb_set(
        jsonb_set(api.response_schema_json::jsonb,
            '{properties,items,items,properties,platform,type}', '["string","null"]'::jsonb, false),
        '{properties,items,items,properties,store_domain,type}', '["string","null"]'::jsonb, false
    )::text
WHERE api.key = 'cs.rma.list' AND api.path = '/api/cs/rma/external/list';
