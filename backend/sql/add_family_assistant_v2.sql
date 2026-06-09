USE recipe;

alter table app_user
    add column email varchar(200) comment '邮箱' after avatar,
    add column birthday date comment '公历生日' after email,
    add column birthday_calendar varchar(20) not null default 'SOLAR' comment '生日历法：SOLAR-公历，LUNAR-农历' after birthday,
    add column lunar_birthday_year int comment '农历出生年' after birthday_calendar,
    add column lunar_birthday_month tinyint comment '农历生日月份' after lunar_birthday_year,
    add column lunar_birthday_day tinyint comment '农历生日日期' after lunar_birthday_month,
    add column lunar_birthday_leap tinyint(1) not null default 0 comment '是否农历闰月' after lunar_birthday_day,
    add column remark varchar(500) comment '备注' after status,
    add column notification_preference varchar(50) comment '通知偏好' after remark;

create table if not exists todo
(
    id varchar(36) not null comment '主键id',
    title varchar(128) not null comment '标题',
    category varchar(32) not null comment '分类',
    description varchar(500) comment '备注',
    related_type varchar(64) comment '关联类型',
    related_id varchar(36) comment '关联id',
    owner_id varchar(36) comment '负责人id',
    due_time datetime comment '到期时间',
    due_calendar varchar(20) not null default 'SOLAR' comment '到期日期历法：SOLAR-公历，LUNAR-农历',
    lunar_due_month tinyint comment '农历到期月份',
    lunar_due_day tinyint comment '农历到期日期',
    lunar_due_leap tinyint(1) not null default 0 comment '是否农历闰月',
    birthday_year int comment '出生年，用于生日年龄展示',
    repeat_type varchar(32) not null default 'NONE' comment '重复规则',
    notify_site tinyint(1) not null default 1 comment '站内通知',
    notify_email tinyint(1) not null default 0 comment '邮箱通知',
    notify_push tinyint(1) not null default 0 comment 'PWA通知',
    status varchar(32) not null default 'TODO' comment '状态',
    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',
    primary key (id),
    key idx_todo_due_time (due_time),
    key idx_todo_owner_status (owner_id, status),
    key idx_todo_related (related_type, related_id)
)
engine=innodb
default charset=utf8mb4
comment='家庭待办表';

create table if not exists todo_owner
(
    id varchar(36) not null comment '主键id',
    todo_id varchar(36) not null comment '待办id',
    owner_id varchar(36) not null comment '负责人id',
    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',
    primary key (id),
    unique key uk_todo_owner (todo_id, owner_id),
    key idx_todo_owner_owner (owner_id)
)
engine=innodb
default charset=utf8mb4
comment='待办负责人关系表';

create table if not exists todo_notice_rule
(
    id varchar(36) not null comment '主键id',
    todo_id varchar(36) not null comment '待办id',
    before_type varchar(32) not null comment '提醒类型',
    before_minutes int not null default 0 comment '提前分钟数',
    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',
    primary key (id),
    key idx_todo_notice_rule_todo (todo_id)
)
engine=innodb
default charset=utf8mb4
comment='待办提醒规则表';

create table if not exists todo_send_log
(
    id varchar(36) not null comment '主键id',
    todo_id varchar(36) not null comment '待办id',
    rule_id varchar(36) not null comment '提醒规则id',
    notify_type varchar(32) not null comment '通知类型',
    send_time datetime comment '发送时间',
    send_status varchar(32) comment '发送状态',
    error_msg varchar(500) comment '错误信息',
    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',
    primary key (id),
    key idx_todo_send_log_rule (todo_id, rule_id, notify_type, send_time)
)
engine=innodb
default charset=utf8mb4
comment='待办提醒发送日志表';

create table if not exists notification
(
    id varchar(36) not null comment '主键id',
    user_id varchar(36) comment '用户id',
    title varchar(128) not null comment '标题',
    content varchar(500) comment '内容',
    notification_type varchar(32) comment '通知类型',
    related_id varchar(36) comment '关联待办id',
    is_read tinyint(1) not null default 0 comment '是否已读',
    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',
    primary key (id),
    key idx_notification_user_read (user_id, is_read, create_time)
)
engine=innodb
default charset=utf8mb4
comment='站内通知表';
