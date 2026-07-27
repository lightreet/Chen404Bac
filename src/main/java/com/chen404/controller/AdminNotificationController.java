package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.domain.PageResult;
import com.chen404.domain.Result;
import com.chen404.domain.dto.AdminNotificationVO;
import com.chen404.domain.dto.UnreadCountVO;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.AdminNotificationService;
import com.chen404.util.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台消息中心", description = "管理员业务消息、未读数与已读状态接口")
@RestController
@RequestMapping("/admin/notifications")
public class AdminNotificationController {

    private final AdminNotificationService adminNotificationService;

    public AdminNotificationController(AdminNotificationService adminNotificationService) {
        this.adminNotificationService = adminNotificationService;
    }

    @RequireAdmin
    @Operation(summary = "分页查询管理员消息")
    @GetMapping
    public Result<PageResult<AdminNotificationVO>> listNotifications(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Integer readStatus,
            @RequestParam(required = false) String eventType,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success(adminNotificationService.listNotifications(
                adminId,
                page,
                size,
                readStatus,
                eventType
        ));
    }

    @RequireAdmin
    @Operation(summary = "获取管理员未读消息数")
    @GetMapping("/unread-count")
    public Result<UnreadCountVO> getUnreadCount(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success(new UnreadCountVO(adminNotificationService.countUnread(adminId)));
    }

    @RequireAdmin
    @Operation(summary = "将一条管理员消息标为已读")
    @PutMapping("/{id}/read")
    public Result<Void> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        adminNotificationService.markRead(id, adminId);
        return Result.success("消息已读");
    }

    @RequireAdmin
    @Operation(summary = "将全部管理员消息标为已读")
    @PutMapping("/read-all")
    public Result<Void> markAllRead(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        adminNotificationService.markAllRead(adminId);
        return Result.success("全部消息已读");
    }

    @RequireAdmin
    @Operation(summary = "删除一条管理员消息")
    @DeleteMapping("/{id}")
    public Result<Void> deleteNotification(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        adminNotificationService.deleteNotification(id, adminId);
        return Result.success("消息已删除");
    }
}
