USE recipe;
SET NAMES utf8mb4;

-- ============================================================
-- 菜谱/基础数据改为"人所有"
-- 执行前必须备份：mysqldump recipe > recipe-backup-YYYYmmdd.sql
-- 执行顺序：init.sql / add_household.sql 之后
-- 注意：会删除相关表的 household_id 与旧唯一键，执行后不可逆
-- ============================================================

-- 迁移前检查：如果返回结果非空，说明同一作者存在同名同版本菜谱，需要先人工合并
select recipe_name, recipe_version, owner_user_id, count(*) as dup_count
from recipe
where household_id = '00000000-0000-0000-0000-000000000001'
group by recipe_name, recipe_version, owner_user_id
having count(*) > 1;

-- ------------------------------------------------------------
-- 1. 菜谱：增加可见范围，唯一键回到"作者 + 名称 + 版本"
-- ------------------------------------------------------------
alter table recipe
    add column visibility varchar(20) not null default 'family'
        comment '可见范围：family-家庭成员可见，private-仅自己可见' after status;

alter table recipe drop index uk_recipe_name_version_owner;
alter table recipe add unique key uk_recipe_name_version_owner (recipe_name, recipe_version, owner_user_id);

alter table recipe drop index idx_recipe_household, drop column household_id;

-- ------------------------------------------------------------
-- 2. 菜谱子表：去掉家庭归属
-- ------------------------------------------------------------
alter table recipe_image drop index idx_recipe_image_household, drop column household_id;
alter table recipe_step drop index idx_recipe_step_household, drop column household_id;
alter table recipe_ingredient_rel drop index idx_recipe_ingredient_rel_household, drop column household_id;
alter table recipe_seasoning_rel drop index idx_recipe_seasoning_rel_household, drop column household_id;

-- ------------------------------------------------------------
-- 3. 分类：平台公共 + 个人，唯一键按归属人
-- ------------------------------------------------------------
alter table recipe_category
    add column scope varchar(20) not null default 'platform'
        comment '数据范围：platform-平台公共，personal-个人' after category_desc,
    add column owner_user_id varchar(36) not null default 'platform'
        comment '归属人：平台数据固定为 platform，个人数据为用户id' after scope;

alter table recipe_category drop index uk_recipe_category_code;
alter table recipe_category drop index uk_recipe_category_name;
alter table recipe_category add unique key uk_recipe_category_code (owner_user_id, category_code);
alter table recipe_category add unique key uk_recipe_category_name (owner_user_id, category_name);

alter table recipe_category drop index idx_recipe_category_household, drop column household_id;

-- ------------------------------------------------------------
-- 4. 食材：平台公共 + 个人
-- ------------------------------------------------------------
alter table ingredient
    add column scope varchar(20) not null default 'platform'
        comment '数据范围：platform-平台公共，personal-个人' after ingredient_desc,
    add column owner_user_id varchar(36) not null default 'platform'
        comment '归属人：平台数据固定为 platform，个人数据为用户id' after scope;

alter table ingredient drop index uk_ingredient_name;
alter table ingredient add unique key uk_ingredient_name (owner_user_id, ingredient_name);

alter table ingredient drop index idx_ingredient_household, drop column household_id;

-- ------------------------------------------------------------
-- 5. 调料：平台公共 + 个人
-- ------------------------------------------------------------
alter table seasoning
    add column scope varchar(20) not null default 'platform'
        comment '数据范围：platform-平台公共，personal-个人' after seasoning_desc,
    add column owner_user_id varchar(36) not null default 'platform'
        comment '归属人：平台数据固定为 platform，个人数据为用户id' after scope;

alter table seasoning drop index uk_seasoning_name;
alter table seasoning add unique key uk_seasoning_name (owner_user_id, seasoning_name);

alter table seasoning drop index idx_seasoning_household, drop column household_id;

-- ------------------------------------------------------------
-- 6. 数据修正：历史菜谱可能仍挂着旧的内置管理员 id
-- ------------------------------------------------------------
update recipe
set owner_user_id = (select id from app_user where user_role = 'super_admin' order by create_time limit 1)
where owner_user_id = 'admin-wangshifu'
  and exists (select 1 from app_user where user_role = 'super_admin');
