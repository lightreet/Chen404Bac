package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文章标签摘要。
 */
@Schema(description = "文章标签摘要")
@Data
public class ArticleTagVO {

    @Schema(description = "标签ID", example = "8")
    private Long id;

    @Schema(description = "标签名称", example = "Spring")
    private String name;

    @Schema(description = "标签别名", example = "spring")
    private String slug;

    @Schema(description = "标签颜色", example = "#409EFF")
    private String color;
}
