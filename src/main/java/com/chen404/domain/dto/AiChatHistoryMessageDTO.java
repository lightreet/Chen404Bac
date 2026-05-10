package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * AI 聊天历史消息 DTO。
 * <p>
 * 用于会话恢复时返回已保存的消息记录与引用卡片。
 */
@Data
@Schema(description = "AI 聊天历史消息")
public class AiChatHistoryMessageDTO {

    @Schema(description = "消息 ID")
    private String messageId;

    @Schema(description = "消息角色")
    private String role;

    @Schema(description = "消息正文")
    private String content;

    @Schema(description = "消息场景")
    private String scene;

    @Schema(description = "情绪标签")
    private String mood;

    @Schema(description = "引用列表")
    private List<AiChatCitationDTO> citations;

    @Schema(description = "快捷追问建议")
    private List<String> suggestions;
}
