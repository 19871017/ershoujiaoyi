-- 0007_community_related_product.sql
-- Community posts can optionally link to one approved product owned by the post author.

CREATE TABLE IF NOT EXISTS community_post (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  post_no VARCHAR(128) NOT NULL,
  author_id BIGINT NOT NULL,
  title VARCHAR(128) NOT NULL,
  topic VARCHAR(64) NOT NULL,
  content TEXT NOT NULL,
  image_urls TEXT NULL,
  related_product_id BIGINT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
  like_count INT NOT NULL DEFAULT 0,
  comment_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_community_post_no (post_no),
  KEY idx_community_post_author_created (author_id, created_at),
  KEY idx_community_post_topic_created (topic, created_at),
  KEY idx_community_post_status_created (status, created_at),
  KEY idx_community_post_related_product (related_product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS community_comment (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  comment_no VARCHAR(128) NOT NULL,
  post_id BIGINT NOT NULL,
  author_id BIGINT NOT NULL,
  content VARCHAR(512) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_community_comment_no (comment_no),
  KEY idx_community_comment_post_created (post_id, created_at),
  KEY idx_community_comment_author_created (author_id, created_at),
  KEY idx_community_comment_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS community_like (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_community_like_post_user (post_id, user_id),
  KEY idx_community_like_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @add_community_post_related_product_sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE community_post ADD COLUMN related_product_id BIGINT NULL AFTER image_urls',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'community_post'
    AND COLUMN_NAME = 'related_product_id'
);

PREPARE add_community_post_related_product_stmt FROM @add_community_post_related_product_sql;
EXECUTE add_community_post_related_product_stmt;
DEALLOCATE PREPARE add_community_post_related_product_stmt;

SET @add_community_post_related_product_idx_sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE community_post ADD INDEX idx_community_post_related_product (related_product_id)',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'community_post'
    AND INDEX_NAME = 'idx_community_post_related_product'
);

PREPARE add_community_post_related_product_idx_stmt FROM @add_community_post_related_product_idx_sql;
EXECUTE add_community_post_related_product_idx_stmt;
DEALLOCATE PREPARE add_community_post_related_product_idx_stmt;
