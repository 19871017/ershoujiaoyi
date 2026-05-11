INSERT INTO system_config (config_key, config_value, config_type, config_group, remark)
SELECT 'platform.category.primary', '女装', 'string', 'product', 'MVP 主分类'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'platform.category.primary');

INSERT INTO system_config (config_key, config_value, config_type, config_group, remark)
SELECT 'platform.category.secondary', '鞋袜,小用品', 'string', 'product', 'MVP 辅助分类'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'platform.category.secondary');

INSERT INTO system_config (config_key, config_value, config_type, config_group, remark)
SELECT 'finance.platform_fee_rate', '0.08', 'decimal', 'finance', '平台服务费率'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'finance.platform_fee_rate');

INSERT INTO user_account (user_no, phone, password_hash, nickname, status, created_at, updated_at)
SELECT 'UADMINSMOKE0000001', '13800138000', 'pbkdf2$120000$eHlxLWFkbWluLXNlZWQhIQ==$mv7n99HlN6RAsVtf7C5NA58RQY+hfgehxhnQ44kJZWs=', '小原圈管理员', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE phone = '13800138000');

INSERT INTO user_profile (user_id, gender, city, bio, identity_status, main_role, video_identity_status, video_verified, created_at, updated_at)
SELECT id, NULL, NULL, '后台真实登录验通账号', 'UNVERIFIED', 'BUYER', 'UNVERIFIED', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM user_account
WHERE phone = '13800138000'
  AND NOT EXISTS (SELECT 1 FROM user_profile WHERE user_id = user_account.id);

INSERT INTO admin_user_permission (user_id, permission_code, enabled, created_at, updated_at)
SELECT id, permission_code, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM user_account
CROSS JOIN (
  SELECT 'audit:read' AS permission_code
  UNION ALL SELECT 'audit:review'
  UNION ALL SELECT 'finance:read'
  UNION ALL SELECT 'finance:review'
  UNION ALL SELECT 'user:read'
  UNION ALL SELECT 'order:read'
  UNION ALL SELECT 'after-sales:read'
  UNION ALL SELECT 'after-sales:review'
  UNION ALL SELECT 'system:config'
  UNION ALL SELECT 'audit:log'
) permissions
WHERE phone = '13800138000'
  AND NOT EXISTS (
    SELECT 1
    FROM admin_user_permission existing
    WHERE existing.user_id = user_account.id
      AND existing.permission_code = permissions.permission_code
  );
