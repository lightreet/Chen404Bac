package com.chen404.service.support.scenario.music;

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
    void shouldBuildPromptAndParseSuggestion() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("""
                ```json
                {
                  "title": "夜に駆ける",
                  "artist": "YOASOBI",
                  "album": "THE BOOK",
                  "releaseYear": 2019,
                  "language": "日语",
                  "genre": "J-pop",
                  "tags": ["日系", "夜读", "J-pop", "日系", "温柔", "额外"],
                  "recommendation": "像把夜色轻轻折进耳机里，适合一个人慢慢听。",
                  "moodText": "夜风、霓虹和一点点心跳。",
                  "lyricSource": "官方歌词"
                }
                ```
                """);

        MusicTrackSuggestScenarioDefinition definition = new MusicTrackSuggestScenarioDefinition(llmClient);
        MusicTrackSuggestScenarioResult result = definition.execute(
                AiScenarioRequest.of(
                        AiScenarioCode.MUSIC_TRACK_SUGGEST,
                        new MusicTrackSuggestScenarioRequest("夜に駆ける", "")
                )
        ).data();

        assertEquals("YOASOBI", result.artist());
        assertEquals("THE BOOK", result.album());
        assertEquals(2019, result.releaseYear());
        assertEquals("日语", result.language());
        assertEquals("J-pop", result.genre());
        assertIterableEquals(List.of("日系", "夜读", "J-pop", "温柔", "额外"), result.tags());

        ArgumentCaptor<LlmTextRequest> requestCaptor = ArgumentCaptor.forClass(LlmTextRequest.class);
        verify(llmClient).generateText(requestCaptor.capture());
        assertTrue(requestCaptor.getValue().userPrompt().contains("Song title"));
        assertTrue(requestCaptor.getValue().userPrompt().contains("夜に駆ける"));
        assertTrue(requestCaptor.getValue().userPrompt().contains("Do not provide audio URLs"));
    }

    @Test
    void shouldDropInvalidFutureYear() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("""
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
                  "lyricSource": null
                }
                """);

        MusicTrackSuggestScenarioDefinition definition = new MusicTrackSuggestScenarioDefinition(llmClient);
        MusicTrackSuggestScenarioResult result = definition.execute(
                AiScenarioRequest.of(
                        AiScenarioCode.MUSIC_TRACK_SUGGEST,
                        new MusicTrackSuggestScenarioRequest("未知歌曲", null)
                )
        ).data();

        assertNull(result.releaseYear());
        assertEquals("保留一点未知感。", result.recommendation());
    }
}
