-- 菜谱新增「公共」可见范围 + 点赞（2026-10-27）

-- 1) 点赞数冗余列，便于按赞数排序；历史数据默认 0
alter table recipe
  add column like_count int not null default 0 comment '点赞数';

-- 2) 点赞记录：按用户全局保存，不随家庭；同一用户对同一菜谱只能赞一次
create table if not exists recipe_like (
  id varchar(36) not null comment '主键',
  recipe_id varchar(36) not null comment '菜谱id',
  user_id varchar(36) not null comment '点赞用户id',
  create_time datetime not null comment '创建时间',
  update_time datetime not null comment '更新时间',
  primary key (id),
  unique key uk_recipe_like (recipe_id, user_id),
  key idx_recipe_like_user (user_id)
) engine = InnoDB
  default charset = utf8mb4
  comment = '菜谱点赞';

-- visibility 为 varchar，新增 public 取值无需改表结构
-- 端到端即：recipe.visibility in ('private','family','public')
