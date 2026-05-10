CREATE TABLE IF NOT EXISTS `ai_chat_session` (
  `session_id` varchar(64) NOT NULL COMMENT '会话ID',
  `user_id` bigint DEFAULT NULL COMMENT '登录用户ID',
  `visitor_id` varchar(64) DEFAULT NULL COMMENT '游客匿名ID',
  `title` varchar(200) DEFAULT NULL COMMENT '会话标题',
  `source_page` varchar(32) DEFAULT NULL COMMENT '会话来源页面',
  `source_article_id` bigint DEFAULT NULL COMMENT '来源文章ID',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1-活跃 0-关闭',
  `last_message_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近消息时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`session_id`) USING BTREE,
  KEY `idx_ai_chat_session_user` (`user_id`, `last_message_at`) USING BTREE,
  KEY `idx_ai_chat_session_visitor` (`visitor_id`, `last_message_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI女仆会话表';

CREATE TABLE IF NOT EXISTS `ai_chat_message` (
  `message_id` varchar(64) NOT NULL COMMENT '消息ID',
  `session_id` varchar(64) NOT NULL COMMENT '会话ID',
  `role` varchar(16) NOT NULL COMMENT '消息角色 user/assistant',
  `scene` varchar(16) DEFAULT NULL COMMENT '消息场景 helper/companion',
  `content` text NOT NULL COMMENT '消息正文',
  `mood` varchar(32) DEFAULT NULL COMMENT '情绪标签',
  `status` varchar(16) NOT NULL DEFAULT 'completed' COMMENT '消息状态',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '追踪ID',
  `citations_json` mediumtext DEFAULT NULL COMMENT '引用结果快照(JSON)',
  `related_articles_json` mediumtext DEFAULT NULL COMMENT '相关文章快照(JSON)',
  `suggestions_json` text DEFAULT NULL COMMENT '快捷追问(JSON)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`message_id`) USING BTREE,
  KEY `idx_ai_chat_message_session` (`session_id`, `create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI女仆消息表';

CREATE TABLE IF NOT EXISTS `article_ai_chunk` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '切片ID',
  `article_id` bigint NOT NULL COMMENT '文章ID',
  `chunk_index` int NOT NULL COMMENT '切片顺序',
  `chunk_type` varchar(16) NOT NULL COMMENT '切片类型 title/summary/content',
  `article_title` varchar(255) NOT NULL COMMENT '文章标题快照',
  `content_chunk` mediumtext NOT NULL COMMENT '切片正文',
  `status` tinyint NOT NULL COMMENT '文章状态快照',
  `visibility` tinyint NOT NULL COMMENT '可见性快照',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间快照',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_article_ai_chunk_article` (`article_id`, `chunk_index`) USING BTREE,
  KEY `idx_article_ai_chunk_status_visibility` (`status`, `visibility`, `publish_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章AI知识切片表';
