package com.chen404.controller;

import com.chen404.domain.Result;
import com.chen404.domain.dto.MusicPlayerStateCommand;
import com.chen404.domain.dto.MusicPlayerStateVO;
import com.chen404.domain.dto.MusicTrackAiSuggestRequest;
import com.chen404.domain.dto.MusicTrackAiSuggestResponse;
import com.chen404.domain.dto.MusicPlaylistVO;
import com.chen404.domain.dto.MusicTrackVO;
import com.chen404.service.MusicRadioService;
import com.chen404.service.MusicPlayerStateService;
import com.chen404.service.MusicTrackAiSuggestService;
import com.chen404.security.AuthenticatedUser;
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
        MusicPlayerStateService playerStateService = mock(MusicPlayerStateService.class);
        MusicRadioController controller = new MusicRadioController(service, suggestService, playerStateService);
        List<MusicTrackVO> tracks = List.of(new MusicTrackVO());
        when(service.listPublicTracks(null)).thenReturn(tracks);

        Result<List<MusicTrackVO>> result = controller.listPublicTracks(null);

        assertSame(tracks, result.getData());
        verify(service).listPublicTracks(null);
    }

    @Test
    void shouldExposeDefaultRadioForLyraPlayer() {
        MusicRadioService service = mock(MusicRadioService.class);
        MusicTrackAiSuggestService suggestService = mock(MusicTrackAiSuggestService.class);
        MusicPlayerStateService playerStateService = mock(MusicPlayerStateService.class);
        MusicRadioController controller = new MusicRadioController(service, suggestService, playerStateService);
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
        MusicPlayerStateService playerStateService = mock(MusicPlayerStateService.class);
        MusicRadioController controller = new MusicRadioController(service, suggestService, playerStateService);
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
        MusicPlayerStateService playerStateService = mock(MusicPlayerStateService.class);
        MusicRadioController controller = new MusicRadioController(service, suggestService, playerStateService);

        AuthenticatedUser admin = new AuthenticatedUser(1L, "admin", "admin");
        controller.deleteTrack(9L, admin);

        verify(service).deleteTrack(9L, 1L);
    }

    @Test
    void shouldExposeAdminAiTrackSuggestion() {
        MusicRadioService service = mock(MusicRadioService.class);
        MusicTrackAiSuggestService suggestService = mock(MusicTrackAiSuggestService.class);
        MusicPlayerStateService playerStateService = mock(MusicPlayerStateService.class);
        MusicRadioController controller = new MusicRadioController(service, suggestService, playerStateService);
        MusicTrackAiSuggestRequest request = new MusicTrackAiSuggestRequest();
        request.setTitle("夜に駆ける");
        MusicTrackAiSuggestResponse response = new MusicTrackAiSuggestResponse();
        when(suggestService.suggest(request)).thenReturn(response);

        Result<MusicTrackAiSuggestResponse> result = controller.suggestTrack(request);

        assertSame(response, result.getData());
        verify(suggestService).suggest(request);
    }

    @Test
    void shouldSaveAuthenticatedPlayerState() {
        MusicRadioService service = mock(MusicRadioService.class);
        MusicTrackAiSuggestService suggestService = mock(MusicTrackAiSuggestService.class);
        MusicPlayerStateService playerStateService = mock(MusicPlayerStateService.class);
        MusicRadioController controller = new MusicRadioController(service, suggestService, playerStateService);
        AuthenticatedUser user = new AuthenticatedUser(7L, "listener", "user");
        MusicPlayerStateCommand command = new MusicPlayerStateCommand();
        command.setTrackIds(List.of(3L, 5L));
        command.setCurrentTrackId(5L);

        controller.savePlayerState(command, user);

        verify(playerStateService).saveState(7L, command);
    }
}
