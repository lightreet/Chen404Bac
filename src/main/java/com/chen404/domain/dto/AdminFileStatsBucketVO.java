package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "文件统计分桶")
@Data
public class AdminFileStatsBucketVO {

    @Schema(description = "分桶键", example = "REFERENCED")
    private String key;

    @Schema(description = "分桶标签", example = "Referenced")
    private String label;

    @Schema(description = "分桶数量", example = "42")
    private Long count;
}
