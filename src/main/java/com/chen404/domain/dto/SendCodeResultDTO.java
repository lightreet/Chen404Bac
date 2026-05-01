package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "验证码发送结果")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendCodeResultDTO {

    @Schema(description = "验证码有效期，单位秒", example = "300")
    private Integer expireSeconds;
}
