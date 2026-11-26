ALTER TABLE gateway_custom_api
    ADD COLUMN unwrap_response boolean NOT NULL DEFAULT false;

UPDATE gateway_custom_api
SET unwrap_response = true
WHERE key = 'cs.rma.list'
  AND module_key = 'cs'
  AND path = '/api/cs/rma/external/list';
