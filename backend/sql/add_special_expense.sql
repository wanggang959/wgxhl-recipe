USE recipe;
SET NAMES utf8mb4;

create table if not exists special_expense_project
(
    id varchar(36) not null comment '主键id',
    project_name varchar(100) not null comment '专项名称',
    project_type varchar(50) not null default 'OTHER' comment '专项类型',
    total_budget decimal(12,2) not null default 0.00 comment '总预算',
    status varchar(30) not null default 'ACTIVE' comment '状态：ACTIVE-进行中，ARCHIVED-已归档',
    start_date date comment '开始日期',
    end_date date comment '预计结束日期',
    archived tinyint(1) not null default 0 comment '是否归档',
    remark varchar(500) comment '备注',
    create_user_id varchar(36) comment '创建人id',
    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',
    primary key (id),
    key idx_special_expense_project_status (status),
    key idx_special_expense_project_archived (archived)
)
engine=innodb
default charset=utf8mb4
comment='家庭专项开支项目表';

create table if not exists special_expense_item
(
    id varchar(36) not null comment '主键id',
    project_id varchar(36) not null comment '专项id',
    item_name varchar(120) not null comment '开支名称',
    category varchar(50) not null default 'OTHER' comment '分类',
    budget_amount decimal(12,2) not null default 0.00 comment '预算金额',
    actual_amount decimal(12,2) not null default 0.00 comment '实际花费',
    quantity varchar(50) comment '数量',
    status varchar(30) not null default 'TODO' comment '状态',
    payer_id varchar(36) comment '付款人id',
    owner_id varchar(36) comment '负责人id',
    purchase_date date comment '购买日期',
    planned_time datetime comment '计划购买时间',
    purchase_channel varchar(100) comment '购买渠道',
    purchase_link varchar(500) comment '购买链接',
    remark varchar(500) comment '备注',
    image_url varchar(500) comment '图片或凭证',
    todo_id varchar(36) comment '关联待办id',
    create_time datetime comment '创建时间',
    update_time datetime comment '更新时间',
    primary key (id),
    key idx_special_expense_item_project (project_id),
    key idx_special_expense_item_status (status),
    key idx_special_expense_item_planned_time (planned_time),
    key idx_special_expense_item_todo (todo_id)
)
engine=innodb
default charset=utf8mb4
comment='家庭专项开支明细表';
