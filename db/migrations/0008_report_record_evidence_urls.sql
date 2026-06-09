-- 0008_report_record_evidence_urls.sql
-- Store report evidence URLs separately from the 512-character description field.

SET @add_report_record_evidence_urls_sql := (
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

PREPARE add_report_record_evidence_urls_stmt FROM @add_report_record_evidence_urls_sql;
EXECUTE add_report_record_evidence_urls_stmt;
DEALLOCATE PREPARE add_report_record_evidence_urls_stmt;
