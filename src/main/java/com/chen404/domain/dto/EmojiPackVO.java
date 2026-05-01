package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "表情包视图对象")
@Data
public class EmojiPackVO {

    @Schema(description = "表情包ID", example = "1")
    private Long id;

    @Schema(description = "表情包编码", example = "default")
    private String packCode;

    @Schema(description = "表情包名称", example = "默认表情")
    private String name;

    @Schema(description = "表情包描述", example = "站点默认表情包")
    private String description;

    @Schema(description = "图标地址", example = "https://cdn.example.com/icon.png")
    private String iconUrl;

    @Schema(description = "是否启用：0-否 1-是", example = "1")
    private Integer enabled;

    @Schema(description = "排序值", example = "0")
    private Integer sort;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
