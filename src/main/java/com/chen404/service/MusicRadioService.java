package com.chen404.service;

import com.chen404.domain.dto.MusicPlaylistTracksCommand;
import com.chen404.domain.dto.MusicPlaylistUpsertCommand;
import com.chen404.domain.dto.MusicPlaylistVO;
import com.chen404.domain.dto.MusicTrackUpsertCommand;
import com.chen404.domain.dto.MusicTrackVO;

import java.util.List;

public interface MusicRadioService {

    List<MusicTrackVO> listPublicTracks();

    MusicTrackVO getPublicTrack(Long id);

    List<MusicPlaylistVO> listPublicPlaylists();

    MusicPlaylistVO getPublicPlaylist(Long id);

    MusicPlaylistVO getDefaultRadio();

    List<MusicTrackVO> listAdminTracks();

    MusicTrackVO getAdminTrack(Long id);

    MusicTrackVO createTrack(MusicTrackUpsertCommand command);

    MusicTrackVO updateTrack(Long id, MusicTrackUpsertCommand command);

    MusicTrackVO updateTrackStatus(Long id, String status);

    void deleteTrack(Long id);

    List<MusicPlaylistVO> listAdminPlaylists();

    MusicPlaylistVO createPlaylist(MusicPlaylistUpsertCommand command);

    MusicPlaylistVO updatePlaylist(Long id, MusicPlaylistUpsertCommand command);

    MusicPlaylistVO savePlaylistTracks(Long id, MusicPlaylistTracksCommand command);

    MusicPlaylistVO setDefaultPlaylist(Long id);
}
