-- 0005_trade_order.sql
-- 商品、交易准则、订单、礼物基础表

CREATE TABLE IF NOT EXISTS goods (
  id BIGINT PRIMARY KEY,
  seller_id BIGINT NOT NULL,
  title VARCHAR(128) NOT NULL,
  description TEXT NULL,
  category_id BIGINT NULL,
  price DECIMAL(18,2) NOT NULL,
  goods_status VARCHAR(32) NOT NULL DEFAULT 'draft',
  audit_status VARCHAR(32) NOT NULL DEFAULT 'pending',
  province_code VARCHAR(32) NULL,
  city_code VARCHAR(32) NULL,
  published_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_goods_seller_status (seller_id, goods_status),
  KEY idx_goods_status_created (goods_status, created_at),
  KEY idx_goods_region (province_code, city_code),
  KEY idx_goods_category (category_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS goods_image (
  id BIGINT PRIMARY KEY,
  goods_id BIGINT NOT NULL,
  image_url VARCHAR(512) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  audit_status VARCHAR(32) NOT NULL DEFAULT 'pending',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_goods_image_goods_sort (goods_id, sort_order),
  KEY idx_goods_image_audit (audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS goods_trade_rule (
  id BIGINT PRIMARY KEY,
  goods_id BIGINT NOT NULL,
  is_free_shipping TINYINT NOT NULL DEFAULT 0,
  allow_bargain TINYINT NOT NULL DEFAULT 0,
  allow_return TINYINT NOT NULL DEFAULT 0,
  allow_offline_meet TINYINT NOT NULL DEFAULT 0,
  shipping_days INT NULL,
  forbidden_note VARCHAR(512) NULL,
  special_note VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_goods_trade_rule_goods (goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trade_order (
  id BIGINT PRIMARY KEY,
  order_no VARCHAR(128) NOT NULL,
  buyer_id BIGINT NOT NULL,
  seller_id BIGINT NOT NULL,
  goods_id BIGINT NOT NULL,
  goods_price DECIMAL(18,2) NOT NULL,
  service_fee DECIMAL(18,2) NOT NULL DEFAULT 0.00,
  pay_amount DECIMAL(18,2) NOT NULL,
  order_status VARCHAR(32) NOT NULL DEFAULT 'pending_pay',
  payment_status VARCHAR(32) NOT NULL DEFAULT 'unpaid',
  rate_snapshot_json JSON NULL,
  rule_snapshot_json JSON NULL,
  paid_at DATETIME NULL,
  completed_at DATETIME NULL,
  closed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_trade_order_no (order_no),
  KEY idx_trade_order_buyer_created (buyer_id, created_at),
  KEY idx_trade_order_seller_created (seller_id, created_at),
  KEY idx_trade_order_goods (goods_id),
  KEY idx_trade_order_status_created (order_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gift_config (
  id BIGINT PRIMARY KEY,
  gift_code VARCHAR(64) NOT NULL,
  gift_name VARCHAR(64) NOT NULL,
  price DECIMAL(18,2) NOT NULL,
  charm_value INT NOT NULL DEFAULT 0,
  platform_rate DECIMAL(6,4) NOT NULL DEFAULT 0.0000,
  gift_status VARCHAR(32) NOT NULL DEFAULT 'active',
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_gift_config_code (gift_code),
  KEY idx_gift_config_status_sort (gift_status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gift_order (
  id BIGINT PRIMARY KEY,
  gift_order_no VARCHAR(128) NOT NULL,
  gift_id BIGINT NOT NULL,
  sender_id BIGINT NOT NULL,
  receiver_id BIGINT NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  platform_income DECIMAL(18,2) NOT NULL DEFAULT 0.00,
  receiver_income DECIMAL(18,2) NOT NULL DEFAULT 0.00,
  order_status VARCHAR(32) NOT NULL DEFAULT 'created',
  rate_snapshot_json JSON NULL,
  scene_type VARCHAR(64) NULL,
  scene_id BIGINT NULL,
  paid_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_gift_order_no (gift_order_no),
  KEY idx_gift_sender_created (sender_id, created_at),
  KEY idx_gift_receiver_created (receiver_id, created_at),
  KEY idx_gift_scene (scene_type, scene_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
