-- 0002_user_identity.sql
-- 用户、资料、身份认证、标签、关系基础表

CREATE TABLE IF NOT EXISTS user_account (
  id BIGINT PRIMARY KEY,
  mobile VARCHAR(32) NULL,
  email VARCHAR(128) NULL,
  union_id VARCHAR(128) NULL,
  account_status VARCHAR(32) NOT NULL DEFAULT 'active',
  main_role VARCHAR(32) NOT NULL DEFAULT 'unknown',
  nickname VARCHAR(64) NULL,
  avatar_url VARCHAR(512) NULL,
  gender VARCHAR(16) NULL,
  region_code VARCHAR(32) NULL,
  region_level VARCHAR(32) NULL,
  auth_level INT NOT NULL DEFAULT 0,
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_account_mobile (mobile),
  UNIQUE KEY uk_user_account_email (email),
  UNIQUE KEY uk_user_account_union_id (union_id),
  KEY idx_user_account_status_created (account_status, created_at),
  KEY idx_user_account_region (region_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_profile (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  bio VARCHAR(512) NULL,
  birthday DATE NULL,
  province_code VARCHAR(32) NULL,
  city_code VARCHAR(32) NULL,
  occupation VARCHAR(64) NULL,
  tags_json JSON NULL,
  visibility_level VARCHAR(32) NOT NULL DEFAULT 'public',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_profile_user_id (user_id),
  KEY idx_user_profile_city (city_code),
  KEY idx_user_profile_province (province_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_identity (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  identity_type VARCHAR(32) NOT NULL,
  version INT NOT NULL DEFAULT 1,
  identity_status VARCHAR(32) NOT NULL,
  review_task_id BIGINT NULL,
  review_result_id BIGINT NULL,
  label_codes VARCHAR(512) NULL,
  valid_from DATETIME NULL,
  valid_to DATETIME NULL,
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_identity_version (user_id, identity_type, version),
  KEY idx_user_identity_user_type_status (user_id, identity_type, identity_status),
  KEY idx_user_identity_status_updated (identity_status, updated_at),
  KEY idx_user_identity_review_task (review_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_tag (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  tag_code VARCHAR(64) NOT NULL,
  tag_source VARCHAR(64) NOT NULL,
  tag_status VARCHAR(32) NOT NULL DEFAULT 'active',
  start_at DATETIME NULL,
  end_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_tag_user_code (user_id, tag_code),
  KEY idx_user_tag_status (tag_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_follow (
  id BIGINT PRIMARY KEY,
  follower_id BIGINT NOT NULL,
  followee_id BIGINT NOT NULL,
  relation_status VARCHAR(32) NOT NULL DEFAULT 'following',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_follow_pair (follower_id, followee_id),
  KEY idx_user_follow_followee (followee_id, created_at),
  KEY idx_user_follow_follower (follower_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_block (
  id BIGINT PRIMARY KEY,
  blocker_id BIGINT NOT NULL,
  blocked_id BIGINT NOT NULL,
  reason VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_block_pair (blocker_id, blocked_id),
  KEY idx_user_block_blocked (blocked_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
