ALTER TABLE commute_settings ADD COLUMN site_enabled tinyint NOT NULL DEFAULT 0,
 ADD COLUMN push_enabled tinyint NOT NULL DEFAULT 0;
ALTER TABLE commute_notice_log ADD COLUMN notify_type varchar(20) NOT NULL DEFAULT 'email',
 DROP INDEX uk_commute_notice_user_day,
 ADD UNIQUE KEY uk_commute_notice_channel (user_id,notice_date,notify_type);
