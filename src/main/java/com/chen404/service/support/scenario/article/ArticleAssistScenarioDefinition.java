package com.chen404.service.support.scenario.article;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文章摘要与标签生成场景定义。
 */
@Component
public class ArticleAssistScenarioDefinition implements AiScenarioDefinition<ArticleAssistScenarioRequest, ArticleAssistScenarioResult> {

    private static final String SYSTEM_INSTRUCTION = "You are an assistant for a Chinese technical blog CMS. Return valid JSON only.";
    private static final int MAX_INPUT_CHARS = 12_000;
    private static final int MAX_SUMMARY_LENGTH = 180;
    private static final int PREFERRED_TAG_COUNT = 3;
    private static final int MAX_TAG_COUNT = 5;
    private static final String OUTPUT_FIELD_SUMMARY = "summary";
    private static final String OUTPUT_FIELD_TAGS = "tags";
    private static final String CODE_FENCE_PREFIX = "```";
    private static final String JSON_FENCE_PATTERN = "^```(?:json)?\\s*";
    private static final String JSON_FENCE_SUFFIX_PATTERN = "\\s*```$";
    private static final String EMPTY_TEXT = "";

    private final LlmClient llmClient;

    public ArticleAssistScenarioDefinition(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public AiScenarioCode code() {
        return AiScenarioCode.ARTICLE_ASSIST;
    }

    @Override
    public AiScenarioResult<ArticleAssistScenarioResult> execute(AiScenarioRequest<ArticleAssistScenarioRequest> request) {
        ArticleAssistScenarioRequest payload = request.payload();
        String outputText = llmClient.generateText(LlmTextRequest.of(
                SYSTEM_INSTRUCTION,
                buildPrompt(payload)
        ));
        return AiScenarioResult.of(parseResponse(outputText));
    }

    private ArticleAssistScenarioResult parseResponse(String outputText) {
        JSONObject payload = JSON.parseObject(stripCodeFence(outputText));
        return new ArticleAssistScenarioResult(
                normalizeSummary(payload.getString(OUTPUT_FIELD_SUMMARY)),
                normalizeTags(payload.getJSONArray(OUTPUT_FIELD_TAGS))
        );
    }

    private String buildPrompt(ArticleAssistScenarioRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("Please analyze the following blog article draft and return a JSON object with fields summary and tags.\n");
        builder.append("Requirements:\n");
        builder.append("1. summary must be Chinese, natural, no markdown, no leading label, at most ")
                .append(MAX_SUMMARY_LENGTH)
                .append(" characters.\n");
        builder.append("2. tags should prefer exactly ")
                .append(PREFERRED_TAG_COUNT)
                .append(" concise Chinese tags. Only return 4 to 5 tags when the article clearly contains multiple equally important themes.\n");
        builder.append("3. tags must be sorted by relevance from highest to lowest.\n");
        builder.append("4. Prefer technology, framework, architecture, database, deployment, debugging, or domain terms from the article.\n");
        builder.append("5. Avoid generic tags like 技术, 博客, 学习 unless the draft strongly requires them.\n");
        appendRegenerateConstraints(builder, request);
        if (StringUtils.hasText(request.title())) {
            builder.append("Title:\n").append(request.title().trim()).append("\n\n");
        }
        builder.append("Content:\n").append(truncateContent(request.content()));
        return builder.toString();
    }

    private void appendRegenerateConstraints(StringBuilder builder, ArticleAssistScenarioRequest request) {
        if (!request.regenerate()) {
            return;
        }

        builder.append("6. This is a regeneration request. Provide a different alternative from the current result while staying faithful to the article.\n");

        if (StringUtils.hasText(request.currentSummary())) {
            builder.append("Current summary to avoid repeating:\n")
                    .append(request.currentSummary().trim())
                    .append("\n");
        }

        List<String> currentTags = normalizeCurrentTags(request.currentTags());
        if (!currentTags.isEmpty()) {
            builder.append("Current tags to avoid repeating exactly:\n")
                    .append(String.join(", ", currentTags))
                    .append("\n");
        }
    }

    private List<String> normalizeCurrentTags(List<String> currentTags) {
        if (currentTags == null || currentTags.isEmpty()) {
            return Collections.emptyList();
        }
        return currentTags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private String truncateContent(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.trim();
        if (normalized.length() <= MAX_INPUT_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_INPUT_CHARS);
    }

    private String stripCodeFence(String text) {
        String trimmed = text == null ? EMPTY_TEXT : text.trim();
        if (trimmed.startsWith(CODE_FENCE_PREFIX)) {
            trimmed = trimmed.replaceFirst(JSON_FENCE_PATTERN, EMPTY_TEXT);
            trimmed = trimmed.replaceFirst(JSON_FENCE_SUFFIX_PATTERN, EMPTY_TEXT);
        }
        return trimmed;
    }

    private String normalizeSummary(String summary) {
        if (!StringUtils.hasText(summary)) {
            return "";
        }
        String trimmed = summary.trim().replaceAll("\\s+", " ");
        return trimmed.length() > MAX_SUMMARY_LENGTH ? trimmed.substring(0, MAX_SUMMARY_LENGTH) : trimmed;
    }

    private List<String> normalizeTags(JSONArray tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (int i = 0; i < tags.size(); i++) {
            String tag = tags.getString(i);
            if (!StringUtils.hasText(tag)) {
                continue;
            }

            String cleanTag = tag.trim().replaceAll("^#+", "").replaceAll("[,，。；;]+$", "");
            if (StringUtils.hasText(cleanTag)) {
                normalized.add(cleanTag);
            }
            if (normalized.size() >= MAX_TAG_COUNT) {
                break;
            }
        }
        return new ArrayList<>(normalized);
    }
}
