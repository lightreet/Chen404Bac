package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 文章 AI 辅助响应。
 * <p>
 * 返回可直接用于编辑页的摘要与标签列表。
 */
@Data
@Schema(description = "文章 AI 辅助响应")
public class AiArticleAssistResponse {

    @Schema(description = "生成的摘要")
    private String summary;

    @Schema(description = "推荐标签列表")
    private List<String> tags;
}
