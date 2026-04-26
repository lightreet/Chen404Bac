package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.domain.PageResult;
import com.chen404.domain.Result;
import com.chen404.domain.dto.CreateTrustRequestDTO;
import com.chen404.domain.dto.ReviewTrustRequestDTO;
import com.chen404.domain.dto.TrustRequestVO;
import com.chen404.service.UserTrustRequestService;
import com.chen404.util.RequestAttrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@Tag(name = "受信申请", description = "受信任用户申请与审核")
@RestController
public class TrustRequestController {

    private final UserTrustRequestService userTrustRequestService;

    public TrustRequestController(UserTrustRequestService userTrustRequestService) {
        this.userTrustRequestService = userTrustRequestService;
    }

    @Operation(summary = "提交受信申请")
    @PostMapping("/trust-requests")
    public Result<TrustRequestVO> createRequest(@RequestBody CreateTrustRequestDTO dto, HttpServletRequest request) {
        Long userId = RequestAttrUtil.requireUserId(request);
        return Result.success("申请已提交", userTrustRequestService.createRequest(userId, dto));
    }

    @Operation(summary = "获取我最近的一条受信申请")
    @GetMapping("/trust-requests/me/latest")
    public Result<TrustRequestVO> getMyLatestRequest(HttpServletRequest request) {
        Long userId = RequestAttrUtil.requireUserId(request);
        return Result.success(userTrustRequestService.getLatestForUser(userId));
    }

    @RequireAdmin
    @Operation(summary = "管理员分页查询受信申请")
    @GetMapping("/admin/trust-requests")
    public Result<PageResult<TrustRequestVO>> getAdminRequests(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.success(userTrustRequestService.getAdminRequests(page, size, status, keyword));
    }

    @RequireAdmin
    @Operation(summary = "管理员通过受信申请")
    @PutMapping("/admin/trust-requests/{id}/approve")
    public Result<TrustRequestVO> approveRequest(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewTrustRequestDTO dto,
            HttpServletRequest request) {
        Long adminId = RequestAttrUtil.requireUserId(request);
        String reviewNote = dto == null ? null : dto.getReviewNote();
        return Result.success("审核通过", userTrustRequestService.approveRequest(id, adminId, reviewNote));
    }

    @RequireAdmin
    @Operation(summary = "管理员拒绝受信申请")
    @PutMapping("/admin/trust-requests/{id}/reject")
    public Result<TrustRequestVO> rejectRequest(
            @PathVariable Long id,
            @RequestBody ReviewTrustRequestDTO dto,
            HttpServletRequest request) {
        Long adminId = RequestAttrUtil.requireUserId(request);
        return Result.success("已拒绝申请", userTrustRequestService.rejectRequest(id, adminId, dto == null ? null : dto.getReviewNote()));
    }

    @Operation(summary = "邮件中直接通过受信申请")
    @GetMapping(value = "/trust-requests/email-approve", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> approveByEmail(@RequestParam("token") String token) {
        String html = userTrustRequestService.approveByEmailToken(token);
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                .body(body);
    }
}
