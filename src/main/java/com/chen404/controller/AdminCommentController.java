package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.domain.PageResult;
import com.chen404.domain.Result;
import com.chen404.domain.dto.AdminCommentStatsVO;
import com.chen404.domain.dto.AdminCommentVO;
import com.chen404.domain.dto.ReviewCommentDTO;
import com.chen404.domain.enums.CommentSceneEnum;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.AdminCommentService;
import com.chen404.util.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端评论查询、统计与审核接口。
 */
@Tag(name = "后台评论管理", description = "管理员评论列表、统计与审核接口")
@RestController
@RequestMapping("/admin/comments")
public class AdminCommentController {

    private final AdminCommentService adminCommentService;

    public AdminCommentController(AdminCommentService adminCommentService) {
        this.adminCommentService = adminCommentService;
    }

    @RequireAdmin
    @Operation(summary = "分页查询评论", description = "按审核状态、来源和关键词查询文章评论与留言板留言")
    @GetMapping
    public Result<PageResult<AdminCommentVO>> getAdminComments(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "审核状态：0-待审核 1-已通过 2-已拒绝")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "来源：ALL-全部 ARTICLE-文章评论 GUESTBOOK-留言板")
            @RequestParam(defaultValue = "ALL") CommentSceneEnum scene,
            @Parameter(description = "关键词，匹配评论内容、昵称或邮箱")
            @RequestParam(required = false) String keyword) {
        return Result.success(adminCommentService.getAdminComments(page, size, status, scene, keyword));
    }

    @RequireAdmin
    @Operation(summary = "查询评论统计", description = "返回全部评论的审核状态数量")
    @GetMapping("/stats")
    public Result<AdminCommentStatsVO> getAdminCommentStats() {
        return Result.success(adminCommentService.getAdminCommentStats());
    }

    @RequireAdmin
    @Operation(summary = "审核评论", description = "管理员通过或拒绝一条文章评论或留言板留言")
    @PutMapping("/{id}/review")
    public Result<AdminCommentVO> reviewComment(
            @Parameter(description = "评论 ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody ReviewCommentDTO dto,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success(
                "审核结果已更新",
                adminCommentService.reviewComment(id, dto.getStatus(), adminId)
        );
    }
}
