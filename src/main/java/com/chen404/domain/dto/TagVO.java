package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 标签视图对象。
 */
@Schema(description = "标签视图对象")
@Data
public class TagVO {

    @Schema(description = "标签ID", example = "1")
    private Long id;

    @Schema(description = "标签名称", example = "Spring")
    private String name;

    @Schema(description = "标签别名", example = "spring")
    private String slug;

    @Schema(description = "标签颜色", example = "#409EFF")
    private String color;

    @Schema(description = "文章数量", example = "12")
    private Integer articleCount;

    @Schema(description = "排序值", example = "0")
    private Integer sortOrder;

    @Schema(description = "状态：0-禁用 1-启用", example = "1")
    private Integer status;
}
