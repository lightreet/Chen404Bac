package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 聊天引用条目。
 * <p>
 * 用于向前端返回可核对的文章来源卡片。
 */
@Data
@Schema(description = "AI 聊天引用条目")
public class AiChatCitationDTO {

    @Schema(description = "引用文章 ID")
    private Long articleId;

    @Schema(description = "引用文章标题")
    private String articleTitle;

    @Schema(description = "引用跳转链接", example = "/article/123")
    private String url;

    @Schema(description = "引用片段摘要")
    private String snippet;
}
