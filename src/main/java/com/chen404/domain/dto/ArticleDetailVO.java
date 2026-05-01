package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文章详情视图对象。
 */
@Schema(description = "文章详情视图对象")
@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleDetailVO extends ArticleListItemVO {

    @Schema(description = "Markdown 正文内容", example = "# 标题\\n正文内容")
    private String content;

    @Schema(description = "服务端渲染后的 HTML 内容")
    private String contentHtml;

    @Schema(description = "是否原创：0-转载 1-原创", example = "1")
    private Integer isOriginal;

    @Schema(description = "转载原文链接", example = "https://example.com/original-post")
    private String originalUrl;

    @Schema(description = "当前用户是否可评论", example = "true")
    private Boolean canComment;
}
