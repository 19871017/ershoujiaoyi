-- 0006_runtime_schema_hardening.sql
-- Remove reliance on application runtime DDL for banners, admin login lockout, and user number changes.

CREATE TABLE IF NOT EXISTS home_banner (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  kicker VARCHAR(64) NOT NULL,
  title VARCHAR(80) NOT NULL,
  description VARCHAR(160) NOT NULL,
  cta VARCHAR(32) NOT NULL,
  image_url VARCHAR(512) NOT NULL,
  action VARCHAR(32) NOT NULL DEFAULT 'none',
  placement VARCHAR(32) NOT NULL DEFAULT 'HOME',
  sort_order INT NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_home_banner_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_login_attempt (
  mobile VARCHAR(32) PRIMARY KEY,
  failed_count INT NOT NULL,
  locked_until DATETIME NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_no_change_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  old_user_no VARCHAR(64) NOT NULL,
  new_user_no VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_no_change_log_user_id (user_id),
  KEY idx_user_no_change_log_new_no (new_user_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @add_home_banner_placement_sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE home_banner ADD COLUMN placement VARCHAR(32) NOT NULL DEFAULT ''HOME'' AFTER action',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'home_banner'
    AND COLUMN_NAME = 'placement'
);

PREPARE add_home_banner_placement_stmt FROM @add_home_banner_placement_sql;
EXECUTE add_home_banner_placement_stmt;
DEALLOCATE PREPARE add_home_banner_placement_stmt;
