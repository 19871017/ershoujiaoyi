-- 0003_im_core.sql
-- IM 会话、消息、回执、游标基础表

CREATE TABLE IF NOT EXISTS chat_conversation (
  id BIGINT PRIMARY KEY,
  conversation_type VARCHAR(32) NOT NULL DEFAULT 'private',
  owner_user_id BIGINT NOT NULL,
  peer_user_id BIGINT NOT NULL,
  related_biz_type VARCHAR(32) NULL,
  related_biz_id BIGINT NULL,
  last_server_seq BIGINT NOT NULL DEFAULT 0,
  last_message_preview VARCHAR(255) NULL,
  last_message_at DATETIME NULL,
  conversation_status VARCHAR(32) NOT NULL DEFAULT 'active',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_chat_conversation_private (owner_user_id, peer_user_id, conversation_type),
  KEY idx_chat_conversation_owner_updated (owner_user_id, updated_at),
  KEY idx_chat_conversation_biz (related_biz_type, related_biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_message (
  id BIGINT PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  server_seq BIGINT NOT NULL,
  client_msg_id VARCHAR(128) NOT NULL,
  server_msg_id VARCHAR(128) NOT NULL,
  sender_id BIGINT NOT NULL,
  receiver_id BIGINT NOT NULL,
  msg_type VARCHAR(32) NOT NULL,
  content_json JSON NOT NULL,
  delivery_status VARCHAR(32) NOT NULL DEFAULT 'sent',
  audit_status VARCHAR(32) NOT NULL DEFAULT 'passed',
  biz_ref_type VARCHAR(32) NULL,
  biz_ref_id BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_chat_message_client (sender_id, client_msg_id),
  UNIQUE KEY uk_chat_message_server (server_msg_id),
  UNIQUE KEY uk_chat_message_seq (conversation_id, server_seq),
  KEY idx_chat_message_receiver_created (receiver_id, created_at),
  KEY idx_chat_message_conversation_created (conversation_id, created_at),
  KEY idx_chat_message_biz_ref (biz_ref_type, biz_ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_message_receipt (
  id BIGINT PRIMARY KEY,
  message_id BIGINT NOT NULL,
  conversation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  receipt_type VARCHAR(32) NOT NULL,
  receipt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_chat_receipt (message_id, user_id, receipt_type),
  KEY idx_chat_receipt_conversation_user (conversation_id, user_id, receipt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_read_cursor (
  id BIGINT PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  read_server_seq BIGINT NOT NULL DEFAULT 0,
  sync_server_seq BIGINT NOT NULL DEFAULT 0,
  unread_count INT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_chat_read_cursor (conversation_id, user_id),
  KEY idx_chat_read_cursor_user (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
