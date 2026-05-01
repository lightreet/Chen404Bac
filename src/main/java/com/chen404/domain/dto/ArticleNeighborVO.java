package com.chen404.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文章上一篇/下一篇摘要。
 */
@Schema(description = "文章上一篇/下一篇摘要")
@Data
public class ArticleNeighborVO {

    @Schema(description = "文章ID", example = "100")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "文章标题", example = "上一篇文章标题")
    private String title;
}
