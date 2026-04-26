package com.chen404.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.domain.PageResult;
import com.chen404.domain.Result;
import com.chen404.domain.dto.ArchiveYearVO;
import com.chen404.domain.dto.ArticleLikeResult;
import com.chen404.domain.entity.Article;
import com.chen404.exception.ForbiddenException;
import com.chen404.exception.UnauthorizedException;
import com.chen404.service.ArticleService;
import com.chen404.util.RequestAttrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 文章控制器：列表/详情/热门/推荐等公开；我的文章/创建/更新/删除需登录。
 * 归档接口也归属于文章资源，因此统一收口到当前控制器。
 */
@Tag(name = "文章", description = "文章列表、详情、点赞、热门、推荐、归档及个人文章管理")
@RestController
public class ArticleController {

    @Autowired
    private ArticleService articleService;

    @Operation(summary = "我的文章列表", description = "分页获取当前登录用户的文章，需登录")
    @GetMapping("/articles/mine")
    public Result<PageResult<Article>> getMyArticles(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        Long userId = RequestAttrUtil.requireUserId(request);
        Page<Article> articlePage = articleService.getMyArticlePage(userId, page, size, status, keyword);
        return Result.success(PageResult.of(articlePage));
    }

    @Operation(summary = "我的点赞文章", description = "分页获取当前用户点赞过的、仍可见的文章，需登录")
    @GetMapping("/articles/mine/liked")
    public Result<PageResult<Article>> getMyLikedArticles(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        Long userId = RequestAttrUtil.requireUserId(request);
        Page<Article> articlePage = articleService.getMyLikedArticlePage(userId, page, size);
        return Result.success(PageResult.of(articlePage));
    }

    @Operation(summary = "我的收藏文章", description = "分页获取当前用户收藏的文章，需登录")
    @GetMapping("/articles/mine/favorites")
    public Result<PageResult<Article>> getMyFavoriteArticles(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        Long userId = RequestAttrUtil.requireUserId(request);
        Page<Article> articlePage = articleService.getMyFavoriteArticlePage(userId, page, size);
        return Result.success(PageResult.of(articlePage));
    }

    @Operation(summary = "获取文章列表", description = "支持分页、分类筛选、标签筛选；关键词仅按文章标题模糊匹配")
    @Parameter(name = "page", description = "页码，默认1")
    @Parameter(name = "size", description = "每页数量，默认10")
    @Parameter(name = "status", description = "文章状态：0-草稿 1-已发布 2-回收站")
    @Parameter(name = "categoryId", description = "分类ID")
    @Parameter(name = "tagId", description = "标签ID")
    @Parameter(name = "authorId", description = "作者ID")
    @Parameter(name = "keyword", description = "搜索关键词（仅匹配文章标题）")
    @GetMapping("/articles")
    public Result<PageResult<Article>> getArticles(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        Page<Article> articlePage = articleService.getArticlePage(
                page,
                size,
                status,
                categoryId,
                tagId,
                authorId,
                keyword,
                RequestAttrUtil.getUserId(request)
        );
        return Result.success(PageResult.of(articlePage));
    }

    @Operation(summary = "获取文章详情", description = "获取单篇文章的详细信息")
    @Parameter(name = "id", description = "文章ID", required = true)
    @Parameter(name = "incrementView", description = "是否增加浏览量，默认true")
    @GetMapping("/articles/{id}")
    public Result<Article> getArticleById(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") Boolean incrementView,
            HttpServletRequest request) {
        Article article = articleService.getArticleById(id, incrementView, RequestAttrUtil.getUserId(request));
        if (article == null) {
            return Result.error(404, "文章不存在");
        }
        return Result.success(article);
    }

    @Operation(summary = "上一篇/下一篇", description = "按发布时间获取相邻文章")
    @Parameter(name = "id", description = "当前文章ID", required = true)
    @GetMapping("/articles/{id}/neighbors")
    public Result<Map<String, Article>> getArticleNeighbors(@PathVariable Long id, HttpServletRequest request) {
        Map<String, Article> neighbors = articleService.getNeighbors(id, RequestAttrUtil.getUserId(request));
        return Result.success(neighbors);
    }

    @Operation(summary = "归档时间线", description = "仅包含已发布且公开可见、有发布时间的文章，按发布时间倒序分组")
    @GetMapping("/archives")
    public Result<List<ArchiveYearVO>> listArchives(HttpServletRequest request) {
        return Result.success(articleService.listArchives(RequestAttrUtil.getUserId(request)));
    }

    @Operation(summary = "点赞文章", description = "为文章点赞")
    @Parameter(name = "id", description = "文章ID", required = true)
    @PostMapping("/articles/{id}/like")
    public Result<ArticleLikeResult> likeArticle(@PathVariable Long id, HttpServletRequest request) {
        ArticleLikeResult result = articleService.likeArticle(id, RequestAttrUtil.getUserId(request), getClientIp(request));
        return Result.success(result);
    }

    @Operation(summary = "切换收藏", description = "登录用户收藏/取消收藏文章")
    @PostMapping("/articles/{id}/favorite")
    public Result<Map<String, Boolean>> toggleFavorite(@PathVariable Long id, HttpServletRequest request) {
        Long userId = RequestAttrUtil.requireUserId(request);
        boolean favorited = articleService.toggleFavorite(id, userId);
        return Result.success(Map.of("favorited", favorited));
    }

    @Operation(summary = "获取热门文章", description = "根据浏览量排序获取热门文章")
    @Parameter(name = "limit", description = "返回数量，默认10")
    @GetMapping("/articles/hot")
    public Result<List<Article>> getHotArticles(
            @RequestParam(defaultValue = "10") Integer limit,
            HttpServletRequest request) {
        List<Article> articles = articleService.getHotArticles(limit, RequestAttrUtil.getUserId(request));
        return Result.success(articles);
    }

    @Operation(summary = "获取推荐文章", description = "获取管理员推荐的文章列表")
    @Parameter(name = "limit", description = "返回数量，默认6")
    @GetMapping("/articles/recommend")
    public Result<List<Article>> getRecommendArticles(
            @RequestParam(defaultValue = "6") Integer limit,
            HttpServletRequest request) {
        List<Article> articles = articleService.getRecommendArticles(limit, RequestAttrUtil.getUserId(request));
        return Result.success(articles);
    }

    @Operation(summary = "创建文章", description = "发布新文章或保存草稿，需登录")
    @PostMapping("/articles")
    public Result<Article> createArticle(@RequestBody Article article, HttpServletRequest request) {
        Long userId = RequestAttrUtil.requireUserId(request);
        article.setAuthorId(userId);
        try {
            Article created = articleService.createArticle(article);
            return Result.success("创建成功", created);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @Operation(summary = "更新文章", description = "更新已有文章，需登录")
    @Parameter(name = "id", description = "文章ID", required = true)
    @PutMapping("/articles/{id}")
    public Result<Article> updateArticle(
            @PathVariable Long id,
            @RequestBody Article article,
            HttpServletRequest request) {
        Long userId = RequestAttrUtil.requireUserId(request);
        try {
            Article updated = articleService.updateArticle(id, article, userId);
            return Result.success("更新成功", updated);
        } catch (ForbiddenException | UnauthorizedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @Operation(summary = "删除文章", description = "逻辑删除，需登录")
    @Parameter(name = "id", description = "文章ID", required = true)
    @DeleteMapping("/articles/{id}")
    public Result<Void> deleteArticle(@PathVariable Long id, HttpServletRequest request) {
        Long userId = RequestAttrUtil.requireUserId(request);
        try {
            articleService.deleteArticle(id, userId);
            return Result.success("删除成功");
        } catch (ForbiddenException | UnauthorizedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            int commaIndex = ip.indexOf(',');
            return commaIndex >= 0 ? ip.substring(0, commaIndex).trim() : ip.trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }

        return request.getRemoteAddr();
    }
}
