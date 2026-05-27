package com.chen404.service.impl;

import com.chen404.config.AiRuntimeProperties;
import com.chen404.domain.dto.MusicTrackAiSuggestRequest;
import com.chen404.domain.dto.MusicTrackAiSuggestResponse;
import com.chen404.service.MusicTrackAiSuggestService;
import com.chen404.service.support.scenario.AiScenarioCode;
import com.chen404.service.support.scenario.AiScenarioExecutor;
import com.chen404.service.support.scenario.AiScenarioRequest;
import com.chen404.service.support.scenario.AiScenarioResult;
import com.chen404.service.support.scenario.music.MusicTrackSuggestScenarioRequest;
import com.chen404.service.support.scenario.music.MusicTrackSuggestScenarioResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 基于通用 LLM 场景执行器的音乐曲目信息补全服务。
 */
@Service
public class LlmMusicTrackAiSuggestServiceImpl implements MusicTrackAiSuggestService {

    private static final String EMPTY_RESULT_ERROR = "LLM 服务返回空的歌曲补全结果";

    private final AiScenarioExecutor aiScenarioExecutor;
    private final AiRuntimeProperties aiRuntimeProperties;

    public LlmMusicTrackAiSuggestServiceImpl(AiScenarioExecutor aiScenarioExecutor, AiRuntimeProperties aiRuntimeProperties) {
        this.aiScenarioExecutor = aiScenarioExecutor;
        this.aiRuntimeProperties = aiRuntimeProperties;
    }

    @Override
    public MusicTrackAiSuggestResponse suggest(MusicTrackAiSuggestRequest request) {
        if (!aiRuntimeProperties.getMusicAssist().isEnabled()) {
            throw new IllegalStateException("当前环境未开启 AI 音乐补全能力");
        }

        AiScenarioResult<MusicTrackSuggestScenarioResult> scenarioExecution = aiScenarioExecutor.execute(
                AiScenarioRequest.of(
                        AiScenarioCode.MUSIC_TRACK_SUGGEST,
                        new MusicTrackSuggestScenarioRequest(request.getTitle(), request.getArtist())
                )
        );
        return toResponse(scenarioExecution.data());
    }

    private MusicTrackAiSuggestResponse toResponse(MusicTrackSuggestScenarioResult scenarioResult) {
        MusicTrackAiSuggestResponse response = new MusicTrackAiSuggestResponse();
        response.setTitle(scenarioResult.title());
        response.setArtist(scenarioResult.artist());
        response.setAlbum(scenarioResult.album());
        response.setReleaseYear(scenarioResult.releaseYear());
        response.setLanguage(scenarioResult.language());
        response.setGenre(scenarioResult.genre());
        response.setTags(scenarioResult.tags());
        response.setRecommendation(scenarioResult.recommendation());
        response.setMoodText(scenarioResult.moodText());
        response.setLyricSource(scenarioResult.lyricSource());
        if (isEmptyResponse(response)) {
            throw new IllegalStateException(EMPTY_RESULT_ERROR);
        }
        return response;
    }

    private boolean isEmptyResponse(MusicTrackAiSuggestResponse response) {
        return !StringUtils.hasText(response.getArtist())
                && !StringUtils.hasText(response.getAlbum())
                && response.getReleaseYear() == null
                && !StringUtils.hasText(response.getLanguage())
                && !StringUtils.hasText(response.getGenre())
                && (response.getTags() == null || response.getTags().isEmpty())
                && !StringUtils.hasText(response.getRecommendation())
                && !StringUtils.hasText(response.getMoodText())
                && !StringUtils.hasText(response.getLyricSource());
    }
}
