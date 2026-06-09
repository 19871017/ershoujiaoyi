-- 0010_video_identity_schema_compatibility.sql
-- Keep migration-created databases compatible with seller VIDEO_IDENTITY review and media ticket flows.

SET @add_user_profile_identity_status_sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE user_profile ADD COLUMN identity_status VARCHAR(32) NOT NULL DEFAULT ''UNVERIFIED'' AFTER bio',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_profile'
    AND COLUMN_NAME = 'identity_status'
);

PREPARE add_user_profile_identity_status_stmt FROM @add_user_profile_identity_status_sql;
EXECUTE add_user_profile_identity_status_stmt;
DEALLOCATE PREPARE add_user_profile_identity_status_stmt;

SET @add_user_profile_main_role_sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE user_profile ADD COLUMN main_role VARCHAR(32) NOT NULL DEFAULT ''BUYER'' AFTER identity_status',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_profile'
    AND COLUMN_NAME = 'main_role'
);

PREPARE add_user_profile_main_role_stmt FROM @add_user_profile_main_role_sql;
EXECUTE add_user_profile_main_role_stmt;
DEALLOCATE PREPARE add_user_profile_main_role_stmt;

SET @add_user_profile_video_identity_status_sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE user_profile ADD COLUMN video_identity_status VARCHAR(32) NOT NULL DEFAULT ''UNVERIFIED'' AFTER main_role',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_profile'
    AND COLUMN_NAME = 'video_identity_status'
);

PREPARE add_user_profile_video_identity_status_stmt FROM @add_user_profile_video_identity_status_sql;
EXECUTE add_user_profile_video_identity_status_stmt;
DEALLOCATE PREPARE add_user_profile_video_identity_status_stmt;

SET @add_user_profile_video_verified_sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE user_profile ADD COLUMN video_verified TINYINT(1) NOT NULL DEFAULT 0 AFTER video_identity_status',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_profile'
    AND COLUMN_NAME = 'video_verified'
);

PREPARE add_user_profile_video_verified_stmt FROM @add_user_profile_video_verified_sql;
EXECUTE add_user_profile_video_verified_stmt;
DEALLOCATE PREPARE add_user_profile_video_verified_stmt;

CREATE TABLE IF NOT EXISTS media_upload_ticket (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  ticket_no VARCHAR(128) NOT NULL,
  owner_user_id BIGINT NOT NULL,
  scene VARCHAR(64) NOT NULL,
  original_filename VARCHAR(255) NULL,
  content_type VARCHAR(64) NOT NULL,
  file_size BIGINT NOT NULL,
  storage_url VARCHAR(512) NOT NULL,
  upload_token_hash VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ISSUED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at DATETIME NOT NULL,
  UNIQUE KEY uk_media_upload_ticket_no (ticket_no),
  UNIQUE KEY uk_media_upload_ticket_storage_url (storage_url),
  KEY idx_media_upload_ticket_owner_scene_status (owner_user_id, scene, status),
  KEY idx_media_upload_ticket_scene_created (scene, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_record (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  audit_no VARCHAR(128) NOT NULL,
  audit_type VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_id VARCHAR(128) NOT NULL,
  reason VARCHAR(128) NOT NULL,
  description VARCHAR(512) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  review_remark VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at DATETIME NULL,
  UNIQUE KEY uk_audit_record_no (audit_no),
  KEY idx_audit_record_type_status (audit_type, status, created_at),
  KEY idx_audit_record_target (target_type, target_id),
  KEY idx_audit_record_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  action VARCHAR(64) NOT NULL,
  operator_id BIGINT NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_id VARCHAR(128) NOT NULL,
  result VARCHAR(32) NOT NULL,
  summary VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_admin_audit_log_created (created_at, id),
  KEY idx_admin_audit_log_target (target_type, target_id),
  KEY idx_admin_audit_log_operator (operator_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @add_report_record_evidence_urls_for_video_identity_sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE report_record ADD COLUMN evidence_urls TEXT NULL AFTER description',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'report_record'
    AND COLUMN_NAME = 'evidence_urls'
);

PREPARE add_report_record_evidence_urls_for_video_identity_stmt FROM @add_report_record_evidence_urls_for_video_identity_sql;
EXECUTE add_report_record_evidence_urls_for_video_identity_stmt;
DEALLOCATE PREPARE add_report_record_evidence_urls_for_video_identity_stmt;

