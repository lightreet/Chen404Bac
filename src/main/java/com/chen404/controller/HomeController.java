package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.Banner;
import com.chen404.domain.entity.Comment;
import com.chen404.service.ArticleService;
import com.chen404.service.BannerService;
import com.chen404.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页控制器
 */
@RestController
@RequestMapping("/home")
public class HomeController {

    @Autowired
    private ArticleService articleService;

    @Autowired
    private BannerService bannerService;

    @Autowired
    private CommentService commentService;

    /**
     * 获取首页聚合数据
     */
    @GetMapping("")
    public Result<Map<String, Object>> getHomeData() {
        Map<String, Object> data = new HashMap<>();

        // 轮播图
        List<Banner> banners = bannerService.getBannersByPosition(1);
        data.put("banners", banners);

        // 站点统计
        Map<String, Object> stats = articleService.getSiteStats();
        data.put("stats", stats);

        // 热门文章
        List<Article> hotArticles = articleService.getHotArticles(10);
        data.put("hotArticles", hotArticles);

        // 最新评论
        List<Comment> recentComments = commentService.getRecentComments(5);
        data.put("recentComments", recentComments);

        return Result.success(data);
    }

    /**
     * 获取站点统计
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getSiteStats() {
        Map<String, Object> stats = articleService.getSiteStats();
        return Result.success(stats);
    }
}
