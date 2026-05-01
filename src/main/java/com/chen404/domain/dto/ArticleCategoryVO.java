package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文章分类摘要。
 */
@Schema(description = "文章分类摘要")
@Data
public class ArticleCategoryVO {

    @Schema(description = "分类ID", example = "3")
    private Long id;

    @Schema(description = "分类名称", example = "后端开发")
    private String name;

    @Schema(description = "分类别名", example = "backend")
    private String slug;

    @Schema(description = "分类描述", example = "后端开发相关文章")
    private String description;

    @Schema(description = "分类图标", example = "mdi:server")
    private String icon;
}
