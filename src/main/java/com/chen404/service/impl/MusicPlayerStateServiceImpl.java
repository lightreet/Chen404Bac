package com.chen404.service.impl;

import com.chen404.domain.dto.MusicPlayerStateCommand;
import com.chen404.domain.dto.MusicPlayerStateVO;
import com.chen404.service.MusicPlayerStateService;
import com.chen404.util.RedisKeys;
import com.chen404.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 基于 Redis JSON 快照保存音乐播放现场。
 *
 * <p>该状态属于尽力而为的临时数据；Redis 短暂不可用时不阻断用户正常播放。</p>
 */
@Slf4j
@Service
public class MusicPlayerStateServiceImpl implements MusicPlayerStateService {

    private static final String DEFAULT_PLAY_MODE = "sequence";
    private static final Set<String> SUPPORTED_PLAY_MODES = Set.of("sequence", "shuffle", "single");

    private final RedisUtil redisUtil;
    private final Duration stateTtl;

    public MusicPlayerStateServiceImpl(
            RedisUtil redisUtil,
            @Value("${app.music.player-state-ttl:7d}") Duration stateTtl) {
        this.redisUtil = redisUtil;
        this.stateTtl = stateTtl;
    }

    @Override
    public MusicPlayerStateVO getState(Long userId) {
        try {
            return redisUtil.getJson(RedisKeys.musicPlayerState(userId), MusicPlayerStateVO.class);
        } catch (RuntimeException exception) {
            log.warn("[MUSIC_PLAYER_STATE_READ_FAIL] userId={} message={}", userId, exception.getMessage());
            return null;
        }
    }

    @Override
    public void saveState(Long userId, MusicPlayerStateCommand command) {
        MusicPlayerStateVO state = normalize(command);
        try {
            redisUtil.setJson(RedisKeys.musicPlayerState(userId), state, stateTtl);
        } catch (RuntimeException exception) {
            log.warn("[MUSIC_PLAYER_STATE_SAVE_FAIL] userId={} queueSize={} message={}",
                    userId, state.getTrackIds().size(), exception.getMessage());
        }
    }

    @Override
    public void clearState(Long userId) {
        try {
            redisUtil.delete(RedisKeys.musicPlayerState(userId));
        } catch (RuntimeException exception) {
            log.warn("[MUSIC_PLAYER_STATE_CLEAR_FAIL] userId={} message={}", userId, exception.getMessage());
        }
    }

    private MusicPlayerStateVO normalize(MusicPlayerStateCommand command) {
        List<Long> trackIds = command.getTrackIds() == null
                ? List.of()
                : new LinkedHashSet<>(command.getTrackIds()).stream()
                        .filter(Objects::nonNull)
                        .filter(trackId -> trackId > 0)
                        .limit(MusicPlayerStateCommand.MAX_QUEUE_SIZE)
                        .toList();

        Long currentTrackId = trackIds.contains(command.getCurrentTrackId())
                ? command.getCurrentTrackId()
                : trackIds.stream().findFirst().orElse(null);

        MusicPlayerStateVO state = new MusicPlayerStateVO();
        state.setTrackIds(trackIds);
        state.setCurrentTrackId(currentTrackId);
        state.setCurrentTime(currentTrackId == null ? 0D : normalizeCurrentTime(command.getCurrentTime()));
        state.setMode(normalizeMode(command.getMode()));
        state.setUpdatedAt(Instant.now());
        return state;
    }

    private double normalizeCurrentTime(Double currentTime) {
        if (currentTime == null || !Double.isFinite(currentTime) || currentTime < 0) {
            return 0D;
        }
        return currentTime;
    }

    private String normalizeMode(String mode) {
        if (!StringUtils.hasText(mode) || !SUPPORTED_PLAY_MODES.contains(mode)) {
            return DEFAULT_PLAY_MODE;
        }
        return mode;
    }
}
