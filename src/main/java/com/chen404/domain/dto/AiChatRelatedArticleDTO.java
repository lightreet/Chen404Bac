package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 聊天相关推荐条目。
 */
@Data
@Schema(description = "AI 聊天相关推荐条目")
public class AiChatRelatedArticleDTO {

    @Schema(description = "文章 ID")
    private Long articleId;

    @Schema(description = "文章标题")
    private String articleTitle;

    @Schema(description = "文章链接", example = "/article/123")
    private String url;
}
