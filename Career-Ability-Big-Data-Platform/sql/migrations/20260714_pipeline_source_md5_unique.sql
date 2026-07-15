-- Make source_md5 the final persistence boundary for replayed ETL messages.
-- Existing duplicate rows are reduced to their earliest record before adding
-- the unique key.  The schema permits multiple NULL values for legacy rows.

DELETE duplicate_position
FROM job_position AS duplicate_position
JOIN job_position AS retained_position
  ON duplicate_position.source_md5 = retained_position.source_md5
 AND duplicate_position.id > retained_position.id
WHERE duplicate_position.source_md5 IS NOT NULL;

SET @has_unique_source_md5 := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'job_position'
      AND index_name = 'uq_job_position_source_md5'
);
SET @add_unique_source_md5 := IF(
    @has_unique_source_md5 = 0,
    'ALTER TABLE job_position ADD UNIQUE KEY uq_job_position_source_md5 (source_md5)',
    'SELECT 1'
);
PREPARE pipeline_unique_statement FROM @add_unique_source_md5;
EXECUTE pipeline_unique_statement;
DEALLOCATE PREPARE pipeline_unique_statement;
