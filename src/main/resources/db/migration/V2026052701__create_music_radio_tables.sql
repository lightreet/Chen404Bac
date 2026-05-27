CREATE TABLE IF NOT EXISTS `music_track` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '歌曲 ID',
    `title` VARCHAR(120) NOT NULL COMMENT '歌名',
    `artist` VARCHAR(120) NOT NULL COMMENT '歌手',
    `album` VARCHAR(160) NULL COMMENT '专辑',
    `release_year` INT NULL COMMENT '发行年份',
    `language` VARCHAR(40) NULL COMMENT '语言',
    `genre` VARCHAR(60) NULL COMMENT '风格',
    `tags` VARCHAR(500) NULL COMMENT '逗号分隔标签',
    `audio_file_id` BIGINT NULL COMMENT '音频文件 ID',
    `audio_url` VARCHAR(1000) NOT NULL COMMENT '音频播放地址',
    `cover_file_id` BIGINT NULL COMMENT '封面文件 ID',
    `cover_url` VARCHAR(1000) NULL COMMENT '封面地址',
    `lyric_type` VARCHAR(20) NOT NULL DEFAULT 'plain' COMMENT '歌词类型：plain/lrc',
    `lyrics` MEDIUMTEXT NULL COMMENT '歌词内容',
    `lyric_source` VARCHAR(255) NULL COMMENT '歌词来源备注',
    `recommendation` VARCHAR(500) NULL COMMENT '站长推荐语',
    `mood_text` VARCHAR(255) NULL COMMENT '心情短句',
    `status` VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '状态：draft/published/archived',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_music_track_status_sort` (`status`, `sort_order`, `id`),
    KEY `idx_music_track_audio_file` (`audio_file_id`),
    KEY `idx_music_track_cover_file` (`cover_file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音乐曲库';

CREATE TABLE IF NOT EXISTS `music_playlist` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '歌单 ID',
    `name` VARCHAR(120) NOT NULL COMMENT '歌单名称',
    `description` VARCHAR(500) NULL COMMENT '歌单描述',
    `cover_file_id` BIGINT NULL COMMENT '封面文件 ID',
    `cover_url` VARCHAR(1000) NULL COMMENT '封面地址',
    `opening_text` VARCHAR(255) NULL COMMENT '电台开场文案',
    `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认电台',
    `is_public` TINYINT NOT NULL DEFAULT 1 COMMENT '是否公开',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_music_playlist_public_sort` (`is_public`, `sort_order`, `id`),
    KEY `idx_music_playlist_default` (`is_default`, `is_public`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音乐歌单';

CREATE TABLE IF NOT EXISTS `music_playlist_track` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '歌单歌曲关联 ID',
    `playlist_id` BIGINT NOT NULL COMMENT '歌单 ID',
    `track_id` BIGINT NOT NULL COMMENT '歌曲 ID',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '歌单内排序',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_music_playlist_track` (`playlist_id`, `track_id`),
    KEY `idx_music_playlist_track_sort` (`playlist_id`, `sort_order`, `id`),
    KEY `idx_music_playlist_track_track` (`track_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音乐歌单歌曲关联';
