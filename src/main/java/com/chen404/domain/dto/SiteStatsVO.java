package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 站点统计视图对象。
 */
@Schema(description = "站点统计视图对象")
@Data
public class SiteStatsVO {

    @Schema(description = "文章总数", example = "128")
    private Long articleCount;

    @Schema(description = "分类总数", example = "12")
    private Long categoryCount;

    @Schema(description = "标签总数", example = "36")
    private Long tagCount;

    @Schema(description = "评论总数", example = "520")
    private Long commentCount;

    @Schema(description = "总浏览量", example = "10240")
    private Long viewCount;
}
