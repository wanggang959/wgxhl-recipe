USE recipe;
SET NAMES utf8mb4;

-- ============================================================
-- 多租户改造（一次性迁移）
-- 执行前必须备份：mysqldump recipe > recipe-backup-YYYYmmdd.sql
-- 存量数据统一归入 1 号家庭，后续由应用层按 household_id 隔离
-- ============================================================

create table if not exists household
(
    id varchar(36) not null comment '主键id',

    household_name varchar(64) not null comment '家庭名称',
    owner_user_id varchar(36) comment '家庭创建者用户id',
    status varchar(32) not null default 'normal' comment '状态：normal-正常，disabled-停用',
    remark varchar(255) comment '备注',

    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',

    primary key (id),
    unique key uk_household_name (household_name)
)
engine=innodb
default charset=utf8mb4
comment='家庭表';

create table if not exists household_member
(
    id varchar(36) not null comment '主键id',

    household_id varchar(36) not null comment '家庭id',
    user_id varchar(36) not null comment '用户id',
    member_role varchar(32) not null default 'member' comment '家庭角色：owner-所有者，admin-管理员，member-成员，guest-游客',
    status varchar(32) not null default 'normal' comment '状态：normal-正常，disabled-停用',

    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',

    primary key (id),
    unique key uk_household_member (household_id, user_id),
    key idx_household_member_user (user_id)
)
engine=innodb
default charset=utf8mb4
comment='家庭成员关系表';

-- ------------------------------------------------------------
-- 1. 为所有业务表补充 household_id（先可空，回填后改非空）
-- ------------------------------------------------------------
alter table recipe_category add column household_id varchar(36) null comment '家庭id' after id;
alter table recipe add column household_id varchar(36) null comment '家庭id' after id;
alter table recipe_image add column household_id varchar(36) null comment '家庭id' after id;
alter table recipe_step add column household_id varchar(36) null comment '家庭id' after id;
alter table ingredient add column household_id varchar(36) null comment '家庭id' after id;
alter table seasoning add column household_id varchar(36) null comment '家庭id' after id;
alter table recipe_ingredient_rel add column household_id varchar(36) null comment '家庭id' after id;
alter table recipe_seasoning_rel add column household_id varchar(36) null comment '家庭id' after id;
alter table user_favorite add column household_id varchar(36) null comment '家庭id' after id;
alter table user_wanted_recipe add column household_id varchar(36) null comment '家庭id' after id;
alter table user_push_subscription add column household_id varchar(36) null comment '家庭id' after id;
alter table recipe_view_record add column household_id varchar(36) null comment '家庭id' after id;
alter table todo add column household_id varchar(36) null comment '家庭id' after id;
alter table todo_owner add column household_id varchar(36) null comment '家庭id' after id;
alter table todo_notice_rule add column household_id varchar(36) null comment '家庭id' after id;
alter table todo_send_log add column household_id varchar(36) null comment '家庭id' after id;
alter table notification add column household_id varchar(36) null comment '家庭id' after id;
alter table special_expense_project add column household_id varchar(36) null comment '家庭id' after id;
alter table special_expense_item add column household_id varchar(36) null comment '家庭id' after id;

-- ------------------------------------------------------------
-- 2. 创建默认家庭并回填存量数据
-- ------------------------------------------------------------
insert into household (id, household_name, owner_user_id, status, remark, create_time, update_time)
values ('00000000-0000-0000-0000-000000000001', '王师傅家', 'admin-wangshifu', 'normal', '存量数据迁移', now(), now())
on duplicate key update
    household_name = values(household_name),
    owner_user_id = values(owner_user_id),
    update_time = now();

update recipe_category set household_id = '00000000-0000-0000-0000-000000000001' where household_id is null;
update recipe set household_id = '00000000-0000-0000-0000-000000000001' where household_id is null;
update recipe_image set household_id = '00000000-0000-0000-0000-000000000001' where household_id is null;
update recipe_step set household_id = '00000000-0000-0000-0000-000000000001' where household_id is null;
update ingredient set household_id = '00000000-0000-0000-0000-000000000001' where household_id is null;
update seasoning set household_id = '00000000-0000-0000-0000-000000000001' where household_id is null;
update recipe_ingredient_rel set household_id = '00000000-0000-0000-0000-000000000001' where household_id is null;
update recipe_seasoning_rel set household_id = '00000000-0000-0000-0000-000000000001' where household_id is null;
update user_favorite set household_id = '00000000-0000-0000-0000-000000000001' where household_id is null;
update user_wanted_recipe set household_id = '00000000-0000-0000-0000-000000000001' where household_id is null;
update user_push_subscription set household_id = '00000000-0000-0000-0000-000000000001' where household_id is null;
update recipe_view_record set household_id = '00000000-0000-0000-0000-000000000001' where household_id is null;
update todo set household_id = '00000000-0000-0000-0000-000000000001' where household_id is null;
update todo_owner set household_id = '00000000-0000-0000-0000-000000000001' where household_id is null;
update todo_notice_rule set household_id = '00000000-0000-0000-0000-000000000001' where household_id is null;
update todo_send_log set household_id = '00000000-0000-0000-0000-000000000001' where household_id is null;
update notification set household_id = '00000000-0000-0000-0000-000000000001' where household_id is null;
update special_expense_project set household_id = '00000000-0000-0000-0000-000000000001' where household_id is null;
update special_expense_item set household_id = '00000000-0000-0000-0000-000000000001' where household_id is null;

insert into household_member (id, household_id, user_id, member_role, status, create_time, update_time)
select replace(uuid(), '-', ''),
       '00000000-0000-0000-0000-000000000001',
       u.id,
       case
           when u.id = 'admin-wangshifu' then 'owner'
           when u.id = 'guest' then 'guest'
           else 'member'
       end,
       'normal',
       now(),
       now()
from app_user u
where not exists (select 1
                  from household_member m
                  where m.household_id = '00000000-0000-0000-0000-000000000001'
                    and m.user_id = u.id);

-- ------------------------------------------------------------
-- 3. 收紧为 NOT NULL 并加家庭索引
-- ------------------------------------------------------------
alter table recipe_category modify column household_id varchar(36) not null comment '家庭id', add key idx_recipe_category_household (household_id);
alter table recipe modify column household_id varchar(36) not null comment '家庭id', add key idx_recipe_household (household_id);
alter table recipe_image modify column household_id varchar(36) not null comment '家庭id', add key idx_recipe_image_household (household_id);
alter table recipe_step modify column household_id varchar(36) not null comment '家庭id', add key idx_recipe_step_household (household_id);
alter table ingredient modify column household_id varchar(36) not null comment '家庭id', add key idx_ingredient_household (household_id);
alter table seasoning modify column household_id varchar(36) not null comment '家庭id', add key idx_seasoning_household (household_id);
alter table recipe_ingredient_rel modify column household_id varchar(36) not null comment '家庭id', add key idx_recipe_ingredient_rel_household (household_id);
alter table recipe_seasoning_rel modify column household_id varchar(36) not null comment '家庭id', add key idx_recipe_seasoning_rel_household (household_id);
alter table user_favorite modify column household_id varchar(36) not null comment '家庭id', add key idx_user_favorite_household (household_id);
alter table user_wanted_recipe modify column household_id varchar(36) not null comment '家庭id', add key idx_user_wanted_household (household_id);
alter table user_push_subscription modify column household_id varchar(36) not null comment '家庭id', add key idx_user_push_household (household_id);
alter table recipe_view_record modify column household_id varchar(36) not null comment '家庭id', add key idx_recipe_view_record_household (household_id);
alter table todo modify column household_id varchar(36) not null comment '家庭id', add key idx_todo_household (household_id);
alter table todo_owner modify column household_id varchar(36) not null comment '家庭id', add key idx_todo_owner_household (household_id);
alter table todo_notice_rule modify column household_id varchar(36) not null comment '家庭id', add key idx_todo_notice_rule_household (household_id);
alter table todo_send_log modify column household_id varchar(36) not null comment '家庭id', add key idx_todo_send_log_household (household_id);
alter table notification modify column household_id varchar(36) not null comment '家庭id', add key idx_notification_household (household_id);
alter table special_expense_project modify column household_id varchar(36) not null comment '家庭id', add key idx_special_expense_project_household (household_id);
alter table special_expense_item modify column household_id varchar(36) not null comment '家庭id', add key idx_special_expense_item_household (household_id);

-- ------------------------------------------------------------
-- 4. 唯一键改为家庭内唯一
-- ------------------------------------------------------------
alter table recipe_category drop index uk_recipe_category_code,
    add unique key uk_recipe_category_code (household_id, category_code);
alter table recipe_category drop index uk_recipe_category_name,
    add unique key uk_recipe_category_name (household_id, category_name);
alter table recipe drop index uk_recipe_name_version_owner,
    add unique key uk_recipe_name_version_owner (household_id, recipe_name, recipe_version, owner_user_id);
alter table ingredient drop index uk_ingredient_name,
    add unique key uk_ingredient_name (household_id, ingredient_name);
alter table seasoning drop index uk_seasoning_name,
    add unique key uk_seasoning_name (household_id, seasoning_name);
