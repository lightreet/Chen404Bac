package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分类视图对象。
 */
@Schema(description = "分类视图对象")
@Data
public class CategoryVO {

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

    @Schema(description = "文章数量", example = "18")
    private Integer articleCount;

    @Schema(description = "排序值", example = "0")
    private Integer sortOrder;

    @Schema(description = "状态：0-禁用 1-启用", example = "1")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
