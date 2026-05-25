package com.chen404.service.support.scenario.chat;

import com.chen404.domain.dto.AiAdminConfigDTO;
import com.chen404.domain.dto.AiChatMessageDTO;
import com.chen404.domain.entity.Article;
import com.chen404.service.support.chat.ArticleKnowledgeHit;
import com.chen404.service.support.prompt.AiMaidPromptScene;

import java.util.List;

/**
 * 女仆聊天场景请求。
 *
 * @param scene            场景模式
 * @param systemPrompt     系统提示词
 * @param messages         聊天消息
 * @param pageContext      页面上下文
 * @param currentArticleId 当前文章 ID
 * @param currentArticle   当前文章
 * @param knowledgeHits    检索命中
 */
public record MaidChatScenarioRequest(
        AiMaidPromptScene scene,
        String systemPrompt,
        List<AiChatMessageDTO> messages,
        String pageContext,
        Long currentArticleId,
        Article currentArticle,
        List<ArticleKnowledgeHit> knowledgeHits,
        AiAdminConfigDTO aiConfig
) {
}
