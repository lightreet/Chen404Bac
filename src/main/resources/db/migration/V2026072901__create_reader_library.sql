CREATE TABLE IF NOT EXISTS `reader_book` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '书籍 ID',
    `owner_user_id` BIGINT NOT NULL COMMENT '所有者用户 ID',
    `title` VARCHAR(255) NOT NULL COMMENT '书名',
    `author` VARCHAR(255) NULL COMMENT '作者',
    `description` TEXT NULL COMMENT '简介',
    `language` VARCHAR(40) NULL COMMENT '语言',
    `source_format` VARCHAR(20) NOT NULL COMMENT '源格式',
    `source_encoding` VARCHAR(40) NULL COMMENT '文本编码',
    `source_file_id` BIGINT NULL COMMENT '原始文件 ID',
    `source_file_url` VARCHAR(1000) NULL COMMENT '受保护原始文件地址',
    `content_checksum` CHAR(64) NOT NULL COMMENT '源文件 SHA-256',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ready' COMMENT '状态：ready/failed',
    `parse_message` VARCHAR(1000) NULL COMMENT '解析说明',
    `chapter_count` INT NOT NULL DEFAULT 0 COMMENT '章节数',
    `total_char_count` BIGINT NOT NULL DEFAULT 0 COMMENT '总字数',
    `cover_asset_id` BIGINT NULL COMMENT '封面资源 ID',
    `content_version` INT NOT NULL DEFAULT 1 COMMENT '正文版本',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_reader_book_owner_update` (`owner_user_id`, `update_time`, `id`),
    UNIQUE KEY `uk_reader_book_owner_checksum` (`owner_user_id`, `content_checksum`),
    KEY `idx_reader_book_source_file` (`source_file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私人书架书籍';

CREATE TABLE IF NOT EXISTS `reader_book_asset` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '书籍资源 ID',
    `book_id` BIGINT NOT NULL COMMENT '书籍 ID',
    `source_path` VARCHAR(1000) NOT NULL COMMENT '源文件内相对路径',
    `source_path_hash` CHAR(64) NOT NULL COMMENT '源路径 SHA-256',
    `file_name` VARCHAR(255) NULL COMMENT '文件名',
    `media_type` VARCHAR(120) NOT NULL COMMENT '媒体类型',
    `file_size` BIGINT NOT NULL COMMENT '文件大小',
    `content_hash` CHAR(64) NOT NULL COMMENT '内容 SHA-256',
    `asset_data` MEDIUMBLOB NOT NULL COMMENT '资源数据',
    `is_cover` TINYINT NOT NULL DEFAULT 0 COMMENT '是否封面',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reader_book_asset_path` (`book_id`, `source_path_hash`),
    KEY `idx_reader_book_asset_book` (`book_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='书籍内嵌图片资源';

CREATE TABLE IF NOT EXISTS `reader_chapter` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '章节 ID',
    `book_id` BIGINT NOT NULL COMMENT '书籍 ID',
    `chapter_order` INT NOT NULL COMMENT '章节顺序，从 0 开始',
    `title` VARCHAR(500) NOT NULL COMMENT '章节标题',
    `volume_title` VARCHAR(500) NULL COMMENT '所属卷或部',
    `source_href` VARCHAR(1000) NULL COMMENT '源文件内定位',
    `content_html` LONGTEXT NOT NULL COMMENT '清洗后的正文 HTML',
    `content_text` LONGTEXT NOT NULL COMMENT '纯文本正文',
    `char_count` INT NOT NULL DEFAULT 0 COMMENT '字符数',
    `content_hash` CHAR(64) NOT NULL COMMENT '正文 SHA-256',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reader_chapter_order` (`book_id`, `chapter_order`),
    KEY `idx_reader_chapter_book` (`book_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='书籍章节正文';

CREATE TABLE IF NOT EXISTS `reader_toc_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '目录项 ID',
    `book_id` BIGINT NOT NULL COMMENT '书籍 ID',
    `parent_id` BIGINT NULL COMMENT '父目录项 ID',
    `chapter_id` BIGINT NULL COMMENT '目标章节 ID',
    `item_order` INT NOT NULL COMMENT '同级排序',
    `depth` INT NOT NULL DEFAULT 0 COMMENT '目录深度',
    `label` VARCHAR(500) NOT NULL COMMENT '目录标题',
    `source_href` VARCHAR(1000) NULL COMMENT '源定位',
    `fragment` VARCHAR(500) NULL COMMENT '章节内锚点',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_reader_toc_book_parent` (`book_id`, `parent_id`, `item_order`, `id`),
    KEY `idx_reader_toc_chapter` (`chapter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='书籍多级目录';

CREATE TABLE IF NOT EXISTS `reader_progress` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '阅读进度 ID',
    `user_id` BIGINT NOT NULL COMMENT '用户 ID',
    `book_id` BIGINT NOT NULL COMMENT '书籍 ID',
    `chapter_id` BIGINT NULL COMMENT '当前章节 ID',
    `block_index` INT NOT NULL DEFAULT 0 COMMENT '正文块序号',
    `character_offset` INT NOT NULL DEFAULT 0 COMMENT '块内字符偏移',
    `progress_percent` DECIMAL(7,3) NOT NULL DEFAULT 0 COMMENT '全书进度百分比',
    `locator_context` VARCHAR(255) NULL COMMENT '位置上下文校验文本',
    `content_version` INT NOT NULL DEFAULT 1 COMMENT '保存时正文版本',
    `finished` TINYINT NOT NULL DEFAULT 0 COMMENT '是否读完',
    `last_read_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后阅读时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reader_progress_user_book` (`user_id`, `book_id`),
    KEY `idx_reader_progress_user_time` (`user_id`, `last_read_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨设备阅读进度';

CREATE TABLE IF NOT EXISTS `reader_preference` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '阅读偏好 ID',
    `user_id` BIGINT NOT NULL COMMENT '用户 ID',
    `font_size` INT NOT NULL DEFAULT 18 COMMENT '字号',
    `line_height` DECIMAL(4,2) NOT NULL DEFAULT 1.85 COMMENT '行高',
    `content_width` INT NOT NULL DEFAULT 720 COMMENT '正文宽度像素',
    `paragraph_spacing` INT NOT NULL DEFAULT 16 COMMENT '段间距像素',
    `theme` VARCHAR(20) NOT NULL DEFAULT 'light' COMMENT '主题：light/rose/dark',
    `font_family` VARCHAR(20) NOT NULL DEFAULT 'serif' COMMENT '字体：serif/sans',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reader_preference_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户阅读偏好';
