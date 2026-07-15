-- Non-destructive report reliability upgrade for existing MySQL 8.0.40 deployments.
-- Run after 20260714_report_permissions_and_bootstrap_admin.sql during the v1.0.0 upgrade.

USE career_ability;

SET @report_column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'report_record' AND column_name = 'filter_city'
);
SET @report_alter := IF(@report_column_exists = 0,
    'ALTER TABLE report_record ADD COLUMN filter_city VARCHAR(100) NULL AFTER time_range_end',
    'SELECT 1');
PREPARE report_alter_statement FROM @report_alter;
EXECUTE report_alter_statement;
DEALLOCATE PREPARE report_alter_statement;

SET @report_column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'report_record' AND column_name = 'filter_position'
);
SET @report_alter := IF(@report_column_exists = 0,
    'ALTER TABLE report_record ADD COLUMN filter_position VARCHAR(100) NULL AFTER filter_city',
    'SELECT 1');
PREPARE report_alter_statement FROM @report_alter;
EXECUTE report_alter_statement;
DEALLOCATE PREPARE report_alter_statement;

SET @report_column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'report_record' AND column_name = 'filter_industry'
);
SET @report_alter := IF(@report_column_exists = 0,
    'ALTER TABLE report_record ADD COLUMN filter_industry VARCHAR(100) NULL AFTER filter_position',
    'SELECT 1');
PREPARE report_alter_statement FROM @report_alter;
EXECUTE report_alter_statement;
DEALLOCATE PREPARE report_alter_statement;

SET @report_column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'report_record' AND column_name = 'analysis_dimensions'
);
SET @report_alter := IF(@report_column_exists = 0,
    'ALTER TABLE report_record ADD COLUMN analysis_dimensions VARCHAR(500) NULL AFTER filter_industry',
    'SELECT 1');
PREPARE report_alter_statement FROM @report_alter;
EXECUTE report_alter_statement;
DEALLOCATE PREPARE report_alter_statement;

SET @report_column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'report_record' AND column_name = 'analysis_scope'
);
SET @report_alter := IF(@report_column_exists = 0,
    'ALTER TABLE report_record ADD COLUMN analysis_scope TEXT NULL AFTER analysis_dimensions',
    'SELECT 1');
PREPARE report_alter_statement FROM @report_alter;
EXECUTE report_alter_statement;
DEALLOCATE PREPARE report_alter_statement;

SET @report_column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'report_record' AND column_name = 'generation_started_at'
);
SET @report_alter := IF(@report_column_exists = 0,
    'ALTER TABLE report_record ADD COLUMN generation_started_at DATETIME NULL AFTER error_msg',
    'SELECT 1');
PREPARE report_alter_statement FROM @report_alter;
EXECUTE report_alter_statement;
DEALLOCATE PREPARE report_alter_statement;

SET @report_column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'report_record' AND column_name = 'generation_attempts'
);
SET @report_alter := IF(@report_column_exists = 0,
    'ALTER TABLE report_record ADD COLUMN generation_attempts INT NOT NULL DEFAULT 0 AFTER generation_started_at',
    'SELECT 1');
PREPARE report_alter_statement FROM @report_alter;
EXECUTE report_alter_statement;
DEALLOCATE PREPARE report_alter_statement;

SET @report_status_index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'report_record'
      AND index_name = 'idx_report_status_update'
);
SET @add_report_status_index := IF(
    @report_status_index_exists = 0,
    'CREATE INDEX idx_report_status_update ON report_record (status, update_time)',
    'SELECT 1'
);
PREPARE report_status_index_statement FROM @add_report_status_index;
EXECUTE report_status_index_statement;
DEALLOCATE PREPARE report_status_index_statement;
