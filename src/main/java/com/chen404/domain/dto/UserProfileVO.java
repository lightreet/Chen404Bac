package com.chen404.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户资料视图对象。
 */
@Schema(description = "用户资料视图对象")
@Data
public class UserProfileVO {

    @Schema(description = "用户ID", example = "10001")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "用户名", example = "chen404")
    private String username;

    @Schema(description = "昵称", example = "辰")
    private String nickname;

    @Schema(description = "邮箱", example = "chen404@example.com")
    private String email;

    @Schema(description = "手机号", example = "13800000000")
    private String phone;

    @Schema(description = "头像地址", example = "https://cdn.example.com/avatar.png")
    private String avatar;

    @Schema(description = "个人简介", example = "专注于后端开发与系统设计")
    private String bio;

    @Schema(description = "用户状态：0-禁用 1-启用", example = "1")
    private Integer status;

    @Schema(description = "角色值", example = "1")
    private Integer role;

    @Schema(description = "角色编码", example = "admin")
    private String roleCode;

    @Schema(description = "角色名称", example = "管理员")
    private String roleName;

    @Schema(description = "信任级别：0-读者 1-知友", example = "1")
    private Integer trustLevel;

    @Schema(description = "信任级别名称", example = "知友")
    private String trustLevelName;

    @Schema(description = "成员标签", example = "站点成员")
    private String memberLabel;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;

    @Schema(description = "最后登录IP", example = "127.0.0.1")
    private String lastLoginIp;
}
