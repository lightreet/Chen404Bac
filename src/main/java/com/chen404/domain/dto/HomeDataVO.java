package com.chen404.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 首页聚合数据视图对象。
 */
@Schema(description = "首页聚合数据视图对象")
@Data
public class HomeDataVO {

    @Schema(description = "轮播图列表")
    private List<BannerVO> banners;

    @Schema(description = "站点统计")
    private SiteStatsVO stats;

    @Schema(description = "热门文章列表")
    private List<ArticleListItemVO> hotArticles;

    @Schema(description = "最新评论列表")
    private List<RecentCommentVO> recentComments;
}
