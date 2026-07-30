ALTER TABLE `reader_book`
    ADD COLUMN `visibility` VARCHAR(20) NOT NULL DEFAULT 'private' COMMENT '可见范围：public/private' AFTER `language`,
    ADD KEY `idx_reader_book_visibility_update` (`visibility`, `update_time`, `id`);
