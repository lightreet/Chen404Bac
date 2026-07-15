package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 开发历程中的仓库摘要。
 */
@Schema(description = "开发历程仓库摘要")
@Data
public class DevelopmentRepositoryVO {

    @Schema(description = "仓库名称")
    private String name;

    @Schema(description = "仓库展示名称")
    private String label;

    @Schema(description = "本次同步到的提交数量")
    private int commitCount;

    @Schema(description = "数据来源，api 或 atom")
    private String source;

    @Schema(description = "GitHub 仓库地址")
    private String url;
}
