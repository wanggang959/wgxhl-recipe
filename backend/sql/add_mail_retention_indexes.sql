-- Index the bounded daily retention cleanup. Safe to run repeatedly on MySQL 8.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mail_message_log' AND INDEX_NAME = 'idx_mail_message_create_time') = 0,
  'ALTER TABLE mail_message_log ADD KEY idx_mail_message_create_time (create_time)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mail_delivery_event' AND INDEX_NAME = 'idx_mail_event_create_time') = 0,
  'ALTER TABLE mail_delivery_event ADD KEY idx_mail_event_create_time (create_time)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'email_verification_code' AND INDEX_NAME = 'idx_email_code_expires_at') = 0,
  'ALTER TABLE email_verification_code ADD KEY idx_email_code_expires_at (expires_at)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
