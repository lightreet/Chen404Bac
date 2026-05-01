package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建文章命令对象，避免控制器直接接收持久化实体。
 */
@Schema(description = "创建文章命令对象")
@Data
public class CreateArticleCommand {

    @Schema(description = "文章标题", example = "Spring Security 接入记录")
    @NotBlank(message = "文章标题不能为空")
    @Size(max = 100, message = "文章标题长度不能超过100个字符")
    private String title;

    @Schema(description = "文章摘要", example = "总结一次权限体系接入的关键步骤")
    @Size(max = 500, message = "文章摘要长度不能超过500个字符")
    private String summary;

    @Schema(description = "Markdown 正文内容", example = "# 标题\\n正文内容")
    @NotBlank(message = "文章内容不能为空")
    private String content;

    @Schema(description = "封面图片地址", example = "https://cdn.example.com/cover.webp")
    private String coverImage;

    @Schema(description = "分类ID", example = "3")
    @NotNull(message = "文章分类不能为空")
    private Long categoryId;

    @Schema(description = "文章状态：0-草稿 1-已发布 2-回收站", example = "1")
    @NotNull(message = "文章状态不能为空")
    private Integer status;

    @Schema(description = "是否置顶：0-否 1-是", example = "0")
    private Integer isTop;

    @Schema(description = "是否推荐：0-否 1-是", example = "0")
    private Integer isRecommend;

    @Schema(description = "是否原创：0-转载 1-原创", example = "1")
    private Integer isOriginal;

    @Schema(description = "转载原文链接", example = "https://example.com/original-post")
    private String originalUrl;

    @Schema(description = "访问密码，仅私密文章需要", example = "123456")
    private String password;

    @Schema(description = "可见性：0-公开 1-登录可见 2-好友可见 3-私密", example = "0")
    private Integer visibility;

    @Schema(description = "评论策略：0-关闭 1-登录可评论 2-好友可评论 3-游客可评论", example = "1")
    private Integer commentPolicy;

    @Schema(description = "已存在标签ID列表")
    private List<Long> tagIds;

    @Schema(description = "新建标签名称列表")
    private List<String> tagNames;
}
