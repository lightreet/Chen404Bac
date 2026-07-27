package com.chen404.service;

import com.chen404.domain.dto.MusicPlaylistTracksCommand;
import com.chen404.domain.dto.MusicPlaylistUpsertCommand;
import com.chen404.domain.dto.MusicPlaylistVO;
import com.chen404.domain.dto.MusicTrackUpsertCommand;
import com.chen404.domain.dto.MusicTrackVO;

import java.util.List;

/**
 * 音乐馆歌曲与分类编排服务。
 */
public interface MusicRadioService {

    /**
     * 获取公开歌曲列表。
     *
     * @return 已公开歌曲
     */
    List<MusicTrackVO> listPublicTracks(Long viewerId);

    /**
     * 获取公开歌曲详情。
     *
     * @param id 歌曲 ID
     * @return 歌曲详情
     */
    MusicTrackVO getPublicTrack(Long id, Long viewerId);

    /**
     * 获取当前用户贡献的全部歌曲。
     */
    List<MusicTrackVO> listMyTracks(Long userId);

    /**
     * 获取当前用户可管理的歌曲详情。
     */
    MusicTrackVO getManageableTrack(Long id, Long userId);

    /**
     * 获取公开分类列表。
     *
     * @return 分类列表
     */
    List<MusicPlaylistVO> listPublicPlaylists();

    /**
     * 获取公开分类详情。
     *
     * @param id 分类 ID
     * @return 分类详情
     */
    MusicPlaylistVO getPublicPlaylist(Long id);

    /**
     * 获取默认播放集。
     *
     * @return 默认播放集
     */
    MusicPlaylistVO getDefaultRadio();

    /**
     * 获取管理员歌曲列表。
     *
     * @return 歌曲列表
     */
    List<MusicTrackVO> listAdminTracks();

    /**
     * 获取管理员歌曲详情。
     *
     * @param id 歌曲 ID
     * @return 歌曲详情
     */
    MusicTrackVO getAdminTrack(Long id);

    /**
     * 新增歌曲。
     *
     * @param command 歌曲命令
     * @return 新增后的歌曲
     */
    MusicTrackVO createTrack(MusicTrackUpsertCommand command, Long operatorId);

    /**
     * 更新歌曲。
     *
     * @param id 歌曲 ID
     * @param command 歌曲命令
     * @return 更新后的歌曲
     */
    MusicTrackVO updateTrack(Long id, MusicTrackUpsertCommand command, Long operatorId);

    /**
     * 更新歌曲状态。
     *
     * @param id 歌曲 ID
     * @param status 目标状态
     * @return 更新后的歌曲
     */
    MusicTrackVO updateTrackStatus(Long id, String status, Long operatorId);

    /**
     * 删除歌曲。
     *
     * @param id 歌曲 ID
     */
    void deleteTrack(Long id, Long operatorId);

    /**
     * 获取管理员分类列表。
     *
     * @return 分类列表
     */
    List<MusicPlaylistVO> listAdminPlaylists();

    /**
     * 新增分类。
     *
     * @param command 分类命令
     * @return 新增后的分类
     */
    MusicPlaylistVO createPlaylist(MusicPlaylistUpsertCommand command);

    /**
     * 更新分类。
     *
     * @param id 分类 ID
     * @param command 分类命令
     * @return 更新后的分类
     */
    MusicPlaylistVO updatePlaylist(Long id, MusicPlaylistUpsertCommand command);

    /**
     * 保存分类下的歌曲顺序。
     *
     * @param id 分类 ID
     * @param command 分类歌曲命令
     * @return 更新后的分类
     */
    MusicPlaylistVO savePlaylistTracks(Long id, MusicPlaylistTracksCommand command);

    /**
     * 删除分类，仅移除分类本体与分类歌曲关系，不删除歌曲。
     *
     * @param id 分类 ID
     */
    void deletePlaylist(Long id);

    /**
     * 设置默认播放集。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     */
    MusicPlaylistVO setDefaultPlaylist(Long id);
}
