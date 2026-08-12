package com.chen404.service.impl;

import com.chen404.domain.dto.MusicTrackAiCandidate;
import com.chen404.domain.dto.MusicTrackAiSuggestRequest;
import com.chen404.domain.dto.MusicTrackAiSuggestResponse;
import com.chen404.domain.enums.RuntimeFeatureEnum;
import com.chen404.service.FeatureToggleService;
import com.chen404.service.MusicTrackAiSuggestService;
import com.chen404.service.support.scenario.AiScenarioCode;
import com.chen404.service.support.scenario.AiScenarioExecutor;
import com.chen404.service.support.scenario.AiScenarioRequest;
import com.chen404.service.support.scenario.AiScenarioResult;
import com.chen404.service.support.scenario.music.MusicTrackSuggestCandidate;
import com.chen404.service.support.scenario.music.MusicTrackSuggestScenarioRequest;
import com.chen404.service.support.scenario.music.MusicTrackSuggestScenarioResult;
import org.springframework.stereotype.Service;

/**
 * 基于通用 LLM 场景执行器的音乐曲目信息补全服务。
 */
@Service
public class LlmMusicTrackAiSuggestServiceImpl implements MusicTrackAiSuggestService {

    private static final String EMPTY_RESULT_ERROR = "LLM 服务返回空的歌曲补全结果";

    private final AiScenarioExecutor aiScenarioExecutor;
    private final FeatureToggleService featureToggleService;

    public LlmMusicTrackAiSuggestServiceImpl(
            AiScenarioExecutor aiScenarioExecutor,
            FeatureToggleService featureToggleService) {
        this.aiScenarioExecutor = aiScenarioExecutor;
        this.featureToggleService = featureToggleService;
    }

    @Override
    public MusicTrackAiSuggestResponse suggest(MusicTrackAiSuggestRequest request) {
        if (!featureToggleService.isEnabled(RuntimeFeatureEnum.AI_MUSIC_ASSIST)) {
            throw new IllegalStateException("AI 音乐补全当前已在管理后台关闭");
        }

        AiScenarioResult<MusicTrackSuggestScenarioResult> scenarioExecution = aiScenarioExecutor.execute(
                AiScenarioRequest.of(
                        AiScenarioCode.MUSIC_TRACK_SUGGEST,
                        new MusicTrackSuggestScenarioRequest(
                                request.getTitle(),
                                request.getArtist(),
                                request.getAlbum(),
                                request.getReleaseYear(),
                                request.getLanguage(),
                                request.getGenre(),
                                request.getLyrics(),
                                request.getLimit()
                        )
                )
        );
        return toResponse(scenarioExecution.data());
    }

    private MusicTrackAiSuggestResponse toResponse(MusicTrackSuggestScenarioResult scenarioResult) {
        MusicTrackAiSuggestResponse response = new MusicTrackAiSuggestResponse();
        response.setCandidates(scenarioResult.candidates().stream()
                .map(this::toCandidateResponse)
                .toList());
        if (isEmptyResponse(response)) {
            throw new IllegalStateException(EMPTY_RESULT_ERROR);
        }
        return response;
    }

    private MusicTrackAiCandidate toCandidateResponse(MusicTrackSuggestCandidate scenarioCandidate) {
        MusicTrackAiCandidate response = new MusicTrackAiCandidate();
        response.setTitle(scenarioCandidate.title());
        response.setArtist(scenarioCandidate.artist());
        response.setAlbum(scenarioCandidate.album());
        response.setReleaseYear(scenarioCandidate.releaseYear());
        response.setLanguage(scenarioCandidate.language());
        response.setGenre(scenarioCandidate.genre());
        response.setTags(scenarioCandidate.tags());
        response.setRecommendation(scenarioCandidate.recommendation());
        response.setMoodText(scenarioCandidate.moodText());
        response.setLyricSource(scenarioCandidate.lyricSource());
        response.setConfidence(scenarioCandidate.confidence());
        response.setMatchReason(scenarioCandidate.matchReason());
        return response;
    }

    private boolean isEmptyResponse(MusicTrackAiSuggestResponse response) {
        return response.getCandidates() == null || response.getCandidates().isEmpty();
    }
}
