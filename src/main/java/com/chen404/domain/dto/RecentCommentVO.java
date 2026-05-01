package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页最新评论视图对象。
 */
@Schema(description = "首页最新评论视图对象")
@Data
public class RecentCommentVO {

    @Schema(description = "评论ID", example = "1001")
    private Long id;

    @Schema(description = "所属文章ID，留言板评论为空", example = "101")
    private Long articleId;

    @Schema(description = "文章标题，留言板评论为空", example = "Spring Security 实战")
    private String articleTitle;

    @Schema(description = "评论内容", example = "写得很清楚，感谢分享")
    private String content;

    @Schema(description = "评论作者昵称", example = "Chen")
    private String authorName;

    @Schema(description = "评论作者头像地址", example = "https://cdn.example.com/avatar.png")
    private String authorAvatar;

    @Schema(description = "评论创建时间")
    private LocalDateTime createTime;
}
