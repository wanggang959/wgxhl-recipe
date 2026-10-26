USE recipe;
SET NAMES utf8mb4;

create table if not exists email_verification_code
(
    id varchar(36) not null comment '主键id',

    email varchar(200) not null comment '邮箱',
    code varchar(12) not null comment '验证码',
    purpose varchar(32) not null default 'REGISTER' comment '用途',
    expires_at datetime not null comment '过期时间',
    used tinyint(1) not null default 0 comment '是否已使用',
    attempts int not null default 0 comment '校验尝试次数',

    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',

    primary key (id),
    key idx_email_code_lookup (email, purpose, used, expires_at),
    key idx_email_code_created (email, create_time)
)
engine=innodb
default charset=utf8mb4
comment='邮箱验证码表';
