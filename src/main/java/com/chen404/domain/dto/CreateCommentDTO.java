package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "创建评论请求参数")
public class CreateCommentDTO {

    @Schema(description = "文章ID（留言板评论可不传）")
    private Long articleId;

    @Schema(description = "父评论ID（顶级评论传 0 或不传）", example = "0")
    private Long parentId;

    @Schema(description = "评论内容")
    private String content;

    @Schema(description = "评论者名称（游客必填，登录用户可不传）")
    private String authorName;

    @Schema(description = "评论者邮箱")
    private String authorEmail;

    @Schema(description = "评论者网站")
    private String authorWebsite;
}
