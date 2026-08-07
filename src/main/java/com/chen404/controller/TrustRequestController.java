package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.domain.PageResult;
import com.chen404.domain.Result;
import com.chen404.domain.dto.CreateTrustRequestDTO;
import com.chen404.domain.dto.EmailApproveTrustRequestDTO;
import com.chen404.domain.dto.ReviewTrustRequestDTO;
import com.chen404.domain.dto.TrustRequestVO;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.UserTrustRequestService;
import com.chen404.util.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 好友申请提交与后台审核接口。
 */
@Tag(name = "好友申请", description = "好友申请提交、查询与审核接口")
@RestController
public class TrustRequestController {

    private final UserTrustRequestService userTrustRequestService;
    private final String frontendBaseUrl;

    public TrustRequestController(
            UserTrustRequestService userTrustRequestService,
            @Value("${app.frontend-base-url:http://localhost:20204}") String frontendBaseUrl) {
        this.userTrustRequestService = userTrustRequestService;
        this.frontendBaseUrl = frontendBaseUrl;
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

    @Operation(summary = "打开邮件审批确认页", description = "仅跳转到后台确认页，不在 GET 请求中修改审核状态")
    @GetMapping("/trust-requests/email-approve")
    public ResponseEntity<Void> openEmailApproval(
            @Parameter(description = "邮件审批 token", required = true)
            @RequestParam("token") String token) {
        URI confirmationUri = UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .path("/admin")
                .queryParam("tab", "trust-requests")
                .fragment("emailApproveToken=" + token)
                .build()
                .encode()
                .toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(confirmationUri)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }

    @RequireAdmin
    @Operation(summary = "管理员确认邮件审批", description = "管理员登录后使用一次性邮件 token 确认通过申请")
    @PostMapping("/admin/trust-requests/email-approve")
    public Result<TrustRequestVO> approveByEmail(
            @Valid @RequestBody EmailApproveTrustRequestDTO dto,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success(
                "审核通过",
                userTrustRequestService.approveByEmailToken(dto.getToken(), adminId)
        );
    }
}
