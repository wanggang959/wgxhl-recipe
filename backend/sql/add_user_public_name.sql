-- 公共菜谱作者展示名（2026-10-27）
-- 为空时公共菜谱显示「厨友+用户id后四位」，家庭/私有菜谱仍显示真实昵称
alter table app_user
  add column public_name varchar(64) null comment '公共菜谱展示名，为空则用厨友别名';
