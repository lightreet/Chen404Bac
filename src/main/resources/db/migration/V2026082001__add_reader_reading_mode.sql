ALTER TABLE `reader_preference`
    ADD COLUMN `reading_mode` VARCHAR(20) NOT NULL DEFAULT 'paged'
        COMMENT '阅读方式：paged/continuous'
        AFTER `font_family`;
