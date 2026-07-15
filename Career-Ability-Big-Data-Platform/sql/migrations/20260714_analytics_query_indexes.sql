-- C-05: composite indexes used by the paged position search and daily analytics queries.
-- The statements are idempotent so the migration can be included in an existing deployment runbook.
SET @schema_name := DATABASE();

SET @index_name := 'idx_jp_city_publish_date';
SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = @schema_name AND table_name = 'job_position' AND index_name = @index_name
);
SET @statement := IF(@index_exists = 0,
    'CREATE INDEX idx_jp_city_publish_date ON job_position(city, publish_date, id)',
    'SELECT 1');
PREPARE analytics_index_statement FROM @statement;
EXECUTE analytics_index_statement;
DEALLOCATE PREPARE analytics_index_statement;

SET @index_name := 'idx_jp_education_publish_date';
SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = @schema_name AND table_name = 'job_position' AND index_name = @index_name
);
SET @statement := IF(@index_exists = 0,
    'CREATE INDEX idx_jp_education_publish_date ON job_position(education, publish_date)',
    'SELECT 1');
PREPARE analytics_index_statement FROM @statement;
EXECUTE analytics_index_statement;
DEALLOCATE PREPARE analytics_index_statement;

SET @index_name := 'idx_jp_company_publish_date';
SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = @schema_name AND table_name = 'job_position' AND index_name = @index_name
);
SET @statement := IF(@index_exists = 0,
    'CREATE INDEX idx_jp_company_publish_date ON job_position(company_id, publish_date)',
    'SELECT 1');
PREPARE analytics_index_statement FROM @statement;
EXECUTE analytics_index_statement;
DEALLOCATE PREPARE analytics_index_statement;

SET @index_name := 'idx_jc_industry';
SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = @schema_name AND table_name = 'job_company' AND index_name = @index_name
);
SET @statement := IF(@index_exists = 0,
    'CREATE INDEX idx_jc_industry ON job_company(industry)',
    'SELECT 1');
PREPARE analytics_index_statement FROM @statement;
EXECUTE analytics_index_statement;
DEALLOCATE PREPARE analytics_index_statement;
