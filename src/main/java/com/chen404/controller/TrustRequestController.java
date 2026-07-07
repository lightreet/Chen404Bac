package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.domain.PageResult;
import com.chen404.domain.Result;
import com.chen404.domain.dto.CreateTrustRequestDTO;
import com.chen404.domain.dto.ReviewTrustRequestDTO;
import com.chen404.domain.dto.TrustRequestVO;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.UserTrustRequestService;
import com.chen404.util.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 好友申请提交与后台审核接口。
 */
@Tag(name = "好友申请", description = "好友申请提交、查询与审核接口")
@RestController
public class TrustRequestController {

    private final UserTrustRequestService userTrustRequestService;

    public TrustRequestController(UserTrustRequestService userTrustRequestService) {
        this.userTrustRequestService = userTrustRequestService;
    }

    @Operation(summary = "提交好友申请", description = "登录用户提交好友申请，可携带申请理由和附件 URL 列表")
    @PostMapping("/trust-requests")
    public Result<TrustRequestVO> createRequest(
            @RequestBody CreateTrustRequestDTO dto,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success("申请已提交", userTrustRequestService.createRequest(userId, dto));
    }

    @Operation(summary = "获取我最近的一条好友申请", description = "登录用户查询自己最近一次好友申请及审核状态")
    @GetMapping("/trust-requests/me/latest")
    public Result<TrustRequestVO> getMyLatestRequest(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success(userTrustRequestService.getLatestForUser(userId));
    }

    @RequireAdmin
    @Operation(summary = "管理员分页查询好友申请", description = "仅管理员可查看好友申请列表")
    @GetMapping("/admin/trust-requests")
    public Result<PageResult<TrustRequestVO>> getAdminRequests(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "审核状态：0-待审核 1-已通过 2-已拒绝", example = "0")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "关键词，可按用户名、昵称或申请理由搜索", example = "chen")
            @RequestParam(required = false) String keyword) {
        return Result.success(userTrustRequestService.getAdminRequests(page, size, status, keyword));
    }

    @RequireAdmin
    @Operation(summary = "管理员通过好友申请", description = "仅管理员可将待审核申请标记为通过")
    @PutMapping("/admin/trust-requests/{id}/approve")
    public Result<TrustRequestVO> approveRequest(
            @Parameter(description = "申请 ID", required = true, example = "1001")
            @PathVariable Long id,
            @RequestBody(required = false) ReviewTrustRequestDTO dto,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        String reviewNote = dto == null ? null : dto.getReviewNote();
        return Result.success("审核通过", userTrustRequestService.approveRequest(id, adminId, reviewNote));
    }

    @RequireAdmin
    @Operation(summary = "管理员拒绝好友申请", description = "仅管理员可将待审核申请标记为拒绝")
    @PutMapping("/admin/trust-requests/{id}/reject")
    public Result<TrustRequestVO> rejectRequest(
            @Parameter(description = "申请 ID", required = true, example = "1001")
            @PathVariable Long id,
            @RequestBody ReviewTrustRequestDTO dto,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success("已拒绝申请", userTrustRequestService.rejectRequest(id, adminId, dto == null ? null : dto.getReviewNote()));
    }

    @Operation(summary = "邮件中直接通过好友申请", description = "通过邮件审批 token 完成审核，并返回 HTML 结果页")
    @GetMapping(value = "/trust-requests/email-approve", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> approveByEmail(
            @Parameter(description = "邮件审批 token", required = true)
            @RequestParam("token") String token) {
        String html = userTrustRequestService.approveByEmailToken(token);
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                .body(body);
    }
}
