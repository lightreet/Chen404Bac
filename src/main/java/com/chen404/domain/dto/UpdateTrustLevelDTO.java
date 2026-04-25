package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理员更新用户信任级别请求 DTO。
 */
@Data
@Schema(description = "更新用户信任级别请求参数")
public class UpdateTrustLevelDTO {

    @Schema(description = "信任级别：0-读者 1-知友", example = "1")
    private Integer trustLevel;
}
