package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.entity.Article;
import com.chen404.service.ArticleService;
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

    @Operation(summary = "创建文章", description = "发布新文章或保存草稿，需要登录")
    @PostMapping("/articles")
    public Result<Article> createArticle(@RequestBody Article article, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
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
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
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
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        try {
            articleService.deleteArticle(id);
            return Result.success("删除成功");
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
