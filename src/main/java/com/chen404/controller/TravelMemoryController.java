package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.converter.TravelMemoryConverter;
import com.chen404.domain.Result;
import com.chen404.domain.dto.CreateTravelMemoryCommand;
import com.chen404.domain.dto.TravelMemoryLocationDetailVO;
import com.chen404.domain.dto.TravelMemoryLocationListItemVO;
import com.chen404.domain.dto.UpdateTravelMemoryCommand;
import com.chen404.domain.entity.TravelMemoryLocation;
import com.chen404.exception.ResourceNotFoundException;
import com.chen404.security.AuthenticatedUser;
import com.chen404.service.TravelMemoryService;
import com.chen404.util.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 旅行记忆地图控制器。
 */
@Tag(name = "旅行记忆地图", description = "旅行记忆地图查询与管理接口")
@RestController
public class TravelMemoryController {

    private final TravelMemoryService travelMemoryService;
    private final TravelMemoryConverter travelMemoryConverter;

    public TravelMemoryController(
            TravelMemoryService travelMemoryService,
            TravelMemoryConverter travelMemoryConverter) {
        this.travelMemoryService = travelMemoryService;
        this.travelMemoryConverter = travelMemoryConverter;
    }

    @Operation(summary = "获取当前用户可见的旅行记忆地点列表", description = "游客可查看公开地点，知友与管理员可查看知友可见地点")
    @GetMapping("/travel-memories")
    public Result<List<TravelMemoryLocationListItemVO>> listVisibleMemories(
            @RequestParam(required = false) Long creatorId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.getUserId(currentUser);
        return Result.success(travelMemoryConverter.toListItemVOList(
                travelMemoryService.listVisibleLocations(userId, creatorId)
        ));
    }

    @Operation(summary = "获取我的旅行记忆地点", description = "知友查看自己创建的全部旅行地点")
    @GetMapping("/travel-memories/mine")
    public Result<List<TravelMemoryLocationDetailVO>> listMyMemories(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success(travelMemoryService.listMyLocations(userId).stream()
                .map(travelMemoryConverter::toDetailVO)
                .toList());
    }

    @Operation(summary = "获取我的旅行记忆地点详情", description = "资源所有者或管理员可访问")
    @GetMapping("/travel-memories/mine/{id}")
    public Result<TravelMemoryLocationDetailVO> getMyMemoryDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success(travelMemoryConverter.toDetailVO(
                travelMemoryService.getManageableLocationDetail(id, userId)
        ));
    }

    @Operation(summary = "获取旅行记忆地点详情", description = "游客可查看公开地点详情，知友与管理员可查看知友可见地点详情")
    @GetMapping("/travel-memories/{id}")
    public Result<TravelMemoryLocationDetailVO> getVisibleMemoryDetail(
            @Parameter(description = "地点 ID", required = true, example = "1001")
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.getUserId(currentUser);
        TravelMemoryLocation location = travelMemoryService.getVisibleLocationDetail(id, userId);
        if (location == null) {
            throw new ResourceNotFoundException("旅行记忆地点不存在");
        }
        return Result.success(travelMemoryConverter.toDetailVO(location));
    }

    @RequireAdmin
    @Operation(summary = "获取后台旅行记忆地点列表", description = "仅管理员可访问")
    @GetMapping("/admin/travel-memories")
    public Result<List<TravelMemoryLocationDetailVO>> listAdminMemories(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success(
                travelMemoryService.listAdminLocations(adminId).stream()
                        .map(travelMemoryConverter::toDetailVO)
                        .toList()
        );
    }

    @RequireAdmin
    @Operation(summary = "获取后台旅行记忆地点详情", description = "仅管理员可访问")
    @GetMapping("/admin/travel-memories/{id}")
    public Result<TravelMemoryLocationDetailVO> getAdminMemoryDetail(
            @Parameter(description = "地点 ID", required = true, example = "1001")
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        TravelMemoryLocation location = travelMemoryService.getAdminLocationDetail(id, adminId);
        return Result.success(travelMemoryConverter.toDetailVO(location));
    }

    @RequireAdmin
    @Operation(summary = "创建旅行记忆地点", description = "仅管理员可访问")
    @PostMapping("/admin/travel-memories")
    public Result<TravelMemoryLocationDetailVO> createTravelMemory(
            @Valid @RequestBody CreateTravelMemoryCommand command,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        TravelMemoryLocation created = travelMemoryService.createLocation(
                travelMemoryConverter.toEntity(command),
                travelMemoryConverter.toStopEntityList(command.getStops()),
                travelMemoryConverter.toEntryEntityList(command.getEntries()),
                adminId
        );
        return Result.success("创建成功", travelMemoryConverter.toDetailVO(created));
    }

    @RequireAdmin
    @Operation(summary = "更新旅行记忆地点", description = "仅管理员可访问")
    @PutMapping("/admin/travel-memories/{id}")
    public Result<TravelMemoryLocationDetailVO> updateTravelMemory(
            @Parameter(description = "地点 ID", required = true, example = "1001")
            @PathVariable Long id,
            @Valid @RequestBody UpdateTravelMemoryCommand command,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        TravelMemoryLocation updated = travelMemoryService.updateLocation(
                id,
                travelMemoryConverter.toEntity(command),
                travelMemoryConverter.toStopEntityList(command.getStops()),
                travelMemoryConverter.toEntryEntityList(command.getEntries()),
                adminId
        );
        return Result.success("更新成功", travelMemoryConverter.toDetailVO(updated));
    }

    @RequireAdmin
    @Operation(summary = "删除旅行记忆地点", description = "仅管理员可访问")
    @DeleteMapping("/admin/travel-memories/{id}")
    public Result<Void> deleteTravelMemory(
            @Parameter(description = "地点 ID", required = true, example = "1001")
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        travelMemoryService.deleteLocation(id, adminId);
        return Result.success("删除成功");
    }

    @Operation(summary = "创建旅行记忆地点", description = "知友或管理员可创建")
    @PostMapping("/travel-memories")
    public Result<TravelMemoryLocationDetailVO> createMyTravelMemory(
            @Valid @RequestBody CreateTravelMemoryCommand command,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        TravelMemoryLocation created = travelMemoryService.createLocation(
                travelMemoryConverter.toEntity(command),
                travelMemoryConverter.toStopEntityList(command.getStops()),
                travelMemoryConverter.toEntryEntityList(command.getEntries()),
                userId
        );
        return Result.success("创建成功", travelMemoryConverter.toDetailVO(created));
    }

    @Operation(summary = "更新旅行记忆地点", description = "资源所有者或管理员可更新")
    @PutMapping("/travel-memories/{id}")
    public Result<TravelMemoryLocationDetailVO> updateMyTravelMemory(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTravelMemoryCommand command,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        TravelMemoryLocation updated = travelMemoryService.updateLocation(
                id,
                travelMemoryConverter.toEntity(command),
                travelMemoryConverter.toStopEntityList(command.getStops()),
                travelMemoryConverter.toEntryEntityList(command.getEntries()),
                userId
        );
        return Result.success("更新成功", travelMemoryConverter.toDetailVO(updated));
    }

    @Operation(summary = "删除旅行记忆地点", description = "资源所有者或管理员可删除")
    @DeleteMapping("/travel-memories/{id}")
    public Result<Void> deleteMyTravelMemory(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        travelMemoryService.deleteLocation(id, userId);
        return Result.success("删除成功");
    }
}
