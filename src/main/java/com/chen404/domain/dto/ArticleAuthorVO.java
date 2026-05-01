package com.chen404.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文章作者摘要。
 */
@Schema(description = "文章作者摘要")
@Data
public class ArticleAuthorVO {

    @Schema(description = "作者ID", example = "10001")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "用户名", example = "chen404")
    private String username;

    @Schema(description = "昵称", example = "辰")
    private String nickname;

    @Schema(description = "头像地址", example = "https://cdn.example.com/avatar.png")
    private String avatar;

    @Schema(description = "个人简介", example = "专注于后端开发与系统设计")
    private String bio;
}
