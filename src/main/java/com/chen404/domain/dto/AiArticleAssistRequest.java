package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文章 AI 辅助请求。
 * <p>
 * 仅传入标题和正文，由后端统一生成摘要与标签建议。
 */
@Data
@Schema(description = "文章 AI 辅助请求")
public class AiArticleAssistRequest {

    @Schema(description = "文章标题", example = "Spring Boot 接入大模型自动生成博客摘要")
    @Size(max = 100, message = "标题长度不能超过100个字符")
    private String title;

    @NotBlank(message = "文章内容不能为空")
    @Schema(description = "Markdown 正文内容", example = "# 标题\n正文内容")
    private String content;
}
