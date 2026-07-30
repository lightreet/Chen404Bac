ALTER TABLE `reader_book`
    ADD COLUMN `cover_file_id` BIGINT NULL COMMENT '用户上传的自定义封面文件 ID' AFTER `cover_asset_id`;

CREATE INDEX `idx_reader_book_cover_file` ON `reader_book` (`cover_file_id`);
