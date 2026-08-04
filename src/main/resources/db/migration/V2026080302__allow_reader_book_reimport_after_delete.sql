ALTER TABLE `reader_book`
    DROP INDEX `uk_reader_book_owner_checksum`,
    ADD COLUMN `active_content_checksum` CHAR(64)
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN `content_checksum` ELSE NULL END
        ) STORED COMMENT '未删除书籍的文件校验值',
    ADD UNIQUE KEY `uk_reader_book_owner_checksum` (`owner_user_id`, `active_content_checksum`);
