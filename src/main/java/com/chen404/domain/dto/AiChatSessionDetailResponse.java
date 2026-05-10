package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * AI 会话详情响应。
 * <p>
 * 用于前端恢复指定会话下的历史消息列表。
 */
@Data
@Schema(description = "AI 会话详情响应")
public class AiChatSessionDetailResponse {

    @Schema(description = "会话 ID")
    private String sessionId;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "来源页面")
    private String sourcePage;

    @Schema(description = "来源文章 ID")
    private Long sourceArticleId;

    @Schema(description = "历史消息列表")
    private List<AiChatHistoryMessageDTO> messages;
}
