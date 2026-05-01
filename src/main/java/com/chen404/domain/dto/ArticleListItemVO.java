package com.chen404.domain.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章列表项视图对象。
 */
@Schema(description = "文章列表项视图对象")
@Data
public class ArticleListItemVO {

    @Schema(description = "文章ID", example = "101")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "文章标题", example = "Spring Security 接入记录")
    private String title;

    @Schema(description = "文章摘要", example = "总结一次权限体系接入的关键步骤")
    private String summary;

    @Schema(description = "封面图片地址", example = "https://cdn.example.com/cover.webp")
    private String coverImage;

    @Schema(description = "作者ID", example = "10001")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long authorId;

    @Schema(description = "分类ID", example = "3")
    private Long categoryId;

    @Schema(description = "文章状态：0-草稿 1-已发布 2-回收站", example = "1")
    private Integer status;

    @Schema(description = "浏览量", example = "256")
    private Integer viewCount;

    @Schema(description = "评论数", example = "18")
    private Integer commentCount;

    @Schema(description = "点赞数", example = "42")
    private Integer likeCount;

    @Schema(description = "是否置顶：0-否 1-是", example = "0")
    private Integer isTop;

    @Schema(description = "是否推荐：0-否 1-是", example = "0")
    private Integer isRecommend;

    @Schema(description = "可见性：0-公开 1-登录可见 2-好友可见 3-私密", example = "0")
    private Integer visibility;

    @Schema(description = "评论策略：0-关闭 1-登录可评论 2-好友可评论 3-游客可评论", example = "1")
    private Integer commentPolicy;

    @Schema(description = "发布时间")
    private LocalDateTime publishTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "作者摘要")
    private ArticleAuthorVO author;

    @Schema(description = "分类摘要")
    private ArticleCategoryVO category;

    @Schema(description = "标签摘要列表")
    private List<ArticleTagVO> tags;

    @Schema(description = "当前用户是否可编辑", example = "true")
    private Boolean canEdit;

    @Schema(description = "当前用户是否可删除", example = "true")
    private Boolean canDelete;

    @Schema(description = "当前用户是否已点赞", example = "false")
    private Boolean liked;

    @Schema(description = "当前用户是否已收藏", example = "false")
    private Boolean favorited;
}
