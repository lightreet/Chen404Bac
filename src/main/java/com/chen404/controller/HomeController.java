package com.chen404.controller;

import com.chen404.converter.ArticleViewConverter;
import com.chen404.converter.HomeViewConverter;
import com.chen404.domain.Result;
import com.chen404.domain.dto.ArticleListItemVO;
import com.chen404.domain.dto.BannerVO;
import com.chen404.domain.dto.HomeDataVO;
import com.chen404.domain.dto.RecentCommentVO;
import com.chen404.domain.dto.SiteStatsVO;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.Banner;
import com.chen404.domain.entity.Comment;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.ArticleService;
import com.chen404.service.BannerService;
import com.chen404.service.CommentService;
import com.chen404.util.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 首页控制器
 */
@Tag(name = "首页", description = "首页聚合数据与站点统计接口")
@RestController
@RequestMapping("/home")
public class HomeController {

    private final ArticleService articleService;
    private final BannerService bannerService;
    private final CommentService commentService;
    private final ArticleViewConverter articleViewConverter;
    private final HomeViewConverter homeViewConverter;

    public HomeController(
            ArticleService articleService,
            BannerService bannerService,
            CommentService commentService,
            ArticleViewConverter articleViewConverter,
            HomeViewConverter homeViewConverter) {
        this.articleService = articleService;
        this.bannerService = bannerService;
        this.commentService = commentService;
        this.articleViewConverter = articleViewConverter;
        this.homeViewConverter = homeViewConverter;
    }

    /**
     * 获取首页聚合数据
     */
    @Operation(summary = "获取首页聚合数据", description = "返回首页轮播图、站点统计、热门文章与最新评论")
    @GetMapping("")
    public Result<HomeDataVO> getHomeData(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long requesterId = CurrentUserUtil.getUserId(currentUser);

        List<Banner> banners = bannerService.getBannersByPosition(1);
        Map<String, Object> stats = articleService.getSiteStats();
        List<Article> hotArticles = articleService.getHotArticles(10, requesterId);
        List<Comment> recentComments = commentService.getRecentComments(5);

        HomeDataVO data = new HomeDataVO();
        data.setBanners(homeViewConverter.toBannerVOList(banners));
        data.setStats(toSiteStatsVO(stats));
        data.setHotArticles(articleViewConverter.toListItemVOList(hotArticles));
        data.setRecentComments(toRecentCommentVOList(recentComments));

        return Result.success(data);
    }

    /**
     * 获取站点统计
     */
    @Operation(summary = "获取站点统计", description = "返回文章数、分类数、标签数、评论数与总浏览量")
    @GetMapping("/stats")
    public Result<SiteStatsVO> getSiteStats() {
        Map<String, Object> stats = articleService.getSiteStats();
        return Result.success(toSiteStatsVO(stats));
    }

    private SiteStatsVO toSiteStatsVO(Map<String, Object> stats) {
        Map<String, Object> safeStats = stats == null ? Collections.emptyMap() : stats;
        SiteStatsVO vo = new SiteStatsVO();
        vo.setArticleCount(asLong(safeStats.get("articleCount")));
        vo.setCategoryCount(asLong(safeStats.get("categoryCount")));
        vo.setTagCount(asLong(safeStats.get("tagCount")));
        vo.setCommentCount(asLong(safeStats.get("commentCount")));
        vo.setViewCount(asLong(safeStats.get("viewCount")));
        return vo;
    }

    private List<RecentCommentVO> toRecentCommentVOList(List<Comment> recentComments) {
        List<Comment> safeComments = recentComments == null ? Collections.emptyList() : recentComments;
        List<RecentCommentVO> voList = homeViewConverter.toRecentCommentVOList(safeComments);
        Set<Long> articleIds = safeComments.stream()
                .map(Comment::getArticleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> articleTitleById = articleIds.isEmpty()
                ? Collections.emptyMap()
                : articleService.listByIds(articleIds).stream()
                .collect(Collectors.toMap(Article::getId, Article::getTitle, (left, right) -> left, HashMap::new));
        for (RecentCommentVO vo : voList) {
            if (vo.getArticleId() != null) {
                vo.setArticleTitle(articleTitleById.get(vo.getArticleId()));
            }
        }
        return voList;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }
}
