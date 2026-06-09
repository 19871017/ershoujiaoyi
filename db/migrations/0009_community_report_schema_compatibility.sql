-- 0009_community_report_schema_compatibility.sql
-- Align older migration-created databases with the current community/report schema.

SET @rename_user_follow_followee_sql := (
  SELECT IF(
    SUM(COLUMN_NAME = 'followee_id') > 0 AND SUM(COLUMN_NAME = 'followed_id') = 0,
    'ALTER TABLE user_follow CHANGE COLUMN followee_id followed_id BIGINT NOT NULL',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_follow'
    AND COLUMN_NAME IN ('followee_id', 'followed_id')
);

PREPARE rename_user_follow_followee_stmt FROM @rename_user_follow_followee_sql;
EXECUTE rename_user_follow_followee_stmt;
DEALLOCATE PREPARE rename_user_follow_followee_stmt;

SET @add_user_follow_followed_idx_sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE user_follow ADD INDEX idx_user_follow_followed (followed_id, created_at)',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_follow'
    AND INDEX_NAME = 'idx_user_follow_followed'
);

PREPARE add_user_follow_followed_idx_stmt FROM @add_user_follow_followed_idx_sql;
EXECUTE add_user_follow_followed_idx_stmt;
DEALLOCATE PREPARE add_user_follow_followed_idx_stmt;

SET @modify_report_record_target_id_sql := (
  SELECT IF(
    COUNT(*) > 0,
    'ALTER TABLE report_record MODIFY COLUMN target_id VARCHAR(128) NOT NULL',
    'SELECT 1'
  )
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'report_record'
    AND COLUMN_NAME = 'target_id'
    AND DATA_TYPE <> 'varchar'
);

PREPARE modify_report_record_target_id_stmt FROM @modify_report_record_target_id_sql;
EXECUTE modify_report_record_target_id_stmt;
DEALLOCATE PREPARE modify_report_record_target_id_stmt;
