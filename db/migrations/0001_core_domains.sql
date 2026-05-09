-- 0001_core_domains.sql
-- 公共域、配置、审核、风控、后台基础表

CREATE TABLE IF NOT EXISTS system_config (
  id BIGINT PRIMARY KEY,
  config_key VARCHAR(128) NOT NULL,
  config_value TEXT NOT NULL,
  config_type VARCHAR(32) NOT NULL DEFAULT 'string',
  config_group VARCHAR(64) NOT NULL DEFAULT 'default',
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_system_config_key (config_key),
  KEY idx_system_config_group (config_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS content_audit_record (
  id BIGINT PRIMARY KEY,
  audit_no VARCHAR(128) NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_id BIGINT NOT NULL,
  user_id BIGINT NULL,
  content_type VARCHAR(32) NOT NULL,
  audit_status VARCHAR(32) NOT NULL DEFAULT 'pending',
  risk_level VARCHAR(32) NOT NULL DEFAULT 'none',
  risk_reason VARCHAR(512) NULL,
  provider VARCHAR(64) NULL,
  provider_result_json JSON NULL,
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_audit_no (audit_no),
  KEY idx_audit_target (target_type, target_id),
  KEY idx_audit_status_created (audit_status, created_at),
  KEY idx_audit_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS risk_event (
  id BIGINT PRIMARY KEY,
  event_no VARCHAR(128) NOT NULL,
  user_id BIGINT NULL,
  risk_type VARCHAR(64) NOT NULL,
  risk_level VARCHAR(32) NOT NULL,
  biz_type VARCHAR(64) NULL,
  biz_no VARCHAR(128) NULL,
  event_status VARCHAR(32) NOT NULL DEFAULT 'open',
  description VARCHAR(512) NULL,
  evidence_json JSON NULL,
  handled_by BIGINT NULL,
  handled_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_risk_event_no (event_no),
  KEY idx_risk_event_user_created (user_id, created_at),
  KEY idx_risk_event_type_status (risk_type, event_status),
  KEY idx_risk_event_biz (biz_type, biz_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS report_record (
  id BIGINT PRIMARY KEY,
  report_no VARCHAR(128) NOT NULL,
  reporter_id BIGINT NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_id BIGINT NOT NULL,
  reason_code VARCHAR(64) NOT NULL,
  description VARCHAR(512) NULL,
  report_status VARCHAR(32) NOT NULL DEFAULT 'pending',
  handled_by BIGINT NULL,
  handled_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_report_no (report_no),
  KEY idx_report_target (target_type, target_id),
  KEY idx_report_status_created (report_status, created_at),
  KEY idx_report_reporter_created (reporter_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS operation_log (
  id BIGINT PRIMARY KEY,
  operator_id BIGINT NULL,
  operator_type VARCHAR(32) NOT NULL DEFAULT 'system',
  action VARCHAR(128) NOT NULL,
  target_type VARCHAR(64) NULL,
  target_id BIGINT NULL,
  request_id VARCHAR(128) NULL,
  detail_json JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_operation_operator_created (operator_id, created_at),
  KEY idx_operation_target (target_type, target_id),
  KEY idx_operation_action_created (action, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
