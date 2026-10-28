USE recipe;
SET NAMES utf8mb4;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_user' AND COLUMN_NAME='auth_version')=0,
 'ALTER TABLE app_user ADD COLUMN auth_version bigint NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_user' AND COLUMN_NAME='email_verified')=0,
 'ALTER TABLE app_user ADD COLUMN email_verified tinyint(1) NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_user' AND COLUMN_NAME='account_origin')=0,
 'ALTER TABLE app_user ADD COLUMN account_origin varchar(20) NOT NULL DEFAULT ''LEGACY''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_user' AND COLUMN_NAME='created_by_user_id')=0,
 'ALTER TABLE app_user ADD COLUMN created_by_user_id varchar(36) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_user' AND COLUMN_NAME='created_household_id')=0,
 'ALTER TABLE app_user ADD COLUMN created_household_id varchar(36) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE email_verification_code MODIFY COLUMN code varchar(100) NOT NULL;

CREATE TABLE IF NOT EXISTS account_security_ticket (
 id varchar(36) NOT NULL PRIMARY KEY,
 user_id varchar(36) NOT NULL,
 purpose varchar(32) NOT NULL,
 requester_id varchar(36) NULL,
 initiator_household_id varchar(36) NULL,
 token_hash varchar(64) NULL,
 code_old_hash varchar(100) NULL,
 code_new_hash varchar(100) NULL,
 original_email varchar(200) NULL,
 new_email varchar(200) NULL,
 issued_auth_version bigint NOT NULL DEFAULT 0,
 expires_at datetime NOT NULL,
 attempts int NOT NULL DEFAULT 0,
 used tinyint(1) NOT NULL DEFAULT 0,
 create_time datetime NOT NULL,
 update_time datetime NOT NULL,
 UNIQUE KEY uk_security_token (token_hash),
 KEY idx_security_user (user_id,purpose,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS email_send_quota (
 id varchar(36) NOT NULL PRIMARY KEY,
 bucket_type varchar(16) NOT NULL,
 bucket_hash varchar(64) NOT NULL,
 quota_date date NOT NULL,
 used_count int NOT NULL DEFAULT 0,
 last_sent_at datetime NULL,
 create_time datetime NOT NULL,
 update_time datetime NOT NULL,
 UNIQUE KEY uk_email_quota (bucket_type,bucket_hash,quota_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
