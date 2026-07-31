package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员通过邮件令牌确认好友申请。
 */
@Data
@Schema(description = "邮件好友申请审批参数")
public class EmailApproveTrustRequestDTO {

    @NotBlank(message = "邮件审批 token 不能为空")
    @Schema(description = "邮件中的一次性审批 token", requiredMode = Schema.RequiredMode.REQUIRED)
    private String token;
}
