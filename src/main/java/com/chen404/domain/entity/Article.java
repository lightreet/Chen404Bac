package com.chen404.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章实体
 */
@Data
@TableName("article")
public class Article implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文章ID（序列化为字符串，避免前端 JS 大数精度丢失）
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 内容（Markdown）
     */
    private String content;

    /**
     * HTML内容（缓存）
     */
    private String contentHtml;

    /**
     * 封面图URL
     */
    private String coverImage;

    /**
     * 作者ID
     */
    private Long authorId;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 状态：0-草稿 1-已发布 2-回收站
     */
    private Integer status;

    /**
     * 浏览量
     */
    private Integer viewCount;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 评论数
     */
    private Integer commentCount;

    /**
     * 是否置顶：0-否 1-是
     */
    private Integer isTop;

    /**
     * 是否推荐：0-否 1-是
     */
    private Integer isRecommend;

    /**
     * 是否原创：0-转载 1-原创
     */
    private Integer isOriginal;

    /**
     * 原文链接（转载时）
     */
    private String originalUrl;

    /**
     * 访问密码
     */
    @JsonIgnore
    private String password;

    /**
     * 可见性：0-公开 1-登录可见 2-好友可见 3-私密
     */
    private Integer visibility;

    /**
     * 评论策略：0-关闭 1-登录可评论 2-好友可评论 3-游客可评论
     */
    private Integer commentPolicy;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;

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

    // ========== 非数据库字段 ==========

    /**
     * 作者信息
     */
    @TableField(exist = false)
    private User author;

    /**
     * 分类信息
     */
    @TableField(exist = false)
    private Category category;

    /**
     * 标签列表
     */
    @TableField(exist = false)
    private List<Tag> tags;

    /**
     * 标签ID列表（用于接收前端参数）
     */
    @TableField(exist = false)
    private List<Long> tagIds;

    /**
     * 新标签名称列表（用户自定义输入，后端会 findOrCreate 后并入 tagIds）
     */
    @TableField(exist = false)
    private List<String> tagNames;

    /**
     * 当前请求用户是否可编辑
     */
    @TableField(exist = false)
    private Boolean canEdit;

    /**
     * 当前请求用户是否可删除
     */
    @TableField(exist = false)
    private Boolean canDelete;

    /**
     * 当前请求用户是否可评论
     */
    @TableField(exist = false)
    private Boolean canComment;

    public interface Status {
        int DRAFT = 0;
        int PUBLISHED = 1;
        int RECYCLED = 2;
    }

    public interface Visibility {
        int PUBLIC = 0;
        int LOGIN = 1;
        int FRIEND = 2;
        int PRIVATE = 3;
    }

    public interface CommentPolicy {
        int CLOSED = 0;
        int REGISTERED = 1;
        int FRIEND = 2;
        int PUBLIC = 3;
    }
}
