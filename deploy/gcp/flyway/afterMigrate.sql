INSERT INTO gateway_environment (key, label, description, is_production, enabled, created_by)
VALUES ('production', '生产环境', 'Common 生产运行环境', TRUE, TRUE, 'migration')
ON CONFLICT (key) DO NOTHING;
