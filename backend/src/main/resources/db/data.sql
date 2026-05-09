INSERT INTO system_config (config_key, config_value, config_type, config_group, remark)
SELECT 'platform.category.primary', '女装', 'string', 'product', 'MVP 主分类'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'platform.category.primary');

INSERT INTO system_config (config_key, config_value, config_type, config_group, remark)
SELECT 'platform.category.secondary', '鞋袜,小用品', 'string', 'product', 'MVP 辅助分类'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'platform.category.secondary');

INSERT INTO system_config (config_key, config_value, config_type, config_group, remark)
SELECT 'finance.platform_fee_rate', '0.08', 'decimal', 'finance', '平台服务费率'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'finance.platform_fee_rate');
