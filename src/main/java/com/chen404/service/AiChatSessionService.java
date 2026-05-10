package com.chen404.service;

import com.chen404.domain.dto.AiChatResponse;
import com.chen404.domain.dto.AiChatSessionDetailResponse;
import com.chen404.domain.entity.AiChatSession;

/**
 * AI 女仆会话持久化服务。
 * <p>
 * 负责会话归属校验、历史消息保存与会话恢复。
 */
public interface AiChatSessionService {

    /**
     * 创建或续用会话。
     *
     * @param sessionId       前端会话 ID，可为空
     * @param requesterId     当前用户 ID，游客为空
     * @param visitorId       游客匿名 ID，可为空
     * @param sourcePage      来源页面
     * @param sourceArticleId 来源文章 ID
     * @param titleHint       会话标题提示
     * @return 已确认归属的会话实体
     */
    AiChatSession ensureSession(String sessionId, Long requesterId, String visitorId, String sourcePage, Long sourceArticleId, String titleHint);

    /**
     * 保存用户消息。
     *
     * @param sessionId 会话 ID
     * @param content   消息正文
     */
    void saveUserMessage(String sessionId, String content);

    /**
     * 保存女仆回复。
     *
     * @param sessionId 会话 ID
     * @param response  结构化回复
     */
    void saveAssistantMessage(String sessionId, AiChatResponse response);

    /**
     * 读取可访问的会话详情。
     *
     * @param sessionId   会话 ID
     * @param requesterId 当前用户 ID，游客为空
     * @param visitorId   游客匿名 ID，可为空
     * @return 会话详情
     */
    AiChatSessionDetailResponse loadSessionDetail(String sessionId, Long requesterId, String visitorId);
}
