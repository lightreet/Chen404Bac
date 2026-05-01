package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "表情包导入结果")
@Data
public class EmojiImportResultDTO {

    @Schema(description = "导入的表情包编码", example = "default")
    private String packCode;

    @Schema(description = "成功导入数量", example = "18")
    private Integer successCount;

    @Schema(description = "失败数量", example = "2")
    private Integer failCount;

    @Schema(description = "失败详情列表")
    private List<EmojiImportErrorDTO> errors;

    @Schema(description = "表情包导入失败项")
    @Data
    public static class EmojiImportErrorDTO {

        @Schema(description = "失败的短代码", example = ":smile:")
        private String shortcode;

        @Schema(description = "失败原因", example = "图片文件缺失")
        private String error;
    }
}
