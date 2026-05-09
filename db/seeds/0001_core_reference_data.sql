-- 0001_core_reference_data.sql
-- MVP 初始配置与礼物种子数据

INSERT INTO system_config (id, config_key, config_value, config_type, config_group, remark)
VALUES
  (1001, 'buyer.auto_verify.recharge_amount', '10', 'number', 'auth', '充值满 10 元自动认证买家'),
  (1002, 'buyer.auto_verify.gift_amount', '10', 'number', 'auth', '送礼满 10 元自动认证买家'),
  (2001, 'goods.service_fee_rate', '0.1000', 'number', 'finance', '商品平台服务费默认 10%'),
  (2002, 'gift.platform_rate', '0.5000', 'number', 'finance', '礼物平台默认抽成 50%')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), updated_at = CURRENT_TIMESTAMP;

INSERT INTO gift_config (id, gift_code, gift_name, price, charm_value, platform_rate, gift_status, sort_order)
VALUES
  (1, 'flower', '小花', 1.00, 1, 0.5000, 'active', 1),
  (2, 'coffee', '咖啡', 3.00, 3, 0.5000, 'active', 2),
  (3, 'cake', '蛋糕', 6.00, 6, 0.5000, 'active', 3),
  (4, 'perfume', '香水', 10.00, 10, 0.5000, 'active', 4),
  (5, 'lipstick', '口红', 20.00, 20, 0.5000, 'active', 5),
  (6, 'necklace', '项链', 50.00, 50, 0.5000, 'active', 6),
  (7, 'crown', '皇冠', 100.00, 100, 0.5000, 'active', 7),
  (8, 'diamond', '钻石', 200.00, 200, 0.5000, 'active', 8),
  (9, 'star', '星河', 500.00, 500, 0.5000, 'active', 9),
  (10, 'castle', '城堡', 1000.00, 1000, 0.5000, 'active', 10)
ON DUPLICATE KEY UPDATE price = VALUES(price), charm_value = VALUES(charm_value), updated_at = CURRENT_TIMESTAMP;
