package com.chen404.service.support.scenario.music;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chen404.service.support.LlmClient;
import com.chen404.service.support.LlmTextRequest;
import com.chen404.service.support.scenario.AiScenarioCode;
import com.chen404.service.support.scenario.AiScenarioDefinition;
import com.chen404.service.support.scenario.AiScenarioRequest;
import com.chen404.service.support.scenario.AiScenarioResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 音乐曲目信息补全场景定义。
 */
@Component
public class MusicTrackSuggestScenarioDefinition implements AiScenarioDefinition<MusicTrackSuggestScenarioRequest, MusicTrackSuggestScenarioResult> {

    private static final String SYSTEM_INSTRUCTION = "You are a careful music metadata assistant for a personal Sakura Radio CMS. Return valid JSON only.";
    private static final String CODE_FENCE_PREFIX = "```";
    private static final String JSON_FENCE_PATTERN = "^```(?:json)?\\s*";
    private static final String JSON_FENCE_SUFFIX_PATTERN = "\\s*```$";
    private static final String EMPTY_TEXT = "";
    private static final int MIN_RELEASE_YEAR = 1900;
    private static final int MAX_TAG_COUNT = 5;
    private static final int MAX_RECOMMENDATION_LENGTH = 140;
    private static final int MAX_MOOD_TEXT_LENGTH = 80;

    private final LlmClient llmClient;

    public MusicTrackSuggestScenarioDefinition(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public AiScenarioCode code() {
        return AiScenarioCode.MUSIC_TRACK_SUGGEST;
    }

    @Override
    public AiScenarioResult<MusicTrackSuggestScenarioResult> execute(AiScenarioRequest<MusicTrackSuggestScenarioRequest> request) {
        String outputText = llmClient.generateText(LlmTextRequest.of(
                SYSTEM_INSTRUCTION,
                buildPrompt(request.payload())
        ));
        return AiScenarioResult.of(parseResponse(outputText));
    }

    private String buildPrompt(MusicTrackSuggestScenarioRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("Given a song title, infer safe, likely metadata for a music library form.\n");
        builder.append("Return one JSON object with exactly these fields: title, artist, album, releaseYear, language, genre, tags, recommendation, moodText, lyricSource.\n");
        builder.append("Rules:\n");
        builder.append("1. Use null for uncertain factual fields instead of guessing aggressively.\n");
        builder.append("2. Do not provide audio URLs, cover URLs, or copyrighted lyrics.\n");
        builder.append("3. recommendation and moodText must be original Chinese copy, warm and concise, no markdown.\n");
        builder.append("4. tags must be 3 to 5 short Chinese tags or recognizable genre labels.\n");
        builder.append("5. releaseYear must be an integer year between 1900 and ").append(Year.now().getValue()).append(", or null when uncertain.\n");
        builder.append("6. lyricSource should be a short source hint like 官方歌词 / 网易云音乐 / 手动校对, or null.\n\n");
        builder.append("Song title:\n").append(trim(request.title())).append("\n");
        if (StringUtils.hasText(request.artist())) {
            builder.append("Known artist:\n").append(trim(request.artist())).append("\n");
        }
        return builder.toString();
    }

    private MusicTrackSuggestScenarioResult parseResponse(String outputText) {
        JSONObject payload = JSON.parseObject(stripCodeFence(outputText));
        return new MusicTrackSuggestScenarioResult(
                normalizeText(payload.getString("title")),
                normalizeText(payload.getString("artist")),
                normalizeText(payload.getString("album")),
                normalizeReleaseYear(payload.getInteger("releaseYear")),
                normalizeText(payload.getString("language")),
                normalizeText(payload.getString("genre")),
                normalizeTags(payload.getJSONArray("tags")),
                normalizeLimitedText(payload.getString("recommendation"), MAX_RECOMMENDATION_LENGTH),
                normalizeLimitedText(payload.getString("moodText"), MAX_MOOD_TEXT_LENGTH),
                normalizeText(payload.getString("lyricSource"))
        );
    }

    private String stripCodeFence(String text) {
        String trimmed = text == null ? EMPTY_TEXT : text.trim();
        if (trimmed.startsWith(CODE_FENCE_PREFIX)) {
            trimmed = trimmed.replaceFirst(JSON_FENCE_PATTERN, EMPTY_TEXT);
            trimmed = trimmed.replaceFirst(JSON_FENCE_SUFFIX_PATTERN, EMPTY_TEXT);
        }
        return trimmed;
    }

    private Integer normalizeReleaseYear(Integer releaseYear) {
        if (releaseYear == null) {
            return null;
        }
        int currentYear = Year.now().getValue();
        if (releaseYear < MIN_RELEASE_YEAR || releaseYear > currentYear) {
            return null;
        }
        return releaseYear;
    }

    private List<String> normalizeTags(JSONArray tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (int i = 0; i < tags.size(); i++) {
            String tag = normalizeText(tags.getString(i));
            if (StringUtils.hasText(tag)) {
                normalized.add(tag.replaceAll("^#+", "").replaceAll("[,，。；;]+$", ""));
            }
            if (normalized.size() >= MAX_TAG_COUNT) {
                break;
            }
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeLimitedText(String text, int maxLength) {
        String normalized = normalizeText(text);
        if (!StringUtils.hasText(normalized) || normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ");
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
