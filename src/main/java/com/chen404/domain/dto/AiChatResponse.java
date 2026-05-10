package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * AI 女仆聊天响应。
 * <p>
 * 返回给前端的内容拆分为短回复、引用、相关推荐与追问建议，
 * 便于女仆气泡与聊天面板分别展示。
 */
@Data
@Schema(description = "AI 女仆聊天响应")
public class AiChatResponse {

    @Schema(description = "会话 ID")
    private String sessionId;

    @Schema(description = "消息 ID")
    private String messageId;

    @Schema(description = "场景，helper / companion")
    private String scene;

    @Schema(description = "女仆短回复")
    private String replyText;

    @Schema(description = "情绪标签", example = "happy")
    private String mood;

    @Schema(description = "引用文章列表")
    private List<AiChatCitationDTO> citations;

    @Schema(description = "相关推荐列表")
    private List<AiChatRelatedArticleDTO> relatedArticles;

    @Schema(description = "快捷追问建议")
    private List<String> suggestions;

    @Schema(description = "追踪 ID")
    private String traceId;

    @Schema(description = "结束原因")
    private String finishReason;
}
