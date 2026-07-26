package com.chen404.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端评论视图，包含审核所需的作者与上下文信息。
 */
@Data
@Schema(description = "管理端评论视图")
public class AdminCommentVO {

    @Schema(description = "评论 ID", example = "1001")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属文章 ID，留言板留言为空", example = "101")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long articleId;

    @Schema(description = "文章标题，留言板留言为空")
    private String articleTitle;

    @Schema(description = "评论来源：ARTICLE-文章评论 GUESTBOOK-留言板")
    private String scene;

    @Schema(description = "父评论 ID，顶级评论为 0", example = "0")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    @Schema(description = "根评论 ID", example = "1001")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long rootId;

    @Schema(description = "回复对象昵称")
    private String replyToAuthorName;

    @Schema(description = "评论内容")
    private String content;

    @Schema(description = "作者昵称")
    private String authorName;

    @Schema(description = "作者邮箱")
    private String authorEmail;

    @Schema(description = "作者网站")
    private String authorWebsite;

    @Schema(description = "作者头像")
    private String authorAvatar;

    @Schema(description = "作者用户 ID，游客为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long authorId;

    @Schema(description = "提交 IP")
    private String ip;

    @Schema(description = "IP 归属地")
    private String location;

    @Schema(description = "客户端 User-Agent")
    private String userAgent;

    @Schema(description = "评论状态：0-待审核 1-已通过 2-已拒绝")
    private Integer status;

    @Schema(description = "是否管理员评论：0-否 1-是")
    private Integer isAdmin;

    @Schema(description = "点赞数")
    private Integer likeCount;

    @Schema(description = "提交时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
