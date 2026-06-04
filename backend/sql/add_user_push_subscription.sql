USE recipe;

create table if not exists user_push_subscription
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
