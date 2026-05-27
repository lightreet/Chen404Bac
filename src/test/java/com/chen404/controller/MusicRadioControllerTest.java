package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.dto.MusicTrackAiSuggestRequest;
import com.chen404.domain.dto.MusicTrackAiSuggestResponse;
import com.chen404.domain.dto.MusicPlaylistVO;
import com.chen404.domain.dto.MusicTrackVO;
import com.chen404.service.MusicRadioService;
import com.chen404.service.MusicTrackAiSuggestService;
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
        MusicTrackAiSuggestService suggestService = mock(MusicTrackAiSuggestService.class);
        MusicRadioController controller = new MusicRadioController(service, suggestService);
        List<MusicTrackVO> tracks = List.of(new MusicTrackVO());
        when(service.listPublicTracks()).thenReturn(tracks);

        Result<List<MusicTrackVO>> result = controller.listPublicTracks();

        assertSame(tracks, result.getData());
        verify(service).listPublicTracks();
    }

    @Test
    void shouldExposeDefaultRadioForLyraPlayer() {
        MusicRadioService service = mock(MusicRadioService.class);
        MusicTrackAiSuggestService suggestService = mock(MusicTrackAiSuggestService.class);
        MusicRadioController controller = new MusicRadioController(service, suggestService);
        MusicPlaylistVO playlist = new MusicPlaylistVO();
        when(service.getDefaultRadio()).thenReturn(playlist);

        Result<MusicPlaylistVO> result = controller.getDefaultRadio();

        assertSame(playlist, result.getData());
        verify(service).getDefaultRadio();
    }

    @Test
    void shouldExposeAdminTrackDetailForEditor() {
        MusicRadioService service = mock(MusicRadioService.class);
        MusicTrackAiSuggestService suggestService = mock(MusicTrackAiSuggestService.class);
        MusicRadioController controller = new MusicRadioController(service, suggestService);
        MusicTrackVO track = new MusicTrackVO();
        when(service.getAdminTrack(9L)).thenReturn(track);

        Result<MusicTrackVO> result = controller.getAdminTrack(9L);

        assertSame(track, result.getData());
        verify(service).getAdminTrack(9L);
    }

    @Test
    void shouldExposeAdminTrackDeletion() {
        MusicRadioService service = mock(MusicRadioService.class);
        MusicTrackAiSuggestService suggestService = mock(MusicTrackAiSuggestService.class);
        MusicRadioController controller = new MusicRadioController(service, suggestService);

        controller.deleteTrack(9L);

        verify(service).deleteTrack(9L);
    }

    @Test
    void shouldExposeAdminAiTrackSuggestion() {
        MusicRadioService service = mock(MusicRadioService.class);
        MusicTrackAiSuggestService suggestService = mock(MusicTrackAiSuggestService.class);
        MusicRadioController controller = new MusicRadioController(service, suggestService);
        MusicTrackAiSuggestRequest request = new MusicTrackAiSuggestRequest();
        request.setTitle("夜に駆ける");
        MusicTrackAiSuggestResponse response = new MusicTrackAiSuggestResponse();
        when(suggestService.suggest(request)).thenReturn(response);

        Result<MusicTrackAiSuggestResponse> result = controller.suggestTrack(request);

        assertSame(response, result.getData());
        verify(suggestService).suggest(request);
    }
}
