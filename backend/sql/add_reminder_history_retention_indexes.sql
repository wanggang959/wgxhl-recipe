-- Retention only removes old read notifications and obsolete reminder-cycle logs.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='notification' AND INDEX_NAME='idx_notification_retention')=0,
    'ALTER TABLE notification ADD KEY idx_notification_retention (is_read, update_time)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='todo_send_log' AND INDEX_NAME='idx_todo_send_log_retention')=0,
    'ALTER TABLE todo_send_log ADD KEY idx_todo_send_log_retention (create_time)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
