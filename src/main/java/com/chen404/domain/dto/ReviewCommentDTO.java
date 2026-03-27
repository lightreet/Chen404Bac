package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "审核评论请求参数")
public class ReviewCommentDTO {

    @Schema(description = "审核状态：1-通过 2-拒绝", example = "1")
    private Integer status;
}
