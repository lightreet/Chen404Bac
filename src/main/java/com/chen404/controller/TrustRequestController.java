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
 * ??????????????????
 */
@Tag(name = "????", description = "??????????")
@RestController
public class TrustRequestController {

    private final UserTrustRequestService userTrustRequestService;

    public TrustRequestController(UserTrustRequestService userTrustRequestService) {
        this.userTrustRequestService = userTrustRequestService;
    }

    @Operation(summary = "??????", description = "???????????????? URL ??")
    @PostMapping("/trust-requests")
    public Result<TrustRequestVO> createRequest(
            @RequestBody CreateTrustRequestDTO dto,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success("?????", userTrustRequestService.createRequest(userId, dto));
    }

    @Operation(summary = "????????????", description = "??????????????????????????????")
    @GetMapping("/trust-requests/me/latest")
    public Result<TrustRequestVO> getMyLatestRequest(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success(userTrustRequestService.getLatestForUser(userId));
    }

    @RequireAdmin
    @Operation(summary = "???????????", description = "???????????????????")
    @GetMapping("/admin/trust-requests")
    public Result<PageResult<TrustRequestVO>> getAdminRequests(
            @Parameter(description = "???? 1 ??", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "????", example = "10")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "?????0-??? 1-??? 2-???", example = "0")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "????????????????????", example = "chen")
            @RequestParam(required = false) String keyword) {
        return Result.success(userTrustRequestService.getAdminRequests(page, size, status, keyword));
    }

    @RequireAdmin
    @Operation(summary = "?????????", description = "???????????????")
    @PutMapping("/admin/trust-requests/{id}/approve")
    public Result<TrustRequestVO> approveRequest(
            @Parameter(description = "???? ID", required = true, example = "1001")
            @PathVariable Long id,
            @RequestBody(required = false) ReviewTrustRequestDTO dto,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        String reviewNote = dto == null ? null : dto.getReviewNote();
        return Result.success("????", userTrustRequestService.approveRequest(id, adminId, reviewNote));
    }

    @RequireAdmin
    @Operation(summary = "?????????", description = "????????????????")
    @PutMapping("/admin/trust-requests/{id}/reject")
    public Result<TrustRequestVO> rejectRequest(
            @Parameter(description = "???? ID", required = true, example = "1001")
            @PathVariable Long id,
            @RequestBody ReviewTrustRequestDTO dto,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success("?????", userTrustRequestService.rejectRequest(id, adminId, dto == null ? null : dto.getReviewNote()));
    }

    @Operation(summary = "???????????", description = "??????????????????? HTML ???")
    @GetMapping(value = "/trust-requests/email-approve", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> approveByEmail(
            @Parameter(description = "??????", required = true)
            @RequestParam("token") String token) {
        String html = userTrustRequestService.approveByEmailToken(token);
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                .body(body);
    }
}
