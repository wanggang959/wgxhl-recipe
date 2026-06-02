USE recipe;

create table if not exists seasoning
(
    id varchar(36) not null comment '主键id',

    seasoning_name varchar(128) not null comment '调料名称',
    seasoning_image varchar(255) comment '调料图片地址',
    seasoning_desc varchar(255) comment '调料描述',

    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',

    primary key (id),
    unique key uk_seasoning_name (seasoning_name)
)
engine=innodb
default charset=utf8mb4
comment='调料信息表';


create table if not exists recipe_seasoning_rel
(
    id varchar(36) not null comment '主键id',

    recipe_id varchar(36) not null comment '菜谱id',
    seasoning_id varchar(36) not null comment '调料id',
    seasoning_name varchar(128) comment '调料名称冗余',
    amount varchar(64) comment '用量',
    unit varchar(32) comment '单位',
    sort_no int comment '排序号',

    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',

    primary key (id),
    unique key uk_recipe_seasoning_rel (recipe_id, seasoning_id)
)
engine=innodb
default charset=utf8mb4
comment='菜谱调料关系表';
