INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.llm.enabled', 'false', 'false', 'AI model call enabled', 3, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.llm.enabled');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.llm.base_url', 'https://api.openai.com/v1', 'https://api.openai.com/v1', 'AI model base URL', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.llm.base_url');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.llm.model', 'gpt-5.4-mini', 'gpt-5.4-mini', 'AI model name', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.llm.model');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.llm.api_style', 'chat-completions', 'chat-completions', 'AI API style', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.llm.api_style');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.llm.api_key', '', '', 'AI API key', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.llm.api_key');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.llm.temperature', '0.2', '0.2', 'AI temperature', 2, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.llm.temperature');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.llm.max_tokens', '512', '512', 'AI max tokens', 2, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.llm.max_tokens');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.llm.timeout_seconds', '30', '30', 'AI timeout seconds', 2, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.llm.timeout_seconds');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.maid.name', 'Lyra', 'Lyra', 'AI maid name', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.maid.name');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.maid.persona_version', 'v1.1', 'v1.1', 'AI maid persona version', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.maid.persona_version');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.maid.system_prompt', '', '', 'AI maid system prompt', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.maid.system_prompt');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.maid.helper_prompt', '', '', 'AI maid helper prompt', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.maid.helper_prompt');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.maid.companion_prompt', '', '', 'AI maid companion prompt', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.maid.companion_prompt');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.chat.enabled', 'true', 'true', 'AI chat enabled', 3, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.chat.enabled');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.chat.retrieval_enabled', 'true', 'true', 'AI chat retrieval enabled', 3, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.chat.retrieval_enabled');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.chat.max_citation_count', '3', '3', 'AI chat max citation count', 2, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.chat.max_citation_count');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.chat.max_context_messages', '8', '8', 'AI chat max context messages', 2, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.chat.max_context_messages');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.chat.max_article_content_chars', '3000', '3000', 'AI chat max article content chars', 2, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.chat.max_article_content_chars');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.chat.max_article_summary_chars', '300', '300', 'AI chat max article summary chars', 2, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.chat.max_article_summary_chars');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.chat.max_suggestion_count', '3', '3', 'AI chat max suggestion count', 2, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.chat.max_suggestion_count');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.chat.related_article_limit', '2', '2', 'AI chat related article limit', 2, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.chat.related_article_limit');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.chat.require_recommend_intent_for_related_articles', 'true', 'true', 'AI chat require recommend intent', 3, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.chat.require_recommend_intent_for_related_articles');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.chat.bubble_max_chars', '36', '36', 'AI chat bubble max chars', 2, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.chat.bubble_max_chars');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.chat.bubble_long_reply_text', '我整理好了，打开聊天框看详细内容吧。', '我整理好了，打开聊天框看详细内容吧。', 'AI chat long bubble text', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.chat.bubble_long_reply_text');

INSERT INTO `site_config` (`config_key`, `config_value`, `default_value`, `description`, `config_type`, `is_system`, `is_public`)
SELECT 'ai.tools.web_search_enabled', 'false', 'false', 'AI web search enabled', 3, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `site_config` WHERE `config_key` = 'ai.tools.web_search_enabled');
