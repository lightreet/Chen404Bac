package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "表情项创建/更新参数")
public class EmojiItemUpsertDTO {

    @Schema(description = "表情包编码", example = "basic")
    private String packCode;

    @Schema(description = "短码（唯一，如 basic_smile）", example = "basic_smile")
    private String shortcode;

    @Schema(description = "名称", example = "微笑")
    private String label;

    @Schema(description = "分类", example = "emotion")
    private String category;

    @Schema(description = "类型：0-unicode 1-image", example = "0")
    private Integer type;

    @Schema(description = "Unicode 表情（type=0）")
    private String unicode;

    @Schema(description = "资源URL（type=1）")
    private String assetUrl;

    @Schema(description = "宽度（可选）", example = "24")
    private Integer width;

    @Schema(description = "高度（可选）", example = "24")
    private Integer height;

    @Schema(description = "是否启用：0-否 1-是", example = "1")
    private Integer enabled;

    @Schema(description = "排序号", example = "1")
    private Integer sort;
}

