package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 文章 AI 辅助请求。
 * <p>
 * 传入标题、正文以及可选的“重新生成”上下文，
 * 由后端统一生成摘要与标签建议。
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

    @Schema(description = "是否重新生成，true 时要求返回与当前结果有明显差异的候选")
    private Boolean regenerate;

    @Schema(description = "当前已有摘要，用于重新生成时避免重复")
    @Size(max = 500, message = "当前摘要长度不能超过500个字符")
    private String currentSummary;

    @Schema(description = "当前已有标签，用于重新生成时避免重复")
    private List<String> currentTags;
}
