/**
 * 修复数据库表/字段中文注释乱码
 * 用法：node backend/scripts/fix-db-comments.mjs
 */
import mysql from 'mysql2/promise'

const config = {
  host: process.env.MYSQL_HOST || 'wgxhl.space',
  port: Number(process.env.MYSQL_PORT || 3306),
  user: process.env.MYSQL_USER || 'recipe',
  password: process.env.MYSQL_PASS || '123456',
  database: process.env.MYSQL_DB || 'recipe',
  charset: 'utf8mb4',
}

const statements = [
  "ALTER TABLE todo COMMENT='家庭待办表'",
  "ALTER TABLE todo MODIFY COLUMN id varchar(36) NOT NULL COMMENT '主键id'",
  "ALTER TABLE todo MODIFY COLUMN title varchar(128) NOT NULL COMMENT '标题'",
  "ALTER TABLE todo MODIFY COLUMN category varchar(32) NOT NULL COMMENT '分类'",
  "ALTER TABLE todo MODIFY COLUMN description varchar(500) COMMENT '备注'",
  "ALTER TABLE todo MODIFY COLUMN related_type varchar(64) COMMENT '关联类型'",
  "ALTER TABLE todo MODIFY COLUMN related_id varchar(36) COMMENT '关联id'",
  "ALTER TABLE todo MODIFY COLUMN owner_id varchar(36) COMMENT '负责人id'",
  "ALTER TABLE todo MODIFY COLUMN due_time datetime COMMENT '到期时间'",
  "ALTER TABLE todo MODIFY COLUMN due_calendar varchar(20) NOT NULL DEFAULT 'SOLAR' COMMENT '到期日期历法：SOLAR-公历，LUNAR-农历'",
  "ALTER TABLE todo MODIFY COLUMN lunar_due_month tinyint COMMENT '农历到期月份'",
  "ALTER TABLE todo MODIFY COLUMN lunar_due_day tinyint COMMENT '农历到期日期'",
  "ALTER TABLE todo MODIFY COLUMN lunar_due_leap tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否农历闰月'",
  "ALTER TABLE todo MODIFY COLUMN birthday_year int COMMENT '出生年，用于生日年龄展示'",
  "ALTER TABLE todo MODIFY COLUMN repeat_type varchar(32) NOT NULL DEFAULT 'NONE' COMMENT '重复规则'",
  "ALTER TABLE todo MODIFY COLUMN notify_site tinyint(1) NOT NULL DEFAULT 1 COMMENT '站内通知'",
  "ALTER TABLE todo MODIFY COLUMN notify_email tinyint(1) NOT NULL DEFAULT 0 COMMENT '邮箱通知'",
  "ALTER TABLE todo MODIFY COLUMN notify_push tinyint(1) NOT NULL DEFAULT 0 COMMENT 'PWA通知'",
  "ALTER TABLE todo MODIFY COLUMN status varchar(32) NOT NULL DEFAULT 'TODO' COMMENT '状态'",
  "ALTER TABLE todo MODIFY COLUMN create_time datetime COMMENT '创建时间'",
  "ALTER TABLE todo MODIFY COLUMN update_time datetime COMMENT '更新时间'",

  "ALTER TABLE todo_owner COMMENT='待办负责人关系表'",
  "ALTER TABLE todo_owner MODIFY COLUMN id varchar(36) NOT NULL COMMENT '主键id'",
  "ALTER TABLE todo_owner MODIFY COLUMN todo_id varchar(36) NOT NULL COMMENT '待办id'",
  "ALTER TABLE todo_owner MODIFY COLUMN owner_id varchar(36) NOT NULL COMMENT '负责人id'",
  "ALTER TABLE todo_owner MODIFY COLUMN create_time datetime COMMENT '创建时间'",
  "ALTER TABLE todo_owner MODIFY COLUMN update_time datetime COMMENT '更新时间'",

  "ALTER TABLE todo_notice_rule COMMENT='待办提醒规则表'",
  "ALTER TABLE todo_notice_rule MODIFY COLUMN id varchar(36) NOT NULL COMMENT '主键id'",
  "ALTER TABLE todo_notice_rule MODIFY COLUMN todo_id varchar(36) NOT NULL COMMENT '待办id'",
  "ALTER TABLE todo_notice_rule MODIFY COLUMN before_type varchar(32) NOT NULL COMMENT '提醒类型'",
  "ALTER TABLE todo_notice_rule MODIFY COLUMN before_minutes int NOT NULL DEFAULT 0 COMMENT '提前分钟数'",
  "ALTER TABLE todo_notice_rule MODIFY COLUMN create_time datetime COMMENT '创建时间'",
  "ALTER TABLE todo_notice_rule MODIFY COLUMN update_time datetime COMMENT '更新时间'",

  "ALTER TABLE todo_send_log COMMENT='待办提醒发送日志表'",
  "ALTER TABLE todo_send_log MODIFY COLUMN id varchar(36) NOT NULL COMMENT '主键id'",
  "ALTER TABLE todo_send_log MODIFY COLUMN todo_id varchar(36) NOT NULL COMMENT '待办id'",
  "ALTER TABLE todo_send_log MODIFY COLUMN rule_id varchar(36) NOT NULL COMMENT '提醒规则id'",
  "ALTER TABLE todo_send_log MODIFY COLUMN notify_type varchar(32) NOT NULL COMMENT '通知类型'",
  "ALTER TABLE todo_send_log MODIFY COLUMN send_time datetime COMMENT '发送时间'",
  "ALTER TABLE todo_send_log MODIFY COLUMN send_status varchar(32) COMMENT '发送状态'",
  "ALTER TABLE todo_send_log MODIFY COLUMN error_msg varchar(500) COMMENT '错误信息'",
  "ALTER TABLE todo_send_log MODIFY COLUMN create_time datetime COMMENT '创建时间'",
  "ALTER TABLE todo_send_log MODIFY COLUMN update_time datetime COMMENT '更新时间'",

  "ALTER TABLE notification COMMENT='站内通知表'",
  "ALTER TABLE notification MODIFY COLUMN id varchar(36) NOT NULL COMMENT '主键id'",
  "ALTER TABLE notification MODIFY COLUMN user_id varchar(36) COMMENT '用户id'",
  "ALTER TABLE notification MODIFY COLUMN title varchar(128) NOT NULL COMMENT '标题'",
  "ALTER TABLE notification MODIFY COLUMN content varchar(500) COMMENT '内容'",
  "ALTER TABLE notification MODIFY COLUMN notification_type varchar(32) COMMENT '通知类型'",
  "ALTER TABLE notification MODIFY COLUMN related_id varchar(36) COMMENT '关联待办id'",
  "ALTER TABLE notification MODIFY COLUMN is_read tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已读'",
  "ALTER TABLE notification MODIFY COLUMN create_time datetime COMMENT '创建时间'",
  "ALTER TABLE notification MODIFY COLUMN update_time datetime COMMENT '更新时间'",

  "ALTER TABLE app_user MODIFY COLUMN email varchar(200) COMMENT '邮箱'",
  "ALTER TABLE app_user MODIFY COLUMN birthday date COMMENT '公历生日'",
  "ALTER TABLE app_user MODIFY COLUMN birthday_calendar varchar(20) NOT NULL DEFAULT 'SOLAR' COMMENT '生日历法：SOLAR-公历，LUNAR-农历'",
  "ALTER TABLE app_user MODIFY COLUMN lunar_birthday_year int COMMENT '农历出生年'",
  "ALTER TABLE app_user MODIFY COLUMN lunar_birthday_month tinyint COMMENT '农历生日月份'",
  "ALTER TABLE app_user MODIFY COLUMN lunar_birthday_day tinyint COMMENT '农历生日日期'",
  "ALTER TABLE app_user MODIFY COLUMN lunar_birthday_leap tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否农历闰月'",
  "ALTER TABLE app_user MODIFY COLUMN remark varchar(500) COMMENT '备注'",
  "ALTER TABLE app_user MODIFY COLUMN notification_preference varchar(50) COMMENT '通知偏好'",

  "ALTER TABLE user_push_subscription COMMENT='用户 Web Push 推送订阅表'",
  "ALTER TABLE user_push_subscription MODIFY COLUMN id varchar(36) NOT NULL COMMENT '主键id'",
  "ALTER TABLE user_push_subscription MODIFY COLUMN user_id varchar(36) NOT NULL COMMENT '用户id'",
  "ALTER TABLE user_push_subscription MODIFY COLUMN endpoint varchar(512) NOT NULL COMMENT 'Web Push 端点地址'",
  "ALTER TABLE user_push_subscription MODIFY COLUMN p256dh varchar(255) NOT NULL COMMENT '客户端公钥'",
  "ALTER TABLE user_push_subscription MODIFY COLUMN auth varchar(255) NOT NULL COMMENT '客户端认证密钥'",
  "ALTER TABLE user_push_subscription MODIFY COLUMN user_agent varchar(512) COMMENT '浏览器 User-Agent'",
  "ALTER TABLE user_push_subscription MODIFY COLUMN enabled tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用'",
  "ALTER TABLE user_push_subscription MODIFY COLUMN create_time datetime COMMENT '创建时间'",
  "ALTER TABLE user_push_subscription MODIFY COLUMN update_time datetime COMMENT '更新时间'",
]

async function main() {
  const conn = await mysql.createConnection(config)
  try {
    await conn.query('SET NAMES utf8mb4')
    for (const sql of statements) {
      await conn.query(sql)
      console.log('OK:', sql.slice(0, 80))
    }
    const [rows] = await conn.query(
      "SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME IN ('todo','todo_owner','todo_notice_rule','todo_send_log','notification','user_push_subscription') ORDER BY TABLE_NAME",
      [config.database],
    )
    console.log('\n修复后表注释：')
    for (const row of rows) {
      console.log(`- ${row.TABLE_NAME}: ${row.TABLE_COMMENT}`)
    }
  } finally {
    await conn.end()
  }
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
