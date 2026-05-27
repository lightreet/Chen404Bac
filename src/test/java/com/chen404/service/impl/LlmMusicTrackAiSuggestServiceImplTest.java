package com.chen404.service.impl;

import com.chen404.config.AiRuntimeProperties;
import com.chen404.domain.dto.MusicTrackAiSuggestRequest;
import com.chen404.domain.dto.MusicTrackAiSuggestResponse;
import com.chen404.service.support.LlmClient;
import com.chen404.service.support.LlmTextRequest;
import com.chen404.service.support.scenario.AiScenarioExecutor;
import com.chen404.service.support.scenario.music.MusicTrackSuggestScenarioDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmMusicTrackAiSuggestServiceImplTest {

    @Test
    void shouldReturnMusicSuggestionFromScenarioExecutor() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generateText(any(LlmTextRequest.class))).thenReturn("""
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
                  "lyricSource": "官方歌词"
                }
                """);
        AiRuntimeProperties properties = new AiRuntimeProperties();
        AiScenarioExecutor executor = new AiScenarioExecutor(List.of(new MusicTrackSuggestScenarioDefinition(llmClient)));
        LlmMusicTrackAiSuggestServiceImpl service = new LlmMusicTrackAiSuggestServiceImpl(executor, properties);
        MusicTrackAiSuggestRequest request = new MusicTrackAiSuggestRequest();
        request.setTitle("夜に駆ける");

        MusicTrackAiSuggestResponse response = service.suggest(request);

        assertEquals("YOASOBI", response.getArtist());
        assertEquals("THE BOOK", response.getAlbum());
        assertEquals(2019, response.getReleaseYear());
        assertEquals(List.of("日系", "夜读", "温柔"), response.getTags());
    }
}
