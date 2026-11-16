CREATE TABLE IF NOT EXISTS user_wechat_binding (
    user_id VARCHAR(36) NOT NULL,
    app_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    open_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, app_id),
    UNIQUE KEY uk_wechat_identity (app_id, open_id),
    CONSTRAINT fk_wechat_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
