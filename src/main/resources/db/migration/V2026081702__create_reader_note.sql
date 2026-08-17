CREATE TABLE IF NOT EXISTS `reader_note` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '阅读笔记 ID',
    `user_id` BIGINT NOT NULL COMMENT '记录用户 ID',
    `book_id` BIGINT NOT NULL COMMENT '书籍 ID',
    `chapter_id` BIGINT NOT NULL COMMENT '记录时章节 ID',
    `chapter_order` INT NOT NULL COMMENT '记录时章节顺序，从 0 开始',
    `chapter_title` VARCHAR(500) NOT NULL COMMENT '记录时章节标题快照',
    `chapter_content_hash` CHAR(64) NULL COMMENT '记录时章节正文哈希',
    `start_block_index` INT NOT NULL COMMENT '选区起始正文块序号',
    `start_character_offset` INT NOT NULL COMMENT '选区起始块内字符偏移',
    `end_block_index` INT NOT NULL COMMENT '选区结束正文块序号',
    `end_character_offset` INT NOT NULL COMMENT '选区结束块内字符偏移',
    `excerpt` TEXT NOT NULL COMMENT '记录的原文片段',
    `reflection` TEXT NULL COMMENT '用户感悟，可为空',
    `highlight_color` VARCHAR(20) NOT NULL DEFAULT 'rose' COMMENT '高亮色：rose/sage/blue/amber',
    `prefix_context` VARCHAR(255) NULL COMMENT '选区前文定位上下文',
    `suffix_context` VARCHAR(255) NULL COMMENT '选区后文定位上下文',
    `content_version` INT NOT NULL COMMENT '记录时书籍正文版本',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_reader_note_user_book_position` (
        `user_id`, `book_id`, `chapter_order`, `start_block_index`, `start_character_offset`, `id`
    ),
    KEY `idx_reader_note_book` (`book_id`, `id`),
    KEY `idx_reader_note_chapter` (`chapter_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户私有阅读笔记';
