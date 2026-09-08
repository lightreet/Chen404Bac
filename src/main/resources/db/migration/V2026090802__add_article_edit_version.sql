ALTER TABLE article ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '内容编辑版本，互动计数不递增';
