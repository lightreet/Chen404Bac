package com.chen404.service.impl;

import com.chen404.domain.dto.AiAdminConfigDTO;
import com.chen404.domain.dto.MusicTrackAiSuggestRequest;
import com.chen404.domain.dto.MusicTrackAiSuggestResponse;
import com.chen404.domain.enums.RuntimeFeatureEnum;
import com.chen404.service.AiConfigService;
import com.chen404.service.FeatureToggleService;
import com.chen404.service.support.AiLlmRequestFactory;
import com.chen404.service.support.LlmClient;
import com.chen404.service.support.LlmTextRequest;
import com.chen404.service.support.scenario.AiScenarioExecutor;
import com.chen404.service.support.scenario.music.MusicTrackSuggestScenarioDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LlmMusicTrackAiSuggestServiceImplTest {

    @Test
    void shouldRejectBeforeScenarioExecutionWhenFeatureIsDisabled() {
        AiScenarioExecutor executor = mock(AiScenarioExecutor.class);
        LlmMusicTrackAiSuggestServiceImpl service = new LlmMusicTrackAiSuggestServiceImpl(
                executor,
                mock(FeatureToggleService.class)
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.suggest(new MusicTrackAiSuggestRequest())
        );

        assertEquals("AI 音乐补全当前已在管理后台关闭", exception.getMessage());
        verifyNoInteractions(executor);
    }

    @Test
    void shouldReturnMusicSuggestionFromScenarioExecutor() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("""
                {
                  "candidates": [
                    {
                      "title": "夜に駆ける",
                      "artist": "YOASOBI",
                      "album": "THE BOOK",
                      "releaseYear": 2019,
                      "language": "日语",
                      "genre": "J-pop",
                      "tags": ["日系", "夜读", "温柔"],
                      "recommendation": "像把夜色轻轻折进耳机里。",
                      "moodText": "夜风和霓虹。",
                      "lyricSource": "官方歌词",
                      "confidence": "high",
                      "matchReason": "歌名唯一性较高。"
                    }
                  ]
                }
                """);
        AiScenarioExecutor executor = new AiScenarioExecutor(List.of(new MusicTrackSuggestScenarioDefinition(llmClient, requestFactory())));
        FeatureToggleService featureToggleService = mock(FeatureToggleService.class);
        when(featureToggleService.isEnabled(RuntimeFeatureEnum.AI_MUSIC_ASSIST)).thenReturn(true);
        LlmMusicTrackAiSuggestServiceImpl service = new LlmMusicTrackAiSuggestServiceImpl(executor, featureToggleService);
        MusicTrackAiSuggestRequest request = new MusicTrackAiSuggestRequest();
        request.setTitle("夜に駆ける");

        MusicTrackAiSuggestResponse response = service.suggest(request);

        assertEquals(1, response.getCandidates().size());
        assertEquals("YOASOBI", response.getCandidates().get(0).getArtist());
        assertEquals("THE BOOK", response.getCandidates().get(0).getAlbum());
        assertEquals(2019, response.getCandidates().get(0).getReleaseYear());
        assertEquals("high", response.getCandidates().get(0).getConfidence());
        assertEquals("歌名唯一性较高。", response.getCandidates().get(0).getMatchReason());
        assertEquals(List.of("日系", "夜读", "温柔"), response.getCandidates().get(0).getTags());

        org.mockito.ArgumentCaptor<LlmTextRequest> captor = org.mockito.ArgumentCaptor.forClass(LlmTextRequest.class);
        verify(llmClient).generateText(captor.capture());
        assertEquals("https://db.example/v1", captor.getValue().baseUrl());
        assertEquals("gpt-db", captor.getValue().model());
        assertEquals("sk-db", captor.getValue().apiKey());
    }

    private AiLlmRequestFactory requestFactory() {
        AiAdminConfigDTO config = new AiAdminConfigDTO();
        config.getLlm().setEnabled(true);
        config.getLlm().setBaseUrl("https://db.example/v1");
        config.getLlm().setModel("gpt-db");
        config.getLlm().setApiKey("sk-db");
        config.getLlm().setApiStyle("chat-completions");
        config.getLlm().setTemperature(0.2);
        config.getLlm().setMaxTokens(512);
        config.getLlm().setTimeoutSeconds(30);
        AiConfigService aiConfigService = mock(AiConfigService.class);
        when(aiConfigService.getEffectiveConfig()).thenReturn(config);
        return new AiLlmRequestFactory(aiConfigService);
    }
}
