package com.chen404.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.domain.PageResult;
import com.chen404.domain.Result;
import com.chen404.domain.entity.Article;
import com.chen404.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文章控制器
 */
@Tag(name = "文章管理", description = "文章发布、编辑、查询等相关接口")
@RestController
@RequestMapping("/articles")
public class ArticleController {

    @Autowired
    private ArticleService articleService;

    /**
     * 获取文章列表
     */
    @Operation(summary = "获取文章列表", description = "支持分页、分类筛选、标签筛选、关键词搜索")
    @Parameter(name = "page", description = "页码，默认1")
    @Parameter(name = "size", description = "每页数量，默认10")
    @Parameter(name = "status", description = "文章状态：0-草稿 1-已发布 2-回收站")
    @Parameter(name = "categoryId", description = "分类ID")
    @Parameter(name = "tagId", description = "标签ID")
    @Parameter(name = "keyword", description = "搜索关键词")
    @GetMapping("")
    public Result<PageResult<Article>> getArticles(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String keyword) {

        Page<Article> articlePage = articleService.getArticlePage(page, size, status, categoryId, tagId, keyword);
        return Result.success(PageResult.of(articlePage));
    }

    /**
     * 获取文章详情
     */
    @Operation(summary = "获取文章详情", description = "获取单篇文章的详细信息")
    @Parameter(name = "id", description = "文章ID", required = true)
    @Parameter(name = "incrementView", description = "是否增加浏览量，默认true")
    @GetMapping("/{id}")
    public Result<Article> getArticleById(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") Boolean incrementView) {

        Article article = articleService.getArticleById(id, incrementView);
        if (article == null) {
            return Result.error(404, "文章不存在");
        }
        return Result.success(article);
    }

    /**
     * 创建文章（需要登录）
     */
    @Operation(summary = "创建文章", description = "发布新文章或保存草稿，需要登录")
    @PostMapping("/admin/articles")
    public Result<Article> createArticle(@RequestBody Article article, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }

        // 设置作者
        article.setAuthorId(userId);

        try {
            Article created = articleService.createArticle(article);
            return Result.success("创建成功", created);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 更新文章（需要登录）
     */
    @Operation(summary = "更新文章", description = "更新已有文章，需要登录且是文章作者或管理员")
    @Parameter(name = "id", description = "文章ID", required = true)
    @PutMapping("/admin/articles/{id}")
    public Result<Article> updateArticle(
            @PathVariable Long id,
            @RequestBody Article article,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }

        // TODO: 检查权限（是否是作者或管理员）

        try {
            Article updated = articleService.updateArticle(id, article);
            return Result.success("更新成功", updated);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 删除文章（需要登录）
     */
    @Operation(summary = "删除文章", description = "删除文章（逻辑删除），需要登录且是文章作者或管理员")
    @Parameter(name = "id", description = "文章ID", required = true)
    @DeleteMapping("/admin/articles/{id}")
    public Result<Void> deleteArticle(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }

        // TODO: 检查权限

        try {
            articleService.deleteArticle(id);
            return Result.success("删除成功");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 点赞文章
     */
    @Operation(summary = "点赞文章", description = "为文章点赞")
    @Parameter(name = "id", description = "文章ID", required = true)
    @PostMapping("/{id}/like")
    public Result<Map<String, Integer>> likeArticle(@PathVariable Long id) {
        Integer likes = articleService.likeArticle(id);
        return Result.success(Map.of("likes", likes));
    }

    /**
     * 获取热门文章
     */
    @Operation(summary = "获取热门文章", description = "根据浏览量排序获取热门文章")
    @Parameter(name = "limit", description = "返回数量，默认10")
    @GetMapping("/hot")
    public Result<List<Article>> getHotArticles(
            @RequestParam(defaultValue = "10") Integer limit) {
        List<Article> articles = articleService.getHotArticles(limit);
        return Result.success(articles);
    }

    /**
     * 获取推荐文章
     */
    @Operation(summary = "获取推荐文章", description = "获取管理员推荐的文章列表")
    @Parameter(name = "limit", description = "返回数量，默认6")
    @GetMapping("/recommend")
    public Result<List<Article>> getRecommendArticles(
            @RequestParam(defaultValue = "6") Integer limit) {
        List<Article> articles = articleService.getRecommendArticles(limit);
        return Result.success(articles);
    }
}
