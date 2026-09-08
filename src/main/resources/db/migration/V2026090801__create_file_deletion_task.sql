CREATE TABLE file_deletion_task (
    file_id BIGINT NOT NULL PRIMARY KEY,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_file_deletion_due (next_attempt_at, file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提交后可重试的文件删除任务';
