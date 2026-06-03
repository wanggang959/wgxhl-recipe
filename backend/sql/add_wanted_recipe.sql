USE recipe;

create table if not exists user_wanted_recipe
(
    id varchar(36) not null comment '主键id',

    user_id varchar(36) not null comment '用户id',
    recipe_id varchar(36) not null comment '菜谱id',
    recipe_name varchar(128) comment '菜谱名称冗余',
    cover_image varchar(255) comment '封面图片地址冗余',
    planned_date date not null comment '想吃日期',

    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',

    primary key (id),
    unique key uk_user_wanted_recipe (user_id, recipe_id),
    key idx_user_wanted_date (user_id, planned_date)
)
engine=innodb
default charset=utf8mb4
comment='用户想吃菜谱表';
