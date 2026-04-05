package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新个人资料请求 DTO
 */
@Schema(description = "更新个人资料请求参数")
@Data
public class UpdateProfileDTO {

    @Schema(description = "昵称", required = true, example = "小陈同学")
    @NotBlank(message = "昵称不能为空")
    @Size(min = 2, max = 20, message = "昵称长度 2-20 位")
    private String nickname;

    @Schema(description = "头像 URL", required = true)
    @NotBlank(message = "头像不能为空")
    private String avatar;
}
