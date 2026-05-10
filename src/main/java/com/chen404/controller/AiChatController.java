package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.dto.AiChatRequest;
import com.chen404.domain.dto.AiChatResponse;
import com.chen404.domain.dto.AiChatSessionDetailResponse;
import com.chen404.exception.BadRequestException;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.AiChatService;
import com.chen404.util.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 女仆聊天控制器。
 * <p>
 * 第一阶段开放非流式聊天接口，供前端 Live2D 面板调用。
 * 游客可访问，但回答会根据当前登录状态决定可用上下文。
 */
@Tag(name = "AI 女仆", description = "Lyra 聊天与页面引导能力")
@RestController
public class AiChatController {

    private static final Logger log = LoggerFactory.getLogger(AiChatController.class);
    private static final int HTTP_BAD_REQUEST = 400;

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @Operation(summary = "女仆聊天", description = "根据最近几轮消息、页面上下文与当前文章上下文生成 Lyra 回复")
    @PostMapping("/ai/chat")
    public Result<AiChatResponse> chat(
            @Valid @RequestBody AiChatRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        try {
            Long requesterId = CurrentUserUtil.getUserId(currentUser);
            return Result.success(aiChatService.chat(request, requesterId));
        } catch (BadRequestException e) {
            return Result.error(HTTP_BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("[AI_CHAT_BAD_STATE] pageContext={} articleId={} message={}",
                    request.getPageContext(), request.getCurrentArticleId(), e.getMessage(), e);
            return Result.error(HTTP_BAD_REQUEST, e.getMessage());
        }
    }

    @Operation(summary = "女仆流式聊天", description = "以 SSE 方式逐步返回 Lyra 回复，支持前端停止生成")
    @PostMapping(value = "/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @Valid @RequestBody AiChatRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long requesterId = CurrentUserUtil.getUserId(currentUser);
        return aiChatService.streamChat(request, requesterId);
    }

    @Operation(summary = "读取女仆会话详情", description = "根据会话 ID 恢复最近一次与 Lyra 的聊天记录")
    @GetMapping("/ai/chat/sessions/{sessionId}")
    public Result<AiChatSessionDetailResponse> getSessionDetail(
            @PathVariable String sessionId,
            @RequestParam(required = false) String visitorId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        try {
            Long requesterId = CurrentUserUtil.getUserId(currentUser);
            return Result.success(aiChatService.getSessionDetail(sessionId, requesterId, visitorId));
        } catch (BadRequestException e) {
            return Result.error(HTTP_BAD_REQUEST, e.getMessage());
        }
    }
}
