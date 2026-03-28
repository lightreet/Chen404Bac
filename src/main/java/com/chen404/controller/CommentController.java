package com.chen404.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.domain.PageResult;
import com.chen404.domain.Result;
import com.chen404.domain.dto.CreateCommentDTO;
import com.chen404.domain.entity.Comment;
import com.chen404.service.CommentService;
import com.chen404.util.RequestAttrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "评论", description = "评论列表、发表、删除、点赞")
@RestController
@RequestMapping("/comments")
public class CommentController {

    @Autowired
    private CommentService commentService;

    @Operation(summary = "获取评论列表", description = "按 articleId 分页查询已审核评论树")
    @GetMapping("")
    public Result<PageResult<Comment>> getComments(
            @RequestParam(required = false) Long articleId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<Comment> result = commentService.getCommentsByArticleId(articleId, page, size);
        return Result.success(PageResult.of(result));
    }

    @Operation(summary = "获取留言板评论")
    @GetMapping("/guestbook")
    public Result<PageResult<Comment>> getGuestbookComments(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<Comment> result = commentService.getGuestbookComments(page, size);
        return Result.success(PageResult.of(result));
    }

    @Operation(summary = "获取最新评论")
    @GetMapping("/recent")
    public Result<List<Comment>> getRecentComments(
            @RequestParam(defaultValue = "5") Integer limit) {
        List<Comment> list = commentService.getRecentComments(limit);
        return Result.success(list);
    }

    @Operation(summary = "发表评论")
    @PostMapping("")
    public Result<Comment> createComment(
            @RequestBody CreateCommentDTO dto,
            HttpServletRequest request) {
        Long userId = RequestAttrUtil.getUserId(request);
        String ip = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        Comment comment = commentService.createComment(dto, userId, ip, userAgent);
        return Result.success(comment);
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/{id}")
    public Result<Void> deleteComment(
            @PathVariable Long id,
            @RequestParam(required = false) String guestDeleteKey,
            HttpServletRequest request) {
        Long userId = RequestAttrUtil.getUserId(request);
        if (userId != null) {
            commentService.deleteComment(id, userId);
            return Result.success("评论已删除");
        }
        commentService.deleteCommentAsGuest(id, guestDeleteKey);
        return Result.success("评论已删除");
    }

    @Operation(summary = "点赞评论")
    @PostMapping("/{id}/like")
    public Result<Map<String, Integer>> likeComment(@PathVariable Long id) {
        int likes = commentService.likeComment(id);
        return Result.success(Map.of("likes", likes));
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
}
