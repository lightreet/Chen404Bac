package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新分类命令对象。
 */
@Schema(description = "更新分类命令对象")
@Data
public class UpdateCategoryCommand {

    @Schema(description = "分类名称", example = "后端开发")
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称长度不能超过50个字符")
    private String name;

    @Schema(description = "分类别名", example = "backend")
    @Size(max = 100, message = "分类别名长度不能超过100个字符")
    private String slug;

    @Schema(description = "分类描述", example = "后端开发相关文章")
    @Size(max = 255, message = "分类描述长度不能超过255个字符")
    private String description;

    @Schema(description = "分类图标", example = "mdi:server")
    @Size(max = 255, message = "分类图标长度不能超过255个字符")
    private String icon;

    @Schema(description = "排序值", example = "0")
    private Integer sortOrder;

    @Schema(description = "状态：0-禁用 1-启用", example = "1")
    private Integer status;
}
