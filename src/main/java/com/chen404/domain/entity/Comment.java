package com.chen404.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("comment")
public class Comment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    private Long parentId;

    private Long rootId;

    private String content;

    private String authorName;

    private String authorEmail;

    private String authorWebsite;

    private String authorAvatar;

    private Long authorId;

    private String ip;

    private String location;

    private String userAgent;

    private Integer status;

    private Integer isAdmin;

    private Integer likeCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    // ========== 非数据库字段 ==========

    @TableField(exist = false)
    private List<Comment> children;

    @TableField(exist = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long replyToUserId;

    @TableField(exist = false)
    private String replyToAuthorName;

    /**
     * 游客评论自助删除 key（明文仅在创建时返回一次；不入库）
     */
    @TableField(exist = false)
    private String guestDeleteKey;

    /**
     * 当前登录用户是否已点赞该评论
     */
    @TableField(exist = false)
    private Boolean likedByMe;

    public interface Status {
        int PENDING = 0;
        int APPROVED = 1;
        int REJECTED = 2;
    }
}
