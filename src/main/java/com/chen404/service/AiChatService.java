package com.chen404.service;

import com.chen404.domain.dto.AiChatRequest;
import com.chen404.domain.dto.AiChatResponse;
import com.chen404.domain.dto.AiChatSessionDetailResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 女仆聊天服务接口。
 * <p>
 * 负责接收前端聊天请求，组装页面上下文和角色 prompt，
 * 并返回可供 Live2D 面板展示的结构化结果。
 */
public interface AiChatService {

    /**
     * 生成一轮 AI 女仆回复。
     *
     * @param request     前端聊天请求
     * @param requesterId 当前登录用户 ID，游客为空
     * @return 结构化聊天响应
     */
    AiChatResponse chat(AiChatRequest request, Long requesterId);

    /**
     * 生成一轮流式 AI 女仆回复。
     *
     * @param request     前端聊天请求
     * @param requesterId 当前登录用户 ID，游客为空
     * @return SSE 发射器
     */
    SseEmitter streamChat(AiChatRequest request, Long requesterId);

    /**
     * 读取指定会话详情。
     *
     * @param sessionId   会话 ID
     * @param requesterId 当前登录用户 ID，游客为空
     * @param visitorId   游客匿名 ID
     * @return 会话详情
     */
    AiChatSessionDetailResponse getSessionDetail(String sessionId, Long requesterId, String visitorId);
}
