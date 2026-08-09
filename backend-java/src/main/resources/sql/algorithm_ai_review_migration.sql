-- Idempotent migration for an existing algorithm_submission table.
-- Run this before deploying the Java application version that requires the
-- AI review columns. Existing judge fields and rows are not modified.

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'algorithm_submission'
     AND COLUMN_NAME = 'ai_status') = 0,
  'ALTER TABLE algorithm_submission ADD COLUMN ai_status VARCHAR(32) NULL AFTER error_message',
  'SELECT 1'
);
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'algorithm_submission'
     AND COLUMN_NAME = 'ai_score') = 0,
  'ALTER TABLE algorithm_submission ADD COLUMN ai_score INT NULL AFTER ai_status',
  'SELECT 1'
);
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'algorithm_submission'
     AND COLUMN_NAME = 'ai_evaluation') = 0,
  'ALTER TABLE algorithm_submission ADD COLUMN ai_evaluation LONGTEXT NULL AFTER ai_score',
  'SELECT 1'
);
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'algorithm_submission'
     AND COLUMN_NAME = 'ai_evaluated_at') = 0,
  'ALTER TABLE algorithm_submission ADD COLUMN ai_evaluated_at DATETIME NULL AFTER ai_evaluation',
  'SELECT 1'
);
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
