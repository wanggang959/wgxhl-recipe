-- Explicit provider rejections can defer a bounded retry without changing audit timestamps.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='todo_delivery_guard' AND COLUMN_NAME='next_retry_time')=0,
    'ALTER TABLE todo_delivery_guard ADD COLUMN next_retry_time datetime(6) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
