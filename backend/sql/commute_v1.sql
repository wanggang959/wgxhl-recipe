CREATE TABLE IF NOT EXISTS commute_settings (
    user_id varchar(36) NOT NULL,
    arrival_time time NOT NULL,
    notice_time time NOT NULL,
    buffer_minutes int NOT NULL DEFAULT 10,
    fallback_minutes int NOT NULL DEFAULT 45,
    work_days varchar(20) NOT NULL DEFAULT '1,2,3,4,5',
    origin_name varchar(200) NOT NULL,
    origin_lat decimal(9,6) NOT NULL,
    origin_lon decimal(9,6) NOT NULL,
    destination_name varchar(200) NOT NULL,
    destination_lat decimal(9,6) NOT NULL,
    destination_lon decimal(9,6) NOT NULL,
    email_enabled tinyint NOT NULL DEFAULT 0,
    create_time datetime NOT NULL,
    update_time datetime NOT NULL,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人通勤设置';

CREATE TABLE IF NOT EXISTS commute_record (
    id varchar(36) NOT NULL,
    user_id varchar(36) NOT NULL,
    commute_date date NOT NULL,
    direction varchar(10) NOT NULL,
    departure_time datetime DEFAULT NULL,
    arrival_time datetime DEFAULT NULL,
    duration_minutes int DEFAULT NULL,
    transport varchar(30) DEFAULT NULL,
    remark varchar(500) DEFAULT NULL,
    include_stats tinyint NOT NULL DEFAULT 1,
    weather_group varchar(20) DEFAULT NULL,
    weather_summary varchar(200) DEFAULT NULL,
    weather_forecast_time datetime DEFAULT NULL,
    create_time datetime NOT NULL,
    update_time datetime NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_commute_user_day_direction (user_id, commute_date, direction),
    KEY idx_commute_user_date (user_id, commute_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人通勤记录';

CREATE TABLE IF NOT EXISTS commute_notice_log (
    id varchar(36) NOT NULL,
    user_id varchar(36) NOT NULL,
    notice_date date NOT NULL,
    status varchar(20) NOT NULL,
    error_message varchar(300) DEFAULT NULL,
    create_time datetime NOT NULL,
    update_time datetime NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_commute_notice_user_day (user_id, notice_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通勤提醒发送记录';
