package com.chen404.service.support.scenario.music;

import com.chen404.domain.dto.AiAdminConfigDTO;
import com.chen404.service.AiConfigService;
import com.chen404.service.support.AiLlmRequestFactory;
import com.chen404.service.support.LlmClient;
import com.chen404.service.support.LlmTextRequest;
import com.chen404.service.support.scenario.AiScenarioCode;
import com.chen404.service.support.scenario.AiScenarioRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MusicTrackSuggestScenarioDefinitionTest {

    @Test
    void shouldBuildPromptAndParseCandidates() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("""
                ```json
                {
                  "candidates": [
                    {
                      "title": "晴天",
                      "artist": "周杰伦",
                      "album": "叶惠美",
                      "releaseYear": 2003,
                      "language": "中文",
                      "genre": "Mandopop",
                      "tags": ["华语", "青春", "晴天", "华语", "温柔", "额外"],
                      "recommendation": "像把雨后的课桌和风一起放进耳机里。",
                      "moodText": "青春、雨后和一点点遗憾。",
                      "lyricSource": "官方歌词",
                      "confidence": "high",
                      "matchReason": "最知名的《晴天》版本，歌名完全匹配。"
                    },
                    {
                      "title": "晴天",
                      "artist": "汪苏泷",
                      "album": null,
                      "releaseYear": null,
                      "language": "中文",
                      "genre": "翻唱",
                      "tags": ["翻唱", "华语"],
                      "recommendation": "更轻一点的晴天，也适合慢慢收藏。",
                      "moodText": "轻声翻唱。",
                      "lyricSource": null,
                      "confidence": "medium",
                      "matchReason": "可能是翻唱版本，需要歌手确认。"
                    }
                  ]
                }
                ```
                """);

        MusicTrackSuggestScenarioDefinition definition = new MusicTrackSuggestScenarioDefinition(llmClient, requestFactory());
        MusicTrackSuggestScenarioResult result = definition.execute(
                AiScenarioRequest.of(
                        AiScenarioCode.MUSIC_TRACK_SUGGEST,
                        new MusicTrackSuggestScenarioRequest("晴天", "周杰伦", "", null, "", "", 5)
                )
        ).data();

        assertEquals(2, result.candidates().size());
        MusicTrackSuggestCandidate first = result.candidates().get(0);
        assertEquals("周杰伦", first.artist());
        assertEquals("叶惠美", first.album());
        assertEquals(2003, first.releaseYear());
        assertEquals("中文", first.language());
        assertEquals("Mandopop", first.genre());
        assertEquals("high", first.confidence());
        assertEquals("最知名的《晴天》版本，歌名完全匹配。", first.matchReason());
        assertIterableEquals(List.of("华语", "青春", "晴天", "温柔", "额外"), first.tags());

        ArgumentCaptor<LlmTextRequest> requestCaptor = ArgumentCaptor.forClass(LlmTextRequest.class);
        verify(llmClient).generateText(requestCaptor.capture());
        assertTrue(requestCaptor.getValue().userPrompt().contains("Song title"));
        assertTrue(requestCaptor.getValue().userPrompt().contains("晴天"));
        assertTrue(requestCaptor.getValue().userPrompt().contains("Known artist"));
        assertTrue(requestCaptor.getValue().userPrompt().contains("Return up to 5 candidates"));
        assertTrue(requestCaptor.getValue().userPrompt().contains("Do not provide audio URLs"));
    }

    @Test
    void shouldDropInvalidFutureYear() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("""
                {
                  "candidates": [
                    {
                      "title": "未知歌曲",
                      "artist": null,
                      "album": null,
                      "releaseYear": 3026,
                      "language": null,
                      "genre": null,
                      "tags": [],
                      "recommendation": "保留一点未知感。",
                      "moodText": "",
                      "lyricSource": null,
                      "confidence": "very-high",
                      "matchReason": ""
                    }
                  ]
                }
                """);

        MusicTrackSuggestScenarioDefinition definition = new MusicTrackSuggestScenarioDefinition(llmClient, requestFactory());
        MusicTrackSuggestScenarioResult result = definition.execute(
                AiScenarioRequest.of(
                        AiScenarioCode.MUSIC_TRACK_SUGGEST,
                        new MusicTrackSuggestScenarioRequest("未知歌曲", null, null, null, null, null, null)
                )
        ).data();

        MusicTrackSuggestCandidate candidate = result.candidates().get(0);
        assertNull(candidate.releaseYear());
        assertEquals("保留一点未知感。", candidate.recommendation());
        assertEquals("low", candidate.confidence());
    }

    @Test
    void shouldRepairMismatchedJsonClosersFromLlm() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("""
                {"candidates":[{"title":"红蔷薇白玫瑰","artist":"邓紫棋","album":null,"releaseYear":null,"language":"中文","genre":"流行","tags":["华语流行","女声","抒情","都市情感"],"recommendation":"情绪层层递进，适合夜晚安静聆听。","moodText":"像在爱与自我之间轻轻拉扯，克制里带一点倔强。","lyricSource":null,"confidence":"medium","matchReason":"已提供歌手为邓紫棋，标题与歌手组合指向性较强，但缺少专辑与发行年份等进一步校验信息。"]}]}
                """);

        MusicTrackSuggestScenarioDefinition definition = new MusicTrackSuggestScenarioDefinition(llmClient, requestFactory());
        MusicTrackSuggestScenarioResult result = definition.execute(
                AiScenarioRequest.of(
                        AiScenarioCode.MUSIC_TRACK_SUGGEST,
                        new MusicTrackSuggestScenarioRequest("红蔷薇白玫瑰", "邓紫棋", null, null, null, null, 5)
                )
        ).data();

        assertEquals(1, result.candidates().size());
        MusicTrackSuggestCandidate candidate = result.candidates().get(0);
        assertEquals("红蔷薇白玫瑰", candidate.title());
        assertEquals("邓紫棋", candidate.artist());
        assertEquals("medium", candidate.confidence());
    }

    private AiLlmRequestFactory requestFactory() {
        AiConfigService aiConfigService = mock(AiConfigService.class);
        when(aiConfigService.getEffectiveConfig()).thenReturn(defaultAiConfig());
        return new AiLlmRequestFactory(aiConfigService);
    }

    private AiAdminConfigDTO defaultAiConfig() {
        AiAdminConfigDTO config = new AiAdminConfigDTO();
        config.getLlm().setEnabled(true);
        config.getLlm().setBaseUrl("https://llm.example/v1");
        config.getLlm().setModel("gpt-test");
        config.getLlm().setApiKey("sk-test");
        config.getLlm().setApiStyle("chat-completions");
        config.getLlm().setTemperature(0.2);
        config.getLlm().setMaxTokens(512);
        config.getLlm().setTimeoutSeconds(30);
        return config;
    }
}
