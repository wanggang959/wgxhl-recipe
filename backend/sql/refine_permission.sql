-- 权限模型细化（2026-10-26）
-- 全局角色只保留 super_admin（王师傅，维护平台公共数据与账号禁用）与 user；
-- 家庭内的一切权限由 household_member.member_role（owner / admin / member / guest）决定。
-- 本脚本只改数据、不改表结构，可重复执行。

-- 1) 历史全局 admin 降级为普通用户。
--    这些账号在各自家庭里的 member_role 已是 owner/admin，家庭内权限不变；
--    变化的是他们不再能跨家庭改别人的菜谱步骤/图片/食材关系（改为按菜谱作者校验）。
update app_user
   set user_role = 'user'
 where user_role = 'admin';

-- 2) 家庭所有者与其成员角色对齐：household.owner_user_id 指向的成员必须是 owner。
update household_member m
  join household h on h.id = m.household_id
   set m.member_role = 'owner'
 where m.user_id = h.owner_user_id
   and m.member_role <> 'owner';

-- 校验：应只剩 super_admin 与 user 两种全局角色，且每个家庭恰好一个 owner
-- select user_role, count(*) from app_user group by user_role;
-- select h.household_name, m.member_role, count(*) from household h
--   join household_member m on m.household_id = h.id group by h.household_name, m.member_role;
