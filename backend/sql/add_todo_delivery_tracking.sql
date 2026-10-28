-- Existing records remain NULL and retain the previous rule-level deduplication behavior.
ALTER TABLE todo_send_log
    ADD COLUMN recipient_id varchar(36) NULL COMMENT '接收人id' AFTER rule_id,
    ADD COLUMN target_time datetime NULL COMMENT '本次提醒计划时间' AFTER recipient_id,
    ADD INDEX idx_todo_delivery (household_id, todo_id, rule_id, target_time, notify_type, recipient_id);
