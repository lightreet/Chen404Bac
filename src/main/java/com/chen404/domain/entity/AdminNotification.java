package com.chen404.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 面向管理员日常使用的站点业务消息。
 */
@Data
@TableName("admin_notification")
public class AdminNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int UNREAD = 0;
    public static final int READ = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recipientUserId;

    private String eventType;

    private Long actorUserId;

    private String resourceType;

    private Long resourceId;

    private String title;

    private String summary;

    private Integer readStatus;

    private LocalDateTime readTime;

    private String dedupeKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
