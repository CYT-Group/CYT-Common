DO $migration$
DECLARE
    old_service CONSTANT text := 'cs_rma';
    new_service CONSTANT text := 'cs';
    old_operation CONSTANT text := 'cs_rma.buyer.push';
    new_operation CONSTANT text := 'cs.buyer.push';
    sensitive_operation boolean;
BEGIN
    SELECT api.sensitive INTO sensitive_operation
    FROM gateway_custom_api api
    JOIN gateway_module service ON service.key = api.module_key
    WHERE api.key = old_operation
      AND service.key = old_service AND service.service_kind = 'internal';

    IF NOT FOUND THEN
        RETURN;
    END IF;

    IF EXISTS (SELECT 1 FROM gateway_module WHERE key = new_service)
       OR EXISTS (SELECT 1 FROM gateway_custom_api WHERE key = new_operation)
       OR EXISTS (SELECT 1 FROM gateway_custom_api WHERE module_key = old_service AND key <> old_operation)
       OR EXISTS (SELECT 1 FROM gateway_grant WHERE operation_pattern = new_operation) THEN
        RAISE NOTICE 'CS registration rename skipped because an existing configuration requires review';
        RETURN;
    END IF;

    -- A changed sensitivity setting can make namespace wildcards effective. Keep
    -- both the old service and its rules when a rename could change such access.
    IF NOT sensitive_operation AND EXISTS (
        SELECT 1 FROM gateway_grant
        WHERE effect = 'allow' AND operation_pattern IN ('cs_rma.*', 'cs_rma.buyer.*', 'cs.*', 'cs.buyer.*')
          AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
    ) THEN
        RAISE NOTICE 'CS registration rename skipped because a wildcard allow requires review';
        RETURN;
    END IF;

    -- A single exact rule cannot preserve both an allow and an overriding
    -- legacy wildcard deny with an independent expiry.
    IF EXISTS (
        SELECT 1 FROM gateway_grant legacy_deny
        JOIN gateway_grant exact_allow ON exact_allow.system = legacy_deny.system
        WHERE legacy_deny.effect = 'deny' AND legacy_deny.operation_pattern IN ('cs_rma.*', 'cs_rma.buyer.*')
          AND (legacy_deny.expires_at IS NULL OR legacy_deny.expires_at > CURRENT_TIMESTAMP)
          AND exact_allow.operation_pattern = old_operation AND exact_allow.effect = 'allow'
          AND (exact_allow.expires_at IS NULL OR exact_allow.expires_at > CURRENT_TIMESTAMP)
    ) THEN
        RAISE NOTICE 'CS registration rename skipped because a legacy denial overlaps an exact grant';
        RETURN;
    END IF;

    UPDATE gateway_module SET key = new_service, name = 'CS' WHERE key = old_service;
    UPDATE gateway_custom_api SET key = new_operation, module_key = new_service WHERE key = old_operation;
    UPDATE gateway_grant SET operation_pattern = new_operation WHERE operation_pattern = old_operation;

    -- Keep old denials effective for this API without widening them to all CS APIs.
    INSERT INTO gateway_grant (system, operation_pattern, effect, expires_at, granted_by, note)
    SELECT DISTINCT ON (system) system, new_operation, 'deny', expires_at, granted_by, note
    FROM gateway_grant
    WHERE effect = 'deny' AND operation_pattern IN ('cs_rma.*', 'cs_rma.buyer.*')
      AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
    ORDER BY system, expires_at DESC NULLS FIRST, id
    ON CONFLICT (system, operation_pattern) DO UPDATE SET
        effect = 'deny',
        expires_at = CASE
            WHEN gateway_grant.effect = 'allow' THEN EXCLUDED.expires_at
            WHEN gateway_grant.expires_at IS NULL OR EXCLUDED.expires_at IS NULL THEN NULL
            ELSE GREATEST(gateway_grant.expires_at, EXCLUDED.expires_at)
        END;

    UPDATE gateway_op_order SET operation_key = new_operation
    WHERE operation_key = old_operation
      AND NOT EXISTS (SELECT 1 FROM gateway_op_order WHERE operation_key = new_operation);
END;
$migration$;
