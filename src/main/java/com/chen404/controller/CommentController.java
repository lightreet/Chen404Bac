package com.chen404.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.annotation.RequireAdmin;
import com.chen404.converter.CommentConverter;
import com.chen404.converter.HomeViewConverter;
import com.chen404.domain.PageResult;
import com.chen404.domain.Result;
import com.chen404.domain.dto.CommentLikeResult;
import com.chen404.domain.dto.CommentVO;
import com.chen404.domain.dto.CreateCommentDTO;
import com.chen404.domain.dto.RecentCommentVO;
import com.chen404.domain.dto.ReviewCommentDTO;
import com.chen404.domain.entity.Article;
import com.chen404.domain.entity.Comment;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.ArticleService;
import com.chen404.service.CommentService;
import com.chen404.util.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "评论", description = "评论列表、发表、删除、点赞与管理员审核")
@RestController
public class CommentController {

    private final CommentService commentService;
    private final CommentConverter commentConverter;
    private final HomeViewConverter homeViewConverter;
    private final ArticleService articleService;

    public CommentController(
            CommentService commentService,
            CommentConverter commentConverter,
            HomeViewConverter homeViewConverter,
            ArticleService articleService) {
        this.commentService = commentService;
        this.commentConverter = commentConverter;
        this.homeViewConverter = homeViewConverter;
        this.articleService = articleService;
    }

    @Operation(summary = "获取评论列表", description = "按 articleId 分页查询已审核评论树")
    @GetMapping("/comments")
    public Result<PageResult<CommentVO>> getComments(
            @RequestParam(required = false) Long articleId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Page<Comment> result = commentService.getCommentsByArticleId(
                articleId, page, size, CurrentUserUtil.getUserId(currentUser));
        return Result.success(new PageResult<>(
                commentConverter.toVOList(result.getRecords()),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        ));
    }

    @Operation(summary = "获取留言板评论")
    @GetMapping("/comments/guestbook")
    public Result<PageResult<CommentVO>> getGuestbookComments(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<Comment> result = commentService.getGuestbookComments(page, size);
        return Result.success(new PageResult<>(
                commentConverter.toVOList(result.getRecords()),
                result.getTotal(),
                result.getCurrent(),
                result.getSize()
        ));
    }

    @Operation(summary = "获取最新评论")
    @GetMapping("/comments/recent")
    public Result<List<RecentCommentVO>> getRecentComments(
            @RequestParam(defaultValue = "5") Integer limit) {
        List<Comment> list = commentService.getRecentComments(limit);
        return Result.success(toRecentCommentVOList(list));
    }

    @Operation(summary = "发表评论")
    @PostMapping("/comments")
    public Result<CommentVO> createComment(
            @RequestBody CreateCommentDTO dto,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest request) {
        Long userId = CurrentUserUtil.getUserId(currentUser);
        String ip = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        Comment comment = commentService.createComment(dto, userId, ip, userAgent);
        return Result.success(commentConverter.toVO(comment));
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(
            @PathVariable Long id,
            @RequestParam(required = false) String guestDeleteKey,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.getUserId(currentUser);
        if (userId != null) {
            commentService.deleteComment(id, userId);
            return Result.success("评论已删除");
        }
        commentService.deleteCommentAsGuest(id, guestDeleteKey);
        return Result.success("评论已删除");
    }

    @Operation(summary = "点赞评论")
    @PostMapping("/comments/{id}/like")
    public Result<CommentLikeResult> likeComment(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest request) {
        CommentLikeResult result = commentService.likeComment(id, CurrentUserUtil.getUserId(currentUser), getClientIp(request));
        return Result.success(result);
    }

    @RequireAdmin
    @Operation(summary = "审核评论", description = "仅管理员")
    @PutMapping("/admin/comments/{id}/review")
    public Result<CommentVO> reviewComment(
            @PathVariable Long id,
            @RequestBody ReviewCommentDTO dto) {
        Comment comment = commentService.reviewComment(id, dto.getStatus());
        return Result.success(commentConverter.toVO(comment));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            int commaIndex = ip.indexOf(',');
            ip = commaIndex >= 0 ? ip.substring(0, commaIndex).trim() : ip.trim();
        } else {
            ip = request.getHeader("X-Real-IP");
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                ip = ip.trim();
            } else {
                ip = request.getRemoteAddr();
            }
        }

        // 兼容本机 IPv6 回环
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }

    private List<RecentCommentVO> toRecentCommentVOList(List<Comment> comments) {
        List<Comment> safeComments = comments == null ? Collections.emptyList() : comments;
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
}
