package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

/**
 * 开发历程中的单条 GitHub 提交。
 */
@Schema(description = "开发历程提交记录")
@Data
public class DevelopmentCommitVO {

    @Schema(description = "完整提交哈希")
    private String sha;

    @Schema(description = "短提交哈希")
    private String shortSha;

    @Schema(description = "仓库名称")
    private String repository;

    @Schema(description = "仓库展示名称")
    private String repositoryLabel;

    @Schema(description = "提交标题")
    private String message;

    @Schema(description = "提交者姓名")
    private String authorName;

    @Schema(description = "GitHub 用户名")
    private String authorLogin;

    @Schema(description = "提交者头像地址")
    private String authorAvatarUrl;

    @Schema(description = "提交时间")
    private Instant committedAt;

    @Schema(description = "GitHub 提交详情地址")
    private String url;
}
