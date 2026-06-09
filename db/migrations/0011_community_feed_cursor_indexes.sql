-- 0011_community_feed_cursor_indexes.sql
-- Support stable community feed cursor pagination by status/topic and created_at + id.

SET @add_community_post_status_created_id_idx_sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE community_post ADD INDEX idx_community_post_status_created_id (status, created_at, id)',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'community_post'
    AND INDEX_NAME = 'idx_community_post_status_created_id'
);

PREPARE add_community_post_status_created_id_idx_stmt FROM @add_community_post_status_created_id_idx_sql;
EXECUTE add_community_post_status_created_id_idx_stmt;
DEALLOCATE PREPARE add_community_post_status_created_id_idx_stmt;

SET @add_community_post_status_topic_created_id_idx_sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE community_post ADD INDEX idx_community_post_status_topic_created_id (status, topic, created_at, id)',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'community_post'
    AND INDEX_NAME = 'idx_community_post_status_topic_created_id'
);

PREPARE add_community_post_status_topic_created_id_idx_stmt FROM @add_community_post_status_topic_created_id_idx_sql;
EXECUTE add_community_post_status_topic_created_id_idx_stmt;
DEALLOCATE PREPARE add_community_post_status_topic_created_id_idx_stmt;
