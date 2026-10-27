-- 菜谱下架 + 登录记住家庭（2026-10-27）

-- 1) 老数据补状态：recipe.status 一直存在，历史行都是「上架」，这里只兜底空值
update recipe
   set status = '上架'
 where status is null or status = '';

-- 2) 账号记住上次所在家庭，登录时优先恢复到该家庭
alter table app_user
  add column active_household_id varchar(36) null comment '上次所在家庭，登录时优先恢复';

-- 无需回填：为空时登录会自动选第一个家庭，并在登录成功后写入
