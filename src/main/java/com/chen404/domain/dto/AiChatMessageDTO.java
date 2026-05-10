package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 聊天消息单元。
 * <p>
 * 第一阶段仅支持 user / assistant 两类消息，
 * 用于传递最近几轮对话上下文。
 */
@Data
@Schema(description = "AI 聊天消息单元")
public class AiChatMessageDTO {

    @NotBlank(message = "消息角色不能为空")
    @Schema(description = "消息角色，仅支持 user / assistant", example = "user")
    private String role;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "单条消息不能超过2000个字符")
    @Schema(description = "消息正文", example = "帮我总结一下这篇文章")
    private String content;
}
