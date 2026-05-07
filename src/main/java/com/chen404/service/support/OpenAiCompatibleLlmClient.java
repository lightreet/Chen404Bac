package com.chen404.service.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chen404.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * OpenAI-compatible 文本客户端实现。
 * <p>
 * 当前统一支持 chat/completions 与 responses 两种协议风格，
 * 负责处理配置校验、HTTP 请求构造以及上游文本结果提取。
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);

    private static final String STYLE_CHAT_COMPLETIONS = "chat-completions";
    private static final String STYLE_RESPONSES = "responses";
    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String AUTHORIZATION_PREFIX = "Bearer ";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String FIELD_MODEL = "model";
    private static final String FIELD_MESSAGES = "messages";
    private static final String FIELD_ROLE = "role";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_TEMPERATURE = "temperature";
    private static final String FIELD_MAX_TOKENS = "max_tokens";
    private static final String FIELD_MAX_OUTPUT_TOKENS = "max_output_tokens";
    private static final String FIELD_INSTRUCTIONS = "instructions";
    private static final String FIELD_INPUT = "input";
    private static final String FIELD_OUTPUT_TEXT = "output_text";
    private static final String FIELD_CHOICES = "choices";
    private static final String FIELD_MESSAGE = "message";
    private static final String FIELD_OUTPUT = "output";
    private static final String FIELD_TEXT = "text";
    private static final int MIN_TIMEOUT_SECONDS = 5;
    private static final int MAX_LOG_TEXT_LENGTH = 240;
    private static final String DEFAULT_ERROR_PREFIX = "LLM 服务调用失败：";
    private static final String EMPTY_TEXT_ERROR = "LLM 响应缺少文本内容";

    private final LlmProperties llmProperties;
    private final HttpClient httpClient;

    public OpenAiCompatibleLlmClient(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(resolveTimeoutSeconds()))
                .build();
    }

    @Override
    public String generateText(LlmTextRequest request) {
        validateConfiguration(request);
        HttpRequest httpRequest = buildRequest(request);

        try {
            String apiStyle = normalizeApiStyle(llmProperties.getApiStyle());
            log.info("[LLM_TEXT_REQ] model={} style={}", resolveModel(request), apiStyle);
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[LLM_TEXT_FAIL] model={} status={} body={}", resolveModel(request), response.statusCode(), response.body());
                throw new IllegalStateException(DEFAULT_ERROR_PREFIX + response.statusCode());
            }

            String outputText = extractOutputText(response.body());
            if (!StringUtils.hasText(outputText)) {
                log.warn("[LLM_TEXT_EMPTY] model={} body={}", resolveModel(request), response.body());
                throw new IllegalStateException(EMPTY_TEXT_ERROR);
            }
            log.info("[LLM_TEXT_OK] model={} textLength={} textPreview={}",
                    resolveModel(request), outputText.length(), formatTextForLog(outputText));
            return outputText;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[LLM_TEXT_INTERRUPTED] model={}", resolveModel(request), e);
            throw new IllegalStateException(DEFAULT_ERROR_PREFIX + "请求被中断", e);
        } catch (IOException e) {
            log.error("[LLM_TEXT_IO_FAIL] model={} message={}", resolveModel(request), e.getMessage(), e);
            throw new IllegalStateException(DEFAULT_ERROR_PREFIX + "网络异常", e);
        }
    }

    private void validateConfiguration(LlmTextRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("LLM 请求不能为空");
        }
        if (!StringUtils.hasText(request.userPrompt())) {
            throw new IllegalArgumentException("LLM 用户提示词不能为空");
        }
        if (!llmProperties.isEnabled()) {
            throw new IllegalStateException("当前环境未开启 LLM 能力");
        }
        if (!StringUtils.hasText(llmProperties.getApiKey())) {
            throw new IllegalStateException("LLM_API_KEY 未配置");
        }
        if (!StringUtils.hasText(resolveModel(request))) {
            throw new IllegalStateException("LLM_MODEL 未配置");
        }
    }

    private HttpRequest buildRequest(LlmTextRequest request) {
        String apiStyle = normalizeApiStyle(llmProperties.getApiStyle());
        boolean useResponses = STYLE_RESPONSES.equals(apiStyle);
        String endpoint = useResponses ? llmProperties.getResponsesPath() : llmProperties.getChatCompletionsPath();
        JSONObject body = useResponses ? buildResponsesBody(request) : buildChatCompletionsBody(request);

        return HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl(llmProperties.getBaseUrl()) + normalizePath(endpoint)))
                .timeout(Duration.ofSeconds(resolveTimeoutSeconds()))
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_PREFIX + llmProperties.getApiKey().trim())
                .header(CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .build();
    }

    private JSONObject buildChatCompletionsBody(LlmTextRequest request) {
        JSONObject body = new JSONObject();
        body.put(FIELD_MODEL, resolveModel(request));
        body.put(FIELD_MESSAGES, buildMessages(request));
        body.put(FIELD_TEMPERATURE, resolveTemperature(request));
        body.put(FIELD_MAX_TOKENS, resolveMaxTokens(request));
        return body;
    }

    private JSONObject buildResponsesBody(LlmTextRequest request) {
        JSONObject body = new JSONObject();
        body.put(FIELD_MODEL, resolveModel(request));
        body.put(FIELD_INSTRUCTIONS, request.systemInstruction());
        body.put(FIELD_INPUT, request.userPrompt());
        body.put(FIELD_TEMPERATURE, resolveTemperature(request));
        body.put(FIELD_MAX_OUTPUT_TOKENS, resolveMaxTokens(request));
        return body;
    }

    private List<JSONObject> buildMessages(LlmTextRequest request) {
        JSONObject user = new JSONObject();
        user.put(FIELD_ROLE, ROLE_USER);
        user.put(FIELD_CONTENT, request.userPrompt());

        if (!StringUtils.hasText(request.systemInstruction())) {
            return List.of(user);
        }

        JSONObject system = new JSONObject();
        system.put(FIELD_ROLE, ROLE_SYSTEM);
        system.put(FIELD_CONTENT, request.systemInstruction());
        return List.of(system, user);
    }

    private String extractOutputText(String rawBody) {
        JSONObject root = JSON.parseObject(rawBody);

        String outputText = root.getString(FIELD_OUTPUT_TEXT);
        if (StringUtils.hasText(outputText)) {
            return outputText;
        }

        JSONArray choices = root.getJSONArray(FIELD_CHOICES);
        if (choices != null && !choices.isEmpty()) {
            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice.getJSONObject(FIELD_MESSAGE);
            if (message != null && StringUtils.hasText(message.getString(FIELD_CONTENT))) {
                return message.getString(FIELD_CONTENT);
            }
        }

        JSONArray output = root.getJSONArray(FIELD_OUTPUT);
        if (output == null || output.isEmpty()) {
            return null;
        }

        for (int i = 0; i < output.size(); i++) {
            JSONObject item = output.getJSONObject(i);
            JSONArray content = item.getJSONArray(FIELD_CONTENT);
            if (content == null || content.isEmpty()) {
                continue;
            }
            for (int j = 0; j < content.size(); j++) {
                JSONObject part = content.getJSONObject(j);
                String text = part.getString(FIELD_TEXT);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }

        return null;
    }

    private String resolveModel(LlmTextRequest request) {
        if (request != null && StringUtils.hasText(request.model())) {
            return request.model().trim();
        }
        return llmProperties.getModel();
    }

    private double resolveTemperature(LlmTextRequest request) {
        if (request != null && request.temperature() != null) {
            return request.temperature();
        }
        return llmProperties.getTemperature();
    }

    private int resolveMaxTokens(LlmTextRequest request) {
        if (request != null && request.maxTokens() != null) {
            return request.maxTokens();
        }
        return llmProperties.getMaxTokens();
    }

    private int resolveTimeoutSeconds() {
        return Math.max(llmProperties.getTimeoutSeconds(), MIN_TIMEOUT_SECONDS);
    }

    private String formatTextForLog(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        String normalized = text
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() <= MAX_LOG_TEXT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_LOG_TEXT_LENGTH) + "...(truncated)";
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
