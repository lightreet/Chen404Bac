package com.chen404.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.converter.ArticleCommandConverter;
import com.chen404.converter.ArticleViewConverter;
import com.chen404.domain.PageResult;
import com.chen404.domain.ApiErrorCode;
import com.chen404.domain.Result;
import com.chen404.domain.dto.ArchiveYearVO;
import com.chen404.domain.dto.ArticleDetailVO;
import com.chen404.domain.dto.ArticleLikeResult;
import com.chen404.domain.dto.ArticleListItemVO;
import com.chen404.domain.dto.ArticleNeighborsVO;
import com.chen404.domain.dto.CreateArticleCommand;
import com.chen404.domain.dto.FavoriteToggleResultDTO;
import com.chen404.domain.dto.UpdateArticleCommand;
import com.chen404.domain.entity.Article;
import com.chen404.exception.ForbiddenException;
import com.chen404.exception.UnauthorizedException;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.ArticleService;
import com.chen404.util.CurrentUserUtil;
import com.chen404.util.WebRequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * 文章控制器：列表/详情/热门/推荐等公开接口；我的文章、创建、更新、删除需要登录。
 */
@Tag(name = "文章", description = "文章列表、详情、点赞、热门、推荐、归档及个人文章管理")
@RestController
public class ArticleController {

    private final ArticleService articleService;
    private final ArticleCommandConverter articleCommandConverter;
    private final ArticleViewConverter articleViewConverter;

    public ArticleController(
            ArticleService articleService,
            ArticleCommandConverter articleCommandConverter,
            ArticleViewConverter articleViewConverter) {
        this.articleService = articleService;
        this.articleCommandConverter = articleCommandConverter;
        this.articleViewConverter = articleViewConverter;
    }

    @Operation(summary = "我的文章列表", description = "分页获取当前登录用户的文章，需要登录")
    @GetMapping("/articles/mine")
    public Result<PageResult<ArticleListItemVO>> getMyArticles(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        Page<Article> articlePage = articleService.getMyArticlePage(userId, page, size, status, keyword);
        return Result.success(toPageResult(articlePage, articleViewConverter.toListItemVOList(articlePage.getRecords())));
    }

    @Operation(summary = "我的点赞文章", description = "分页获取当前用户点赞过且仍可见的文章，需要登录")
    @GetMapping("/articles/mine/liked")
    public Result<PageResult<ArticleListItemVO>> getMyLikedArticles(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        Page<Article> articlePage = articleService.getMyLikedArticlePage(userId, page, size);
        return Result.success(toPageResult(articlePage, articleViewConverter.toListItemVOList(articlePage.getRecords())));
    }

    @Operation(summary = "我的收藏文章", description = "分页获取当前用户收藏的文章，需要登录")
    @GetMapping("/articles/mine/favorites")
    public Result<PageResult<ArticleListItemVO>> getMyFavoriteArticles(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        Page<Article> articlePage = articleService.getMyFavoriteArticlePage(userId, page, size);
        return Result.success(toPageResult(articlePage, articleViewConverter.toListItemVOList(articlePage.getRecords())));
    }

    @Operation(summary = "获取文章列表", description = "支持分页、分类筛选、标签筛选；关键字仅按文章标题模糊匹配")
    @Parameter(name = "page", description = "页码，默认 1")
    @Parameter(name = "size", description = "每页数量，默认 10")
    @Parameter(name = "status", description = "文章状态：0-草稿 1-已发布 2-回收站")
    @Parameter(name = "categoryId", description = "分类 ID")
    @Parameter(name = "tagId", description = "标签 ID")
    @Parameter(name = "authorId", description = "作者 ID")
    @Parameter(name = "keyword", description = "搜索关键字，仅匹配文章标题")
    @GetMapping("/articles")
    public Result<PageResult<ArticleListItemVO>> getArticles(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Page<Article> articlePage = articleService.getArticlePage(
                page,
                size,
                status,
                categoryId,
                tagId,
                authorId,
                keyword,
                CurrentUserUtil.getUserId(currentUser)
        );
        return Result.success(toPageResult(articlePage, articleViewConverter.toListItemVOList(articlePage.getRecords())));
    }

    @Operation(summary = "获取文章详情", description = "获取单篇文章的详细信息")
    @Parameter(name = "id", description = "文章 ID", required = true)
    @Parameter(name = "incrementView", description = "是否增加浏览量，默认 true")
    @GetMapping("/articles/{id}")
    public Result<ArticleDetailVO> getArticleById(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") Boolean incrementView,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Article article = articleService.getArticleById(id, incrementView, CurrentUserUtil.getUserId(currentUser));
        if (article == null) {
            return Result.error(ApiErrorCode.NOT_FOUND, "文章不存在");
        }
        return Result.success(articleViewConverter.toDetailVO(article));
    }

    @Operation(summary = "上一篇 / 下一篇", description = "按发布时间获取相邻文章")
    @Parameter(name = "id", description = "当前文章 ID", required = true)
    @GetMapping("/articles/{id}/neighbors")
    public Result<ArticleNeighborsVO> getArticleNeighbors(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Map<String, Article> neighbors = articleService.getNeighbors(id, CurrentUserUtil.getUserId(currentUser));
        ArticleNeighborsVO result = new ArticleNeighborsVO();
        result.setPrev(articleViewConverter.toNeighborVO(neighbors.get("prev")));
        result.setNext(articleViewConverter.toNeighborVO(neighbors.get("next")));
        return Result.success(result);
    }

    @Operation(summary = "归档时间线", description = "仅包含已发布且公开可见、有发布时间的文章，按发布时间倒序分组")
    @GetMapping("/archives")
    public Result<List<ArchiveYearVO>> listArchives(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return Result.success(articleService.listArchives(CurrentUserUtil.getUserId(currentUser)));
    }

    @Operation(summary = "点赞文章", description = "为文章点赞")
    @Parameter(name = "id", description = "文章 ID", required = true)
    @PostMapping("/articles/{id}/like")
    public Result<ArticleLikeResult> likeArticle(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest request) {
        ArticleLikeResult result = articleService.likeArticle(
                id,
                CurrentUserUtil.getUserId(currentUser),
                WebRequestUtil.getClientIp(request)
        );
        return Result.success(result);
    }

    @Operation(summary = "切换收藏", description = "登录用户收藏/取消收藏文章")
    @PostMapping("/articles/{id}/favorite")
    public Result<FavoriteToggleResultDTO> toggleFavorite(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        boolean favorited = articleService.toggleFavorite(id, userId);
        return Result.success(new FavoriteToggleResultDTO(favorited));
    }

    @Operation(summary = "获取热门文章", description = "根据浏览量排序获取热门文章")
    @Parameter(name = "limit", description = "返回数量，默认 10")
    @GetMapping("/articles/hot")
    public Result<List<ArticleListItemVO>> getHotArticles(
            @RequestParam(defaultValue = "10") Integer limit,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        List<Article> articles = articleService.getHotArticles(limit, CurrentUserUtil.getUserId(currentUser));
        return Result.success(articleViewConverter.toListItemVOList(articles));
    }

    @Operation(summary = "获取推荐文章", description = "获取管理员推荐的文章列表")
    @Parameter(name = "limit", description = "返回数量，默认 6")
    @GetMapping("/articles/recommend")
    public Result<List<ArticleListItemVO>> getRecommendArticles(
            @RequestParam(defaultValue = "6") Integer limit,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        List<Article> articles = articleService.getRecommendArticles(limit, CurrentUserUtil.getUserId(currentUser));
        return Result.success(articleViewConverter.toListItemVOList(articles));
    }

    @Operation(summary = "创建文章", description = "发布新文章或保存草稿，需要登录")
    @PostMapping("/articles")
    public Result<ArticleDetailVO> createArticle(
            @Valid @RequestBody CreateArticleCommand command,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        Article article = articleCommandConverter.toEntity(command);
        article.setAuthorId(userId);
        Article created = articleService.createArticle(article);
        Article freshArticle = articleService.getArticleById(created.getId(), false, userId);
        return Result.success("创建成功", articleViewConverter.toDetailVO(freshArticle));
    }

    @Operation(summary = "更新文章", description = "更新已有文章，需要登录")
    @Parameter(name = "id", description = "文章 ID", required = true)
    @PutMapping("/articles/{id}")
    public Result<ArticleDetailVO> updateArticle(
            @PathVariable Long id,
            @Valid @RequestBody UpdateArticleCommand command,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        Article article = articleCommandConverter.toEntity(command);
        try {
            Article updated = articleService.updateArticle(id, article, userId);
            return Result.success("更新成功", articleViewConverter.toDetailVO(updated));
        } catch (ForbiddenException | UnauthorizedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(ApiErrorCode.BAD_REQUEST, e.getMessage());
        }
    }

    @Operation(summary = "删除文章", description = "逻辑删除，需要登录")
    @Parameter(name = "id", description = "文章 ID", required = true)
    @DeleteMapping("/articles/{id}")
    public Result<Void> deleteArticle(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        try {
            articleService.deleteArticle(id, userId);
            return Result.success("删除成功");
        } catch (ForbiddenException | UnauthorizedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(ApiErrorCode.BAD_REQUEST, e.getMessage());
        }
    }

    private <T> PageResult<T> toPageResult(Page<?> page, List<T> records) {
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
