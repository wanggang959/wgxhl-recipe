-- 菜谱上传者署名（已有库执行一次即可）
USE recipe;

ALTER TABLE recipe
    ADD COLUMN owner_user_id varchar(36) NULL COMMENT '上传者用户id' AFTER category_name,
    ADD COLUMN owner_name varchar(64) NULL COMMENT '上传者显示名' AFTER owner_user_id;
