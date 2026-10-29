CREATE TABLE IF NOT EXISTS login_attempt_bucket (
    bucket_type varchar(8) NOT NULL,
    bucket_hash char(64) NOT NULL,
    failures int NOT NULL DEFAULT 0,
    last_failure datetime(6) NOT NULL,
    locked_until datetime(6) NULL,
    PRIMARY KEY (bucket_type, bucket_hash),
    KEY idx_login_attempt_last_failure (last_failure)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
