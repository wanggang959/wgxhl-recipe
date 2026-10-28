-- SES 投递回调（2026-10-28）
-- mail_message_log 记录每次发信返回的 MessageId，用于把回调事件关联回邮件类型；
-- mail_delivery_event 保存回调事件并按 eventid 去重。两张表都是全局数据，不参与家庭隔离。

create table if not exists mail_message_log
(
    id          varchar(36)  not null comment '主键',
    message_id  varchar(120) not null comment 'SES 返回的 MessageId',
    recipient   varchar(200) not null comment '收件人邮箱',
    mail_kind   varchar(32)  not null comment '邮件类型：CODE/PASSWORD_RESET/NOTIFICATION',
    template_id bigint       null comment 'SES 模板id',
    create_time datetime     null comment '创建时间',
    primary key (id),
    unique key uk_mail_message_log (message_id),
    key idx_mail_message_log_recipient (recipient, create_time)
)
    engine = innodb
    default charset = utf8mb4 comment 'SES 发信记录';

create table if not exists mail_delivery_event
(
    id          varchar(36)  not null comment '主键',
    event_id    varchar(120) not null comment '回调事件id，用于去重',
    message_id  varchar(120) null comment 'SES MessageId',
    event       varchar(32)  not null comment '事件：delivered/bounce/open/click/spamreport等',
    recipient   varchar(200) null comment '收件人邮箱',
    bounce_type varchar(20)  null comment '退信类型：soft_bounce/hard_bounce',
    reason      varchar(500) null comment '退信或失败原因',
    occurred_at bigint       null comment '事件发生时间戳',
    template_id bigint       null comment 'SES 模板id',
    create_time datetime     null comment '入库时间',
    primary key (id),
    unique key uk_mail_delivery_event (event_id),
    key idx_mail_delivery_event_message (message_id)
)
    engine = innodb
    default charset = utf8mb4 comment 'SES 投递回调事件';
