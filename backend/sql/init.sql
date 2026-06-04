USE recipe;

create table recipe_category
(
    id varchar(36) not null comment '主键id',

    category_name varchar(64) not null comment '分类名称',
    category_code varchar(64) not null comment '分类编码',
    sort_no int comment '排序号',
    category_desc varchar(255) comment '分类描述',

    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',

    primary key (id),
    unique key uk_recipe_category_code (category_code),
    unique key uk_recipe_category_name (category_name)
)
engine=innodb
default charset=utf8mb4
comment='菜谱分类表';


create table recipe
(
    id varchar(36) not null comment '主键id',

    recipe_name varchar(128) not null comment '菜谱名称',
    recipe_version varchar(8) not null default '1.0' comment '版本号（如1.0）',
    recipe_desc varchar(255) comment '菜谱简介',
    cover_image varchar(255) comment '封面图片地址',
    difficulty varchar(32) comment '难度',
    cooking_time varchar(32) comment '制作耗时',
    serving_count varchar(32) comment '适合人数',
    taste varchar(64) comment '口味',
    status varchar(32) comment '状态',

    category_id varchar(36) comment '分类id',
    category_name varchar(64) comment '分类名称冗余',

    owner_user_id varchar(36) comment '上传者用户id',
    owner_name varchar(64) comment '上传者显示名',

    remark varchar(255) comment '备注',

    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',

    primary key (id),
    unique key uk_recipe_name_version_owner (recipe_name, recipe_version, owner_user_id)
)
engine=innodb
default charset=utf8mb4
comment='菜谱信息表';


create table recipe_image
(
    id varchar(36) not null comment '主键id',

    recipe_id varchar(36) not null comment '菜谱id',
    image_type varchar(32) not null comment '图片类型：cover-封面，ingredient-食材，step-步骤，finish-成品',
    image_url varchar(255) not null comment '图片地址',
    sort_no int comment '排序号',
    image_desc varchar(255) comment '图片描述',

    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',

    primary key (id)
)
engine=innodb
default charset=utf8mb4
comment='菜谱图片表';


create table recipe_step
(
    id varchar(36) not null comment '主键id',

    recipe_id varchar(36) not null comment '菜谱id',
    step_no int not null comment '步骤序号',
    step_title varchar(128) comment '步骤标题',
    step_desc text comment '步骤说明',
    step_image varchar(255) comment '步骤图片地址',

    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',

    primary key (id),
    unique key uk_recipe_step_no (recipe_id, step_no)
)
engine=innodb
default charset=utf8mb4
comment='菜谱制作步骤表';


create table ingredient
(
    id varchar(36) not null comment '主键id',

    ingredient_name varchar(128) not null comment '食材名称',
    ingredient_image varchar(255) comment '食材图片地址',
    ingredient_desc varchar(255) comment '食材描述',

    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',

    primary key (id),
    unique key uk_ingredient_name (ingredient_name)
)
engine=innodb
default charset=utf8mb4
comment='食材信息表';


create table seasoning
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


create table recipe_ingredient_rel
(
    id varchar(36) not null comment '主键id',

    recipe_id varchar(36) not null comment '菜谱id',
    ingredient_id varchar(36) not null comment '食材id',
    ingredient_name varchar(128) comment '食材名称冗余',
    amount varchar(64) comment '用量',
    unit varchar(32) comment '单位',
    sort_no int comment '排序号',

    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',

    primary key (id),
    unique key uk_recipe_ingredient_rel (recipe_id, ingredient_id)
)
engine=innodb
default charset=utf8mb4
comment='菜谱食材关系表';


create table recipe_seasoning_rel
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


create table user_favorite
(
    id varchar(36) not null comment '主键id',

    user_id varchar(36) not null comment '用户id',
    recipe_id varchar(36) not null comment '菜谱id',
    recipe_name varchar(128) comment '菜谱名称冗余',
    cover_image varchar(255) comment '封面图片地址冗余',

    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',

    primary key (id),
    unique key uk_user_favorite (user_id, recipe_id)
)
engine=innodb
default charset=utf8mb4
comment='用户收藏表';


create table user_wanted_recipe
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


create table user_push_subscription
(
    id varchar(36) not null comment 'primary id',

    user_id varchar(36) not null comment 'user id',
    endpoint varchar(512) not null comment 'web push endpoint',
    p256dh varchar(255) not null comment 'client public key',
    auth varchar(255) not null comment 'client auth secret',
    user_agent varchar(512) comment 'browser user agent',
    enabled tinyint(1) not null default 1 comment 'enabled flag',

    create_time datetime comment 'create time',
    update_time datetime comment 'update time',

    primary key (id),
    unique key uk_user_push_endpoint (endpoint(191)),
    key idx_user_push_user (user_id, enabled)
)
engine=innodb
default charset=utf8mb4
comment='user web push subscription';


create table recipe_view_record
(
    id varchar(36) not null comment '主键id',

    user_id varchar(36) comment '用户id',
    recipe_id varchar(36) not null comment '菜谱id',
    recipe_name varchar(128) comment '菜谱名称冗余',
    view_time datetime comment '浏览时间',

    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',

    primary key (id)
)
engine=innodb
default charset=utf8mb4
comment='菜谱浏览记录表';


create table app_user
(
    id varchar(36) not null comment '主键id',

    username varchar(64) not null comment '用户名',
    nickname varchar(64) comment '昵称',
    password varchar(255) comment '密码',
    avatar varchar(255) comment '头像地址',
    user_role varchar(32) comment '用户角色',
    status varchar(32) comment '状态',

    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',

    primary key (id),
    unique key uk_app_user_username (username)
)
engine=innodb
default charset=utf8mb4
comment='用户信息表';

insert into app_user (id, username, nickname, password, avatar, user_role, status, create_time, update_time)
values ('admin-wangshifu', '王师傅', '王师傅', '123456', '/avatars/11.png', 'super_admin', 'normal', now(), now())
on duplicate key update
    nickname = values(nickname),
    password = values(password),
    avatar = ifnull(avatar, values(avatar)),
    user_role = values(user_role),
    status = values(status),
    update_time = now();

insert into app_user (id, username, nickname, password, avatar, user_role, status, create_time, update_time)
values ('guest', 'guest', '游客', null, '/avatars/15.png', 'user', 'normal', now(), now())
on duplicate key update
    nickname = values(nickname),
    avatar = ifnull(avatar, values(avatar)),
    user_role = values(user_role),
    status = values(status),
    update_time = now();
