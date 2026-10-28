-- Safe for both fresh installations and previously migrated databases.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'todo_send_log' AND COLUMN_NAME = 'recipient_id') = 0,
    'ALTER TABLE todo_send_log ADD COLUMN recipient_id varchar(36) NULL AFTER rule_id', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'todo_send_log' AND COLUMN_NAME = 'target_time') = 0,
    'ALTER TABLE todo_send_log ADD COLUMN target_time datetime NULL AFTER recipient_id', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'todo_send_log' AND INDEX_NAME = 'idx_todo_delivery') = 0,
    'ALTER TABLE todo_send_log ADD INDEX idx_todo_delivery (household_id, todo_id, rule_id, target_time, notify_type, recipient_id)', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
