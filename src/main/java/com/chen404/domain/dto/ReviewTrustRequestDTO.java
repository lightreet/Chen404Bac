package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 好友申请审核请求 DTO。
 */
@Schema(description = "好友申请审核请求参数")
@Data
public class ReviewTrustRequestDTO {

    @Schema(description = "审核备注，可写明通过或拒绝原因", example = "欢迎成为知友")
    private String reviewNote;
}
