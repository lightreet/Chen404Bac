package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.domain.Result;
import com.chen404.domain.dto.MusicPlayerStateCommand;
import com.chen404.domain.dto.MusicPlayerStateVO;
import com.chen404.domain.dto.MusicTrackAiSuggestRequest;
import com.chen404.domain.dto.MusicTrackAiSuggestResponse;
import com.chen404.domain.dto.MusicPlaylistTracksCommand;
import com.chen404.domain.dto.MusicPlaylistUpsertCommand;
import com.chen404.domain.dto.MusicPlaylistVO;
import com.chen404.domain.dto.MusicTrackUpsertCommand;
import com.chen404.domain.dto.MusicTrackVO;
import com.chen404.service.MusicTrackAiSuggestService;
import com.chen404.service.MusicRadioService;
import com.chen404.service.MusicPlayerStateService;
import com.chen404.security.AuthenticatedUser;
import com.chen404.util.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

/**
 * 音乐馆公开播放、歌曲维护和分类维护控制器。
 */
@Tag(name = "音乐馆", description = "公开音乐馆与分类管理接口")
@RestController
public class MusicRadioController {

    private final MusicRadioService musicRadioService;
    private final MusicTrackAiSuggestService musicTrackAiSuggestService;
    private final MusicPlayerStateService musicPlayerStateService;

    public MusicRadioController(
            MusicRadioService musicRadioService,
            MusicTrackAiSuggestService musicTrackAiSuggestService,
            MusicPlayerStateService musicPlayerStateService) {
        this.musicRadioService = musicRadioService;
        this.musicTrackAiSuggestService = musicTrackAiSuggestService;
        this.musicPlayerStateService = musicPlayerStateService;
    }

    @Operation(summary = "获取公开音乐列表")
    @GetMapping("/music/tracks")
    public Result<List<MusicTrackVO>> listPublicTracks(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return Result.success(musicRadioService.listPublicTracks(CurrentUserUtil.getUserId(currentUser)));
    }

    @Operation(summary = "获取公开音乐详情")
    @GetMapping("/music/tracks/{id}")
    public Result<MusicTrackVO> getPublicTrack(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return Result.success(musicRadioService.getPublicTrack(id, CurrentUserUtil.getUserId(currentUser)));
    }

    @Operation(summary = "获取我贡献的音乐")
    @GetMapping("/music/tracks/mine")
    public Result<List<MusicTrackVO>> listMyTracks(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success(musicRadioService.listMyTracks(userId));
    }

    @Operation(summary = "获取我可管理的音乐详情")
    @GetMapping("/music/tracks/mine/{id}")
    public Result<MusicTrackVO> getMyTrack(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success(musicRadioService.getManageableTrack(id, userId));
    }

    @Operation(summary = "新增音乐", description = "知友或管理员可直接保存草稿或发布")
    @PostMapping("/music/tracks")
    public Result<MusicTrackVO> createMyTrack(
            @Valid @RequestBody MusicTrackUpsertCommand command,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success("创建成功", musicRadioService.createTrack(command, userId));
    }

    @Operation(summary = "更新音乐", description = "资源所有者或管理员可更新")
    @PutMapping("/music/tracks/{id}")
    public Result<MusicTrackVO> updateMyTrack(
            @PathVariable Long id,
            @Valid @RequestBody MusicTrackUpsertCommand command,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success("更新成功", musicRadioService.updateTrack(id, command, userId));
    }

    @Operation(summary = "更新音乐状态", description = "资源所有者或管理员可直接切换草稿、发布和归档状态")
    @PatchMapping("/music/tracks/{id}/status")
    public Result<MusicTrackVO> updateMyTrackStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success("状态已更新", musicRadioService.updateTrackStatus(id, status, userId));
    }

    @Operation(summary = "删除音乐", description = "资源所有者或管理员可删除")
    @DeleteMapping("/music/tracks/{id}")
    public Result<Void> deleteMyTrack(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        musicRadioService.deleteTrack(id, userId);
        return Result.success("删除成功");
    }

    @Operation(summary = "获取公开分类列表")
    @GetMapping("/music/playlists")
    public Result<List<MusicPlaylistVO>> listPublicPlaylists() {
        return Result.success(musicRadioService.listPublicPlaylists());
    }

    @Operation(summary = "获取公开分类详情")
    @GetMapping("/music/playlists/{id}")
    public Result<MusicPlaylistVO> getPublicPlaylist(@PathVariable Long id) {
        return Result.success(musicRadioService.getPublicPlaylist(id));
    }

    @Operation(summary = "获取默认播放集")
    @GetMapping("/music/radio/default")
    public Result<MusicPlaylistVO> getDefaultRadio() {
        return Result.success(musicRadioService.getDefaultRadio());
    }

    @Operation(summary = "获取我的播放现场", description = "需要登录；播放现场由 Redis 临时保存")
    @GetMapping("/music/player/state")
    public Result<MusicPlayerStateVO> getPlayerState(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success(musicPlayerStateService.getState(userId));
    }

    @Operation(summary = "保存我的播放现场", description = "需要登录；覆盖保存并刷新 Redis 有效期")
    @PutMapping("/music/player/state")
    public Result<Void> savePlayerState(
            @Valid @RequestBody MusicPlayerStateCommand command,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        musicPlayerStateService.saveState(userId, command);
        return Result.success("播放现场已保存");
    }

    @Operation(summary = "清空我的播放现场", description = "需要登录")
    @DeleteMapping("/music/player/state")
    public Result<Void> clearPlayerState(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = CurrentUserUtil.requireUserId(currentUser);
        musicPlayerStateService.clearState(userId);
        return Result.success("播放现场已清空");
    }

    @RequireAdmin
    @Operation(summary = "获取管理员音乐列表")
    @GetMapping("/admin/music/tracks")
    public Result<List<MusicTrackVO>> listAdminTracks() {
        return Result.success(musicRadioService.listAdminTracks());
    }

    @RequireAdmin
    @Operation(summary = "获取管理员音乐详情")
    @GetMapping("/admin/music/tracks/{id}")
    public Result<MusicTrackVO> getAdminTrack(@PathVariable Long id) {
        return Result.success(musicRadioService.getAdminTrack(id));
    }

    @RequireAdmin
    @Operation(summary = "AI 补全音乐信息")
    @PostMapping("/admin/music/tracks/ai/suggest")
    public Result<MusicTrackAiSuggestResponse> suggestTrack(@Valid @RequestBody MusicTrackAiSuggestRequest request) {
        return Result.success(musicTrackAiSuggestService.suggest(request));
    }

    @RequireAdmin
    @Operation(summary = "新增音乐")
    @PostMapping("/admin/music/tracks")
    public Result<MusicTrackVO> createTrack(
            @Valid @RequestBody MusicTrackUpsertCommand command,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success("创建成功", musicRadioService.createTrack(command, adminId));
    }

    @RequireAdmin
    @Operation(summary = "更新音乐")
    @PutMapping("/admin/music/tracks/{id}")
    public Result<MusicTrackVO> updateTrack(
            @Parameter(description = "音乐 ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody MusicTrackUpsertCommand command,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success("更新成功", musicRadioService.updateTrack(id, command, adminId));
    }

    @RequireAdmin
    @Operation(summary = "更新音乐状态")
    @PatchMapping("/admin/music/tracks/{id}/status")
    public Result<MusicTrackVO> updateTrackStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        return Result.success("状态已更新", musicRadioService.updateTrackStatus(id, status, adminId));
    }

    @RequireAdmin
    @Operation(summary = "删除音乐")
    @DeleteMapping("/admin/music/tracks/{id}")
    public Result<Void> deleteTrack(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long adminId = CurrentUserUtil.requireUserId(currentUser);
        musicRadioService.deleteTrack(id, adminId);
        return Result.success("删除成功", null);
    }

    @RequireAdmin
    @Operation(summary = "获取管理员分类列表")
    @GetMapping("/admin/music/playlists")
    public Result<List<MusicPlaylistVO>> listAdminPlaylists() {
        return Result.success(musicRadioService.listAdminPlaylists());
    }

    @RequireAdmin
    @Operation(summary = "新增分类")
    @PostMapping("/admin/music/playlists")
    public Result<MusicPlaylistVO> createPlaylist(@Valid @RequestBody MusicPlaylistUpsertCommand command) {
        return Result.success("创建成功", musicRadioService.createPlaylist(command));
    }

    @RequireAdmin
    @Operation(summary = "更新分类")
    @PutMapping("/admin/music/playlists/{id}")
    public Result<MusicPlaylistVO> updatePlaylist(
            @PathVariable Long id,
            @Valid @RequestBody MusicPlaylistUpsertCommand command) {
        return Result.success("更新成功", musicRadioService.updatePlaylist(id, command));
    }

    @RequireAdmin
    @Operation(summary = "保存分类歌曲关系")
    @PutMapping("/admin/music/playlists/{id}/tracks")
    public Result<MusicPlaylistVO> savePlaylistTracks(
            @PathVariable Long id,
            @RequestBody MusicPlaylistTracksCommand command) {
        return Result.success("分类已保存", musicRadioService.savePlaylistTracks(id, command));
    }

    @RequireAdmin
    @Operation(summary = "删除分类")
    @DeleteMapping("/admin/music/playlists/{id}")
    public Result<Void> deletePlaylist(@PathVariable Long id) {
        musicRadioService.deletePlaylist(id);
        return Result.success("删除成功", null);
    }

    @RequireAdmin
    @Operation(summary = "设置默认播放集")
    @PatchMapping("/admin/music/playlists/{id}/default")
    public Result<MusicPlaylistVO> setDefaultPlaylist(@PathVariable Long id) {
        return Result.success("默认播放集已更新", musicRadioService.setDefaultPlaylist(id));
    }
}
