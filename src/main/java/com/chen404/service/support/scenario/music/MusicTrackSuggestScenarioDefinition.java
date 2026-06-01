package com.chen404.service.support.scenario.music;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.chen404.service.support.AiLlmRequestFactory;
import com.chen404.service.support.LlmClient;
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

    private static final String SYSTEM_INSTRUCTION = "You are a careful music metadata assistant and music recommendation editor for a personal Sakura Radio CMS. Return valid JSON only.";
    private static final String CODE_FENCE_PREFIX = "```";
    private static final String JSON_FENCE_PATTERN = "^```(?:json)?\\s*";
    private static final String JSON_FENCE_SUFFIX_PATTERN = "\\s*```$";
    private static final String EMPTY_TEXT = "";
    private static final int MIN_RELEASE_YEAR = 1900;
    private static final int MAX_TAG_COUNT = 5;
    private static final int DEFAULT_CANDIDATE_COUNT = 5;
    private static final int MAX_CANDIDATE_COUNT = 5;
    private static final int MAX_RECOMMENDATION_LENGTH = 32;
    private static final int MAX_MOOD_TEXT_LENGTH = 160;
    private static final int MAX_MATCH_REASON_LENGTH = 120;
    private static final int MAX_LYRIC_CONTEXT_LINES = 40;
    private static final int MAX_LYRIC_CONTEXT_LENGTH = 2200;
    private static final char JSON_OBJECT_OPEN = '{';
    private static final char JSON_OBJECT_CLOSE = '}';
    private static final char JSON_ARRAY_OPEN = '[';
    private static final char JSON_ARRAY_CLOSE = ']';

    private final LlmClient llmClient;
    private final AiLlmRequestFactory aiLlmRequestFactory;

    public MusicTrackSuggestScenarioDefinition(LlmClient llmClient, AiLlmRequestFactory aiLlmRequestFactory) {
        this.llmClient = llmClient;
        this.aiLlmRequestFactory = aiLlmRequestFactory;
    }

    @Override
    public AiScenarioCode code() {
        return AiScenarioCode.MUSIC_TRACK_SUGGEST;
    }

    @Override
    public AiScenarioResult<MusicTrackSuggestScenarioResult> execute(AiScenarioRequest<MusicTrackSuggestScenarioRequest> request) {
        String outputText = llmClient.generateText(aiLlmRequestFactory.buildTextRequest(
                SYSTEM_INSTRUCTION,
                buildPrompt(request.payload())
        ));
        return AiScenarioResult.of(parseResponse(outputText, normalizeLimit(request.payload().limit())));
    }

    private String buildPrompt(MusicTrackSuggestScenarioRequest request) {
        int limit = normalizeLimit(request.limit());
        StringBuilder builder = new StringBuilder();
        builder.append("Given a song title and optional known fields, find likely song metadata candidates for a music library form.\n");
        builder.append("For recommendation writing, act like a seasoned music listener and editor who can explain why this exact song is worth replaying.\n");
        builder.append("Return up to ").append(limit).append(" candidates as one JSON object with exactly this shape: ");
        builder.append("{\"candidates\":[{\"title\":string,\"artist\":string|null,\"album\":string|null,\"releaseYear\":number|null,\"language\":string|null,\"genre\":string|null,\"tags\":string[],\"recommendation\":string,\"moodText\":string,\"lyricSource\":string|null,\"confidence\":\"high|medium|low\",\"matchReason\":string}]}.\n");
        builder.append("Rules:\n");
        builder.append("1. Use null for uncertain factual fields instead of guessing aggressively.\n");
        builder.append("2. Do not provide audio URLs, cover URLs, or copyrighted lyrics.\n");
        builder.append("3. recommendation must be original Chinese copy, no markdown, 15 to 25 Chinese characters, one concise and touching sentence.\n");
        builder.append("   Write from a music expert's listening perspective, but keep only one concrete song-specific hook from vocal delivery, arrangement, melody, rhythm, lyrical theme, emotional arc, version context, or listening scene.\n");
        builder.append("   Make it instantly readable and emotionally precise. Avoid generic template phrases like 把心事轻轻唱开, 适合夜深时反复回味, 值得单曲循环, or any copy that could fit almost any ballad.\n");
        builder.append("4. tags must be 3 to 5 short Chinese tags or recognizable genre labels.\n");
        builder.append("5. releaseYear must be an integer year between 1900 and ").append(Year.now().getValue()).append(", or null when uncertain.\n");
        builder.append("6. lyricSource should be a short source hint like 官方歌词 / 网易云音乐 / 手动校对, or null.\n\n");
        builder.append("7. If the same title has different singers, originals, covers, live versions, anime versions, or regional versions, return them as separate candidates.\n");
        builder.append("8. Rank the most likely candidate first. Use confidence high only when the known fields strongly match.\n");
        builder.append("9. If optional known fields narrow the match, filter candidates by those fields.\n");
        builder.append("10. moodText should be concise and display-ready. When Lyrics content is provided, choose 1 to 3 striking lyric lines directly from that provided lyrics content, preserve the original wording, do not paraphrase, do not add quotation marks, and join multiple lines with ' / '.\n");
        builder.append("11. If Lyrics content is empty or unusable, moodText may fall back to one original Chinese mood sentence.\n\n");
        builder.append("Song title:\n").append(trim(request.title())).append("\n");
        if (StringUtils.hasText(request.artist())) {
            builder.append("Known artist:\n").append(trim(request.artist())).append("\n");
        }
        if (StringUtils.hasText(request.album())) {
            builder.append("Known album:\n").append(trim(request.album())).append("\n");
        }
        if (request.releaseYear() != null) {
            builder.append("Known release year:\n").append(request.releaseYear()).append("\n");
        }
        if (StringUtils.hasText(request.language())) {
            builder.append("Known language:\n").append(trim(request.language())).append("\n");
        }
        if (StringUtils.hasText(request.genre())) {
            builder.append("Known genre:\n").append(trim(request.genre())).append("\n");
        }
        if (StringUtils.hasText(request.lyrics())) {
            builder.append("Lyrics content:\n").append(normalizeLyricContext(request.lyrics())).append("\n");
        }
        return builder.toString();
    }

    private MusicTrackSuggestScenarioResult parseResponse(String outputText, int limit) {
        JSONObject payload = parseJsonObject(outputText);
        JSONArray rawCandidates = payload.getJSONArray("candidates");
        if (rawCandidates == null) {
            rawCandidates = new JSONArray();
            rawCandidates.add(payload);
        }

        List<MusicTrackSuggestCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < rawCandidates.size() && candidates.size() < limit; i++) {
            JSONObject rawCandidate = rawCandidates.getJSONObject(i);
            MusicTrackSuggestCandidate candidate = normalizeCandidate(rawCandidate);
            if (!isEmptyCandidate(candidate)) {
                candidates.add(candidate);
            }
        }
        return new MusicTrackSuggestScenarioResult(candidates);
    }

    private JSONObject parseJsonObject(String outputText) {
        String jsonText = stripCodeFence(outputText);
        try {
            return JSON.parseObject(jsonText);
        } catch (JSONException ex) {
            return JSON.parseObject(repairJsonStructure(jsonText));
        }
    }

    /**
     * 尝试修复 LLM 常见的括号闭合错误，避免单个候选末尾把 `}` 写成 `]` 直接导致整段失败。
     */
    private String repairJsonStructure(String jsonText) {
        if (!StringUtils.hasText(jsonText)) {
            return EMPTY_TEXT;
        }
        StringBuilder repaired = new StringBuilder(jsonText.length() + 8);
        List<Character> stack = new ArrayList<>();
        boolean inString = false;
        boolean escaping = false;
        for (int i = 0; i < jsonText.length(); i++) {
            char current = jsonText.charAt(i);
            if (escaping) {
                repaired.append(current);
                escaping = false;
                continue;
            }
            if (current == '\\') {
                repaired.append(current);
                escaping = true;
                continue;
            }
            if (current == '"') {
                repaired.append(current);
                inString = !inString;
                continue;
            }
            if (inString) {
                repaired.append(current);
                continue;
            }
            if (current == JSON_OBJECT_OPEN || current == JSON_ARRAY_OPEN) {
                stack.add(current);
                repaired.append(current);
                continue;
            }
            if (current == JSON_OBJECT_CLOSE || current == JSON_ARRAY_CLOSE) {
                char normalized = normalizeClosingChar(current, stack);
                if (normalized != '\0') {
                    repaired.append(normalized);
                }
                continue;
            }
            repaired.append(current);
        }
        for (int i = stack.size() - 1; i >= 0; i--) {
            repaired.append(stack.get(i) == JSON_OBJECT_OPEN ? JSON_OBJECT_CLOSE : JSON_ARRAY_CLOSE);
        }
        return repaired.toString();
    }

    private char normalizeClosingChar(char current, List<Character> stack) {
        if (stack.isEmpty()) {
            return '\0';
        }
        char lastOpen = stack.get(stack.size() - 1);
        if (matches(lastOpen, current)) {
            stack.remove(stack.size() - 1);
            return current;
        }
        char repaired = lastOpen == JSON_OBJECT_OPEN ? JSON_OBJECT_CLOSE : JSON_ARRAY_CLOSE;
        stack.remove(stack.size() - 1);
        return repaired;
    }

    private boolean matches(char openChar, char closeChar) {
        return (openChar == JSON_OBJECT_OPEN && closeChar == JSON_OBJECT_CLOSE)
                || (openChar == JSON_ARRAY_OPEN && closeChar == JSON_ARRAY_CLOSE);
    }

    private MusicTrackSuggestCandidate normalizeCandidate(JSONObject payload) {
        return new MusicTrackSuggestCandidate(
                normalizeText(payload.getString("title")),
                normalizeText(payload.getString("artist")),
                normalizeText(payload.getString("album")),
                normalizeReleaseYear(payload.getInteger("releaseYear")),
                normalizeText(payload.getString("language")),
                normalizeText(payload.getString("genre")),
                normalizeTags(payload.getJSONArray("tags")),
                normalizeLimitedText(payload.getString("recommendation"), MAX_RECOMMENDATION_LENGTH),
                normalizeLimitedText(payload.getString("moodText"), MAX_MOOD_TEXT_LENGTH),
                normalizeText(payload.getString("lyricSource")),
                normalizeConfidence(payload.getString("confidence")),
                normalizeLimitedText(payload.getString("matchReason"), MAX_MATCH_REASON_LENGTH)
        );
    }

    private boolean isEmptyCandidate(MusicTrackSuggestCandidate candidate) {
        return !StringUtils.hasText(candidate.title())
                && !StringUtils.hasText(candidate.artist())
                && !StringUtils.hasText(candidate.album())
                && candidate.releaseYear() == null
                && !StringUtils.hasText(candidate.language())
                && !StringUtils.hasText(candidate.genre())
                && (candidate.tags() == null || candidate.tags().isEmpty())
                && !StringUtils.hasText(candidate.recommendation())
                && !StringUtils.hasText(candidate.moodText())
                && !StringUtils.hasText(candidate.lyricSource());
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

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_CANDIDATE_COUNT;
        }
        return Math.min(limit, MAX_CANDIDATE_COUNT);
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

    private String normalizeConfidence(String confidence) {
        String normalized = normalizeText(confidence).toLowerCase();
        if ("high".equals(normalized) || "medium".equals(normalized) || "low".equals(normalized)) {
            return normalized;
        }
        return "low";
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ");
    }

    private String normalizeLyricContext(String lyrics) {
        if (!StringUtils.hasText(lyrics)) {
            return EMPTY_TEXT;
        }
        String normalized = lyrics.replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = new ArrayList<>();
        for (String line : normalized.split("\n")) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            lines.add(line.trim());
            if (lines.size() >= MAX_LYRIC_CONTEXT_LINES) {
                break;
            }
        }
        String excerpt = String.join("\n", lines);
        if (excerpt.length() <= MAX_LYRIC_CONTEXT_LENGTH) {
            return excerpt;
        }
        return excerpt.substring(0, MAX_LYRIC_CONTEXT_LENGTH).trim();
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
