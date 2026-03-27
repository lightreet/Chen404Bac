package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "表情包创建/更新参数")
public class EmojiPackUpsertDTO {

    @Schema(description = "表情包编码（唯一）", example = "basic")
    private String packCode;

    @Schema(description = "表情包名称", example = "基础表情")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "图标URL")
    private String iconUrl;

    @Schema(description = "是否启用：0-否 1-是", example = "1")
    private Integer enabled;

    @Schema(description = "排序号", example = "10")
    private Integer sort;
}

