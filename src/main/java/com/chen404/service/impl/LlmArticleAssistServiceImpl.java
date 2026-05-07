package com.chen404.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chen404.config.LlmProperties;
import com.chen404.domain.dto.AiArticleAssistRequest;
import com.chen404.domain.dto.AiArticleAssistResponse;
import com.chen404.service.AiArticleAssistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 通用 LLM 文章助手：
 * - 默认以 OpenAI-compatible 协议工作
 * - 通过配置切换 chat/completions 或 responses
 * - 业务层只依赖统一的摘要 + 标签结果
 */
@Service
public class LlmArticleAssistServiceImpl implements AiArticleAssistService {

    private static final Logger log = LoggerFactory.getLogger(LlmArticleAssistServiceImpl.class);

    /** 上游返回为空时，给编辑页留出的安全兜底摘要长度。 */
    private static final int MAX_INPUT_CHARS = 12_000;
    /** 摘要长度上限，避免写入过长内容。 */
    private static final int MAX_SUMMARY_LENGTH = 180;
    /** 标签建议数量上限，避免一次推荐过多。 */
    private static final int MAX_TAG_COUNT = 5;

    /** OpenAI 兼容风格：Chat Completions。 */
    private static final String STYLE_CHAT_COMPLETIONS = "chat-completions";
    /** OpenAI 兼容风格：Responses。 */
    private static final String STYLE_RESPONSES = "responses";

    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String AUTHORIZATION_PREFIX = "Bearer ";
    private static final String OUTPUT_FIELD_TEXT = "output_text";
    private static final String OUTPUT_FIELD_CHOICES = "choices";
    private static final String OUTPUT_FIELD_OUTPUT = "output";
    private static final String OUTPUT_FIELD_MESSAGE = "message";
    private static final String OUTPUT_FIELD_CONTENT = "content";
    private static final String OUTPUT_FIELD_TEXT_NODE = "text";
    private static final String OUTPUT_FIELD_ROLE = "role";
    private static final String OUTPUT_FIELD_MODEL = "model";
    private static final String OUTPUT_FIELD_MESSAGES = "messages";
    private static final String OUTPUT_FIELD_TEMPERATURE = "temperature";
    private static final String OUTPUT_FIELD_MAX_TOKENS = "max_tokens";
    private static final String OUTPUT_FIELD_MAX_OUTPUT_TOKENS = "max_output_tokens";
    private static final String OUTPUT_FIELD_INSTRUCTIONS = "instructions";
    private static final String OUTPUT_FIELD_INPUT = "input";
    private static final String OUTPUT_FIELD_SUMMARY = "summary";
    private static final String OUTPUT_FIELD_TAGS = "tags";
    private static final String CODE_FENCE_PREFIX = "```";
    private static final String JSON_FENCE_PATTERN = "^```(?:json)?\\s*";
    private static final String JSON_FENCE_SUFFIX_PATTERN = "\\s*```$";
    private static final String EMPTY_TEXT = "";
    private static final String DEFAULT_ERROR_PREFIX = "LLM 服务调用失败：";
    private static final String EMPTY_RESULT_ERROR = "LLM 服务返回空结果";
    private static final String MISSING_TEXT_ERROR = "LLM 响应缺少文本内容";
    private static final int MIN_TIMEOUT_SECONDS = 5;

    private final LlmProperties llmProperties;
    private final HttpClient httpClient;

    public LlmArticleAssistServiceImpl(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(llmProperties.getTimeoutSeconds(), MIN_TIMEOUT_SECONDS)))
                .build();
    }

    @Override
    public AiArticleAssistResponse generateAssist(AiArticleAssistRequest request) {
        validateConfiguration();

        String content = request.getContent().trim();
        String prompt = buildPrompt(request.getTitle(), content);
        HttpRequest httpRequest = buildRequest(prompt);

        try {
            log.info("开始调用 LLM 生成文章辅助信息，model={}, style={}", llmProperties.getModel(), normalizeApiStyle(llmProperties.getApiStyle()));
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("LLM 接口返回非成功状态码，status={}, body={}", response.statusCode(), response.body());
                throw new IllegalStateException(DEFAULT_ERROR_PREFIX + response.statusCode());
            }

            AiArticleAssistResponse result = parseResponse(response.body());
            if (!StringUtils.hasText(result.getSummary()) && result.getTags().isEmpty()) {
                throw new IllegalStateException(EMPTY_RESULT_ERROR);
            }
            int summaryLength = result.getSummary() == null ? 0 : result.getSummary().length();
            log.info("LLM 生成完成，summaryLength={}, tagCount={}", summaryLength, result.getTags().size());
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("LLM 调用被中断", e);
            throw new IllegalStateException(DEFAULT_ERROR_PREFIX + "请求被中断", e);
        } catch (IOException e) {
            log.error("LLM 调用发生 I/O 异常", e);
            throw new IllegalStateException(DEFAULT_ERROR_PREFIX + "网络异常", e);
        }
    }

    private void validateConfiguration() {
        if (!llmProperties.isEnabled()) {
            throw new IllegalStateException("当前环境未开启 LLM 能力");
        }
        if (!StringUtils.hasText(llmProperties.getApiKey())) {
            throw new IllegalStateException("LLM_API_KEY 未配置");
        }
        if (!StringUtils.hasText(llmProperties.getModel())) {
            throw new IllegalStateException("LLM_MODEL 未配置");
        }
    }

    private HttpRequest buildRequest(String prompt) {
        String apiStyle = normalizeApiStyle(llmProperties.getApiStyle());
        boolean useResponses = STYLE_RESPONSES.equals(apiStyle);
        String endpoint = useResponses ? llmProperties.getResponsesPath() : llmProperties.getChatCompletionsPath();
        JSONObject body = useResponses ? buildResponsesBody(prompt) : buildChatCompletionsBody(prompt);

        return HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl(llmProperties.getBaseUrl()) + normalizePath(endpoint)))
                .timeout(Duration.ofSeconds(Math.max(llmProperties.getTimeoutSeconds(), MIN_TIMEOUT_SECONDS)))
                .header("Authorization", AUTHORIZATION_PREFIX + llmProperties.getApiKey().trim())
                .header("Content-Type", JSON_CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .build();
    }

    private JSONObject buildChatCompletionsBody(String prompt) {
        JSONObject system = new JSONObject();
        system.put(OUTPUT_FIELD_ROLE, ROLE_SYSTEM);
        system.put(OUTPUT_FIELD_CONTENT, "You are an assistant for a Chinese technical blog CMS. Return valid JSON only.");

        JSONObject user = new JSONObject();
        user.put(OUTPUT_FIELD_ROLE, ROLE_USER);
        user.put(OUTPUT_FIELD_CONTENT, prompt);

        JSONObject body = new JSONObject();
        body.put(OUTPUT_FIELD_MODEL, llmProperties.getModel());
        body.put(OUTPUT_FIELD_MESSAGES, List.of(system, user));
        body.put(OUTPUT_FIELD_TEMPERATURE, llmProperties.getTemperature());
        body.put(OUTPUT_FIELD_MAX_TOKENS, llmProperties.getMaxTokens());
        return body;
    }

    private JSONObject buildResponsesBody(String prompt) {
        JSONObject body = new JSONObject();
        body.put(OUTPUT_FIELD_MODEL, llmProperties.getModel());
        body.put(OUTPUT_FIELD_INSTRUCTIONS, "You are an assistant for a Chinese technical blog CMS. Return valid JSON only.");
        body.put(OUTPUT_FIELD_INPUT, prompt);
        body.put(OUTPUT_FIELD_TEMPERATURE, llmProperties.getTemperature());
        body.put(OUTPUT_FIELD_MAX_OUTPUT_TOKENS, llmProperties.getMaxTokens());
        return body;
    }

    private AiArticleAssistResponse parseResponse(String rawBody) {
        try {
            JSONObject root = JSON.parseObject(rawBody);
            String outputText = extractOutputText(root);
            if (!StringUtils.hasText(outputText)) {
                log.warn("LLM 响应缺少可解析文本，原始响应={}", rawBody);
                throw new IllegalStateException(MISSING_TEXT_ERROR);
            }

            JSONObject payload = JSON.parseObject(stripCodeFence(outputText));
            AiArticleAssistResponse response = new AiArticleAssistResponse();
            response.setSummary(normalizeSummary(payload.getString(OUTPUT_FIELD_SUMMARY)));
            response.setTags(normalizeTags(payload.getJSONArray(OUTPUT_FIELD_TAGS)));
            return response;
        } catch (RuntimeException e) {
            log.warn("LLM 响应解析失败，原始响应={}", rawBody, e);
            throw new IllegalStateException("LLM 响应格式不合法", e);
        }
    }

    private String extractOutputText(JSONObject root) {
        String outputText = root.getString(OUTPUT_FIELD_TEXT);
        if (StringUtils.hasText(outputText)) {
            return outputText;
        }

        JSONArray choices = root.getJSONArray(OUTPUT_FIELD_CHOICES);
        if (choices != null && !choices.isEmpty()) {
            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice.getJSONObject(OUTPUT_FIELD_MESSAGE);
            if (message != null && StringUtils.hasText(message.getString(OUTPUT_FIELD_CONTENT))) {
                return message.getString(OUTPUT_FIELD_CONTENT);
            }
        }

        JSONArray output = root.getJSONArray(OUTPUT_FIELD_OUTPUT);
        if (output != null) {
            for (int i = 0; i < output.size(); i++) {
                JSONObject item = output.getJSONObject(i);
                JSONArray content = item.getJSONArray(OUTPUT_FIELD_CONTENT);
                if (content == null) {
                    continue;
                }
                for (int j = 0; j < content.size(); j++) {
                    JSONObject part = content.getJSONObject(j);
                    String text = part.getString(OUTPUT_FIELD_TEXT_NODE);
                    if (StringUtils.hasText(text)) {
                        return text;
                    }
                }
            }
        }

        return null;
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

    private String buildPrompt(String title, String content) {
        StringBuilder builder = new StringBuilder();
        builder.append("Please analyze the following blog article draft and return a JSON object with fields summary and tags.\n");
        builder.append("Requirements:\n");
        builder.append("1. summary must be Chinese, natural, no markdown, no leading label, at most ")
                .append(MAX_SUMMARY_LENGTH)
                .append(" characters.\n");
        builder.append("2. tags must be 3 to 5 concise Chinese tags suitable for a blog CMS.\n");
        builder.append("3. Prefer technology, framework, architecture, database, deployment, debugging, or domain terms from the article.\n");
        builder.append("4. Avoid generic tags like 技术, 博客, 学习 unless the draft strongly requires them.\n");
        if (StringUtils.hasText(title)) {
            builder.append("Title:\n").append(title.trim()).append("\n\n");
        }
        builder.append("Content:\n").append(truncateContent(content));
        return builder.toString();
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

    private String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private String normalizeApiStyle(String apiStyle) {
        if (!StringUtils.hasText(apiStyle)) {
            return STYLE_CHAT_COMPLETIONS;
        }
        String normalized = apiStyle.trim().toLowerCase();
        if (STYLE_RESPONSES.equals(normalized)) {
            return STYLE_RESPONSES;
        }
        if (!STYLE_CHAT_COMPLETIONS.equals(normalized)) {
            log.warn("未知的 LLM_API_STYLE 配置，已回退到 chat-completions，value={}", apiStyle);
        }
        return STYLE_CHAT_COMPLETIONS;
    }
}
