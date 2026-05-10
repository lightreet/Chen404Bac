package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * AI 女仆聊天请求。
 * <p>
 * 当前用于承载最近几轮消息、页面上下文与当前文章上下文，
 * 便于后端判断 helper / companion 场景并组装 prompt。
 */
@Data
@Schema(description = "AI 女仆聊天请求")
public class AiChatRequest {

    @Schema(description = "会话 ID，前端可为空，后端会自动生成", example = "sess_01")
    private String sessionId;

    @Schema(description = "游客匿名 ID，用于恢复未登录会话", example = "visitor_abcd1234")
    private String visitorId;

    @Valid
    @NotEmpty(message = "聊天消息不能为空")
    @Schema(description = "最近几轮聊天消息")
    private List<AiChatMessageDTO> messages;

    @Schema(description = "页面上下文，例如 home/article/about", example = "article")
    private String pageContext;

    @Schema(description = "当前文章 ID，若处于文章页则传入")
    private Long currentArticleId;

    @Schema(description = "当前文章标题，可选")
    private String currentArticleTitle;

    @Schema(description = "是否为流式请求，第一阶段仅保留字段")
    private Boolean stream;
}
