USE recipe;
SET NAMES utf8mb4;

create table if not exists household_invite
(
    id varchar(36) not null comment '主键id',

    household_id varchar(36) not null comment '家庭id',
    invite_code varchar(16) not null comment '邀请码',
    member_role varchar(32) not null default 'member' comment '加入后的家庭角色：admin-管理员，member-成员',
    max_uses int not null default 1 comment '最大使用次数',
    used_count int not null default 0 comment '已使用次数',
    expires_at datetime comment '过期时间，为空表示不过期',
    status varchar(32) not null default 'active' comment '状态：active-有效，revoked-已撤销，exhausted-已用完',
    created_by varchar(36) comment '创建人用户id',

    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',

    primary key (id),
    unique key uk_household_invite_code (invite_code),
    key idx_household_invite_household (household_id, status)
)
engine=innodb
default charset=utf8mb4
comment='家庭邀请码表';
