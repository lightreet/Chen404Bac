package com.chen404.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@TableName("sys_user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码（BCrypt加密）
     */
    private String password;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 头像URL（库内冗余；接口展示优先按 {@link #avatarFileId} 解析 sys_file）
     */
    private String avatar;

    /**
     * 头像对应 sys_file.id（接口不返回）
     */
    @JsonIgnore
    @TableField("avatar_file_id")
    private Long avatarFileId;

    /**
     * 个人简介
     */
    private String bio;

    /**
     * 状态：0-禁用 1-启用
     */
    private Integer status;

    /**
     * 信任级别：0-读者 1-知友
     */
    private Integer trustLevel;

    /**
     * 邮箱是否验证：0-否 1-是
     */
    private Integer emailVerified;

    /**
     * 手机是否验证：0-否 1-是
     */
    private Integer phoneVerified;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * 最后登录IP
     */
    private String lastLoginIp;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;

    /**
     * 角色ID（非数据库字段）
     */
    @TableField(exist = false)
    private Integer role;

    /**
     * 角色编码（非数据库字段）
     */
    @TableField(exist = false)
    private String roleCode;

    @TableField(exist = false)
    private String roleName;

    @TableField(exist = false)
    private String trustLevelName;

    @TableField(exist = false)
    private String memberLabel;
}
