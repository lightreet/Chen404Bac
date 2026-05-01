package com.chen404.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论视图对象。
 */
@Schema(description = "评论视图对象")
@Data
public class CommentVO {

    @Schema(description = "评论ID", example = "1001")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属文章ID，留言板评论为空", example = "101")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long articleId;

    @Schema(description = "父评论ID，顶级评论为0", example = "0")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    @Schema(description = "根评论ID，顶级评论为0", example = "0")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long rootId;

    @Schema(description = "评论内容", example = "写得很清楚，感谢分享")
    private String content;

    @Schema(description = "评论作者昵称", example = "Chen")
    private String authorName;

    @Schema(description = "评论作者邮箱", example = "chen@example.com")
    private String authorEmail;

    @Schema(description = "评论作者主页", example = "https://example.com")
    private String authorWebsite;

    @Schema(description = "评论作者头像地址", example = "https://cdn.example.com/avatar.png")
    private String authorAvatar;

    @Schema(description = "作者用户ID，游客为空", example = "10001")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long authorId;

    @Schema(description = "是否管理员评论：0-否 1-是", example = "0")
    private Integer isAdmin;

    @Schema(description = "点赞数", example = "12")
    private Integer likeCount;

    @Schema(description = "评论状态：0-待审核 1-已通过 2-已拒绝", example = "1")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "子评论列表")
    private List<CommentVO> children;

    @Schema(description = "被回复用户ID", example = "10002")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long replyToUserId;

    @Schema(description = "被回复作者昵称", example = "Alice")
    private String replyToAuthorName;

    @Schema(description = "游客评论自助删除Key，仅创建评论时返回一次", example = "guest-delete-token")
    private String guestDeleteKey;

    @Schema(description = "当前登录用户是否已点赞该评论", example = "false")
    private Boolean likedByMe;
}
