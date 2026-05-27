package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.dto.MusicPlaylistVO;
import com.chen404.domain.dto.MusicTrackVO;
import com.chen404.service.MusicRadioService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MusicRadioControllerTest {

    @Test
    void shouldExposePublicTracks() {
        MusicRadioService service = mock(MusicRadioService.class);
        MusicRadioController controller = new MusicRadioController(service);
        List<MusicTrackVO> tracks = List.of(new MusicTrackVO());
        when(service.listPublicTracks()).thenReturn(tracks);

        Result<List<MusicTrackVO>> result = controller.listPublicTracks();

        assertSame(tracks, result.getData());
        verify(service).listPublicTracks();
    }

    @Test
    void shouldExposeDefaultRadioForLyraPlayer() {
        MusicRadioService service = mock(MusicRadioService.class);
        MusicRadioController controller = new MusicRadioController(service);
        MusicPlaylistVO playlist = new MusicPlaylistVO();
        when(service.getDefaultRadio()).thenReturn(playlist);

        Result<MusicPlaylistVO> result = controller.getDefaultRadio();

        assertSame(playlist, result.getData());
        verify(service).getDefaultRadio();
    }

    @Test
    void shouldExposeAdminTrackDetailForEditor() {
        MusicRadioService service = mock(MusicRadioService.class);
        MusicRadioController controller = new MusicRadioController(service);
        MusicTrackVO track = new MusicTrackVO();
        when(service.getAdminTrack(9L)).thenReturn(track);

        Result<MusicTrackVO> result = controller.getAdminTrack(9L);

        assertSame(track, result.getData());
        verify(service).getAdminTrack(9L);
    }

    @Test
    void shouldExposeAdminTrackDeletion() {
        MusicRadioService service = mock(MusicRadioService.class);
        MusicRadioController controller = new MusicRadioController(service);

        controller.deleteTrack(9L);

        verify(service).deleteTrack(9L);
    }
}
