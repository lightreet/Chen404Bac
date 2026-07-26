package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "审核评论请求参数")
public class ReviewCommentDTO {

    @Schema(description = "审核状态：1-通过 2-拒绝", example = "1")
    @NotNull(message = "审核状态不能为空")
    @Min(value = 1, message = "审核状态只能为 1 或 2")
    @Max(value = 2, message = "审核状态只能为 1 或 2")
    private Integer status;
}
