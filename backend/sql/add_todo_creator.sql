USE recipe;
SET NAMES utf8mb4;

-- 为退出家庭清理补充创建人信息（存量数据以负责人回填）
alter table todo add column create_user_id varchar(36) null comment '创建人用户id' after id;
update todo set create_user_id = owner_id where create_user_id is null;
alter table todo add key idx_todo_create_user (create_user_id);
