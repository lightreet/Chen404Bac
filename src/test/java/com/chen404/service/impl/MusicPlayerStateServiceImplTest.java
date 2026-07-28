package com.chen404.service.impl;

import com.chen404.domain.dto.MusicPlayerStateCommand;
import com.chen404.domain.dto.MusicPlayerStateVO;
import com.chen404.util.RedisKeys;
import com.chen404.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MusicPlayerStateServiceImplTest {

    private static final Duration STATE_TTL = Duration.ofDays(7);

    @Test
    void shouldNormalizeAndSavePlayerStateWithTtl() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        MusicPlayerStateServiceImpl service = new MusicPlayerStateServiceImpl(redisUtil, STATE_TTL);
        MusicPlayerStateCommand command = new MusicPlayerStateCommand();
        command.setTrackIds(List.of(3L, 5L, 3L));
        command.setCurrentTrackId(5L);
        command.setCurrentTime(42.75D);
        command.setMode("shuffle");
        ArgumentCaptor<MusicPlayerStateVO> stateCaptor = ArgumentCaptor.forClass(MusicPlayerStateVO.class);

        service.saveState(9L, command);

        verify(redisUtil).setJson(
                org.mockito.ArgumentMatchers.eq(RedisKeys.musicPlayerState(9L)),
                stateCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(STATE_TTL));
        MusicPlayerStateVO state = stateCaptor.getValue();
        assertEquals(List.of(3L, 5L), state.getTrackIds());
        assertEquals(5L, state.getCurrentTrackId());
        assertEquals(42.75D, state.getCurrentTime());
        assertEquals("shuffle", state.getMode());
        assertNotNull(state.getUpdatedAt());
    }

    @Test
    void shouldReadPlayerStateFromUserScopedKey() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        MusicPlayerStateServiceImpl service = new MusicPlayerStateServiceImpl(redisUtil, STATE_TTL);
        MusicPlayerStateVO stored = new MusicPlayerStateVO();
        when(redisUtil.getJson(RedisKeys.musicPlayerState(12L), MusicPlayerStateVO.class)).thenReturn(stored);

        MusicPlayerStateVO result = service.getState(12L);

        assertEquals(stored, result);
    }

    @Test
    void shouldNotBlockPlaybackWhenRedisSaveFails() {
        RedisUtil redisUtil = mock(RedisUtil.class);
        MusicPlayerStateServiceImpl service = new MusicPlayerStateServiceImpl(redisUtil, STATE_TTL);
        MusicPlayerStateCommand command = new MusicPlayerStateCommand();
        command.setTrackIds(List.of(3L));
        doThrow(new IllegalStateException("redis unavailable"))
                .when(redisUtil).setJson(any(String.class), any(MusicPlayerStateVO.class), any(Duration.class));

        assertDoesNotThrow(() -> service.saveState(9L, command));
    }
}
