package com.chen404.controller;

import com.chen404.annotation.RequireAdmin;
import com.chen404.domain.Result;
import com.chen404.domain.dto.MusicTrackAiSuggestRequest;
import com.chen404.domain.dto.MusicTrackAiSuggestResponse;
import com.chen404.domain.dto.MusicPlaylistTracksCommand;
import com.chen404.domain.dto.MusicPlaylistUpsertCommand;
import com.chen404.domain.dto.MusicPlaylistVO;
import com.chen404.domain.dto.MusicTrackUpsertCommand;
import com.chen404.domain.dto.MusicTrackVO;
import com.chen404.service.MusicTrackAiSuggestService;
import com.chen404.service.MusicRadioService;
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

import java.util.List;

@Tag(name = "Sakura Radio", description = "公开音乐馆与音乐电台管理接口")
@RestController
public class MusicRadioController {

    private final MusicRadioService musicRadioService;
    private final MusicTrackAiSuggestService musicTrackAiSuggestService;

    public MusicRadioController(MusicRadioService musicRadioService, MusicTrackAiSuggestService musicTrackAiSuggestService) {
        this.musicRadioService = musicRadioService;
        this.musicTrackAiSuggestService = musicTrackAiSuggestService;
    }

    @Operation(summary = "获取公开音乐列表")
    @GetMapping("/music/tracks")
    public Result<List<MusicTrackVO>> listPublicTracks() {
        return Result.success(musicRadioService.listPublicTracks());
    }

    @Operation(summary = "获取公开音乐详情")
    @GetMapping("/music/tracks/{id}")
    public Result<MusicTrackVO> getPublicTrack(@PathVariable Long id) {
        return Result.success(musicRadioService.getPublicTrack(id));
    }

    @Operation(summary = "获取公开歌单列表")
    @GetMapping("/music/playlists")
    public Result<List<MusicPlaylistVO>> listPublicPlaylists() {
        return Result.success(musicRadioService.listPublicPlaylists());
    }

    @Operation(summary = "获取公开歌单详情")
    @GetMapping("/music/playlists/{id}")
    public Result<MusicPlaylistVO> getPublicPlaylist(@PathVariable Long id) {
        return Result.success(musicRadioService.getPublicPlaylist(id));
    }

    @Operation(summary = "获取默认电台")
    @GetMapping("/music/radio/default")
    public Result<MusicPlaylistVO> getDefaultRadio() {
        return Result.success(musicRadioService.getDefaultRadio());
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
    public Result<MusicTrackVO> createTrack(@Valid @RequestBody MusicTrackUpsertCommand command) {
        return Result.success("创建成功", musicRadioService.createTrack(command));
    }

    @RequireAdmin
    @Operation(summary = "更新音乐")
    @PutMapping("/admin/music/tracks/{id}")
    public Result<MusicTrackVO> updateTrack(
            @Parameter(description = "音乐 ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody MusicTrackUpsertCommand command) {
        return Result.success("更新成功", musicRadioService.updateTrack(id, command));
    }

    @RequireAdmin
    @Operation(summary = "更新音乐状态")
    @PatchMapping("/admin/music/tracks/{id}/status")
    public Result<MusicTrackVO> updateTrackStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return Result.success("状态已更新", musicRadioService.updateTrackStatus(id, status));
    }

    @RequireAdmin
    @Operation(summary = "删除音乐")
    @DeleteMapping("/admin/music/tracks/{id}")
    public Result<Void> deleteTrack(@PathVariable Long id) {
        musicRadioService.deleteTrack(id);
        return Result.success("删除成功", null);
    }

    @RequireAdmin
    @Operation(summary = "获取管理员歌单列表")
    @GetMapping("/admin/music/playlists")
    public Result<List<MusicPlaylistVO>> listAdminPlaylists() {
        return Result.success(musicRadioService.listAdminPlaylists());
    }

    @RequireAdmin
    @Operation(summary = "新增歌单")
    @PostMapping("/admin/music/playlists")
    public Result<MusicPlaylistVO> createPlaylist(@Valid @RequestBody MusicPlaylistUpsertCommand command) {
        return Result.success("创建成功", musicRadioService.createPlaylist(command));
    }

    @RequireAdmin
    @Operation(summary = "更新歌单")
    @PutMapping("/admin/music/playlists/{id}")
    public Result<MusicPlaylistVO> updatePlaylist(
            @PathVariable Long id,
            @Valid @RequestBody MusicPlaylistUpsertCommand command) {
        return Result.success("更新成功", musicRadioService.updatePlaylist(id, command));
    }

    @RequireAdmin
    @Operation(summary = "保存歌单歌曲排序")
    @PutMapping("/admin/music/playlists/{id}/tracks")
    public Result<MusicPlaylistVO> savePlaylistTracks(
            @PathVariable Long id,
            @RequestBody MusicPlaylistTracksCommand command) {
        return Result.success("歌单已保存", musicRadioService.savePlaylistTracks(id, command));
    }

    @RequireAdmin
    @Operation(summary = "设置默认电台")
    @PatchMapping("/admin/music/playlists/{id}/default")
    public Result<MusicPlaylistVO> setDefaultPlaylist(@PathVariable Long id) {
        return Result.success("默认电台已更新", musicRadioService.setDefaultPlaylist(id));
    }
}
