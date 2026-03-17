package com.chen404.controller;

import com.chen404.domain.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.domain.PageResult;
import com.chen404.domain.entity.Article;
import com.chen404.service.ArticleService;
import com.chen404.util.RequestAttrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端文章接口（路径为 /admin/articles，供前端统一调用 /api/admin/articles）
 */
@Tag(name = "管理端-文章", description = "创建、更新、删除文章（需登录）")
@RestController
@RequestMapping("/admin")
public class AdminArticleController {

    @Autowired
    private ArticleService articleService;

    @Operation(summary = "我的文章列表", description = "分页获取当前登录用户的文章（草稿/发布），用于个人中心管理")
    @Parameter(name = "page", description = "页码，默认1")
    @Parameter(name = "size", description = "每页数量，默认10")
    @Parameter(name = "status", description = "文章状态：0-草稿 1-已发布 2-回收站")
    @Parameter(name = "keyword", description = "搜索关键词")
    @GetMapping("/articles")
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

    @Operation(summary = "创建文章", description = "发布新文章或保存草稿，需要登录")
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

    @Operation(summary = "更新文章", description = "更新已有文章，需要登录")
    @Parameter(name = "id", description = "文章ID", required = true)
    @PutMapping("/articles/{id}")
    public Result<Article> updateArticle(
            @PathVariable Long id,
            @RequestBody Article article,
            HttpServletRequest request) {
        RequestAttrUtil.requireUserId(request);
        try {
            Article updated = articleService.updateArticle(id, article);
            return Result.success("更新成功", updated);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @Operation(summary = "删除文章", description = "删除文章（逻辑删除），需要登录")
    @Parameter(name = "id", description = "文章ID", required = true)
    @DeleteMapping("/articles/{id}")
    public Result<Void> deleteArticle(@PathVariable Long id, HttpServletRequest request) {
        RequestAttrUtil.requireUserId(request);
        try {
            articleService.deleteArticle(id);
            return Result.success("删除成功");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
