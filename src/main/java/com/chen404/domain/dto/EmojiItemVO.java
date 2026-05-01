package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "表情项视图对象")
@Data
public class EmojiItemVO {

    @Schema(description = "表情项ID", example = "1")
    private Long id;

    @Schema(description = "表情包编码", example = "default")
    private String packCode;

    @Schema(description = "短代码", example = ":smile:")
    private String shortcode;

    @Schema(description = "显示名称", example = "微笑")
    private String label;

    @Schema(description = "分类", example = "basic")
    private String category;

    @Schema(description = "类型：0-unicode 1-image", example = "0")
    private Integer type;

    @Schema(description = "Unicode 字符串", example = "😄")
    private String unicode;

    @Schema(description = "图片资源地址", example = "https://cdn.example.com/emoji/smile.png")
    private String assetUrl;

    @Schema(description = "宽度", example = "64")
    private Integer width;

    @Schema(description = "高度", example = "64")
    private Integer height;

    @Schema(description = "是否启用：0-否 1-是", example = "1")
    private Integer enabled;

    @Schema(description = "排序值", example = "0")
    private Integer sort;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
