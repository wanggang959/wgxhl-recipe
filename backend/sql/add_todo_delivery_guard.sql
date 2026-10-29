-- Independent durable claims keep concurrent scans from sending the same reminder twice.
CREATE TABLE IF NOT EXISTS todo_delivery_guard (
    household_id varchar(36) NOT NULL,
    todo_id varchar(36) NOT NULL,
    rule_id varchar(36) NOT NULL,
    target_time datetime(6) NOT NULL,
    notify_type varchar(32) NOT NULL,
    recipient_key varchar(36) NOT NULL DEFAULT '',
    status varchar(20) NOT NULL,
    attempts int NOT NULL DEFAULT 1,
    update_time datetime(6) NOT NULL,
    PRIMARY KEY (household_id, todo_id, rule_id, target_time, notify_type, recipient_key),
    KEY idx_todo_guard_updated (update_time),
    KEY idx_todo_guard_status_time (status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS todo_scan_checkpoint (
    household_id varchar(36) NOT NULL PRIMARY KEY,
    last_success_time datetime(6) NOT NULL,
    last_gap_start datetime(6) NULL,
    last_gap_end datetime(6) NULL,
    update_time datetime(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='todo_scan_checkpoint' AND COLUMN_NAME='last_gap_start')=0,
    'ALTER TABLE todo_scan_checkpoint ADD COLUMN last_gap_start datetime(6) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='todo_scan_checkpoint' AND COLUMN_NAME='last_gap_end')=0,
    'ALTER TABLE todo_scan_checkpoint ADD COLUMN last_gap_end datetime(6) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='todo_delivery_guard' AND INDEX_NAME='idx_todo_guard_status_time')=0,
    'ALTER TABLE todo_delivery_guard ADD KEY idx_todo_guard_status_time (status, update_time)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
