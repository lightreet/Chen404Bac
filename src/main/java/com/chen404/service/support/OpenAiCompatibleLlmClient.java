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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
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
    private static final String FIELD_DELTA = "delta";
    private static final String FIELD_STREAM = "stream";
    private static final int MIN_TIMEOUT_SECONDS = 5;
    private static final int MAX_LOG_TEXT_LENGTH = 240;
    private static final String DEFAULT_ERROR_PREFIX = "LLM 服务调用失败：";
    private static final String EMPTY_TEXT_ERROR = "LLM 响应缺少文本内容";
    private static final String SSE_DONE_MARKER = "[DONE]";
    private static final String SSE_DATA_PREFIX = "data:";
    private static final String LINE_BREAK_REGEX = "\\R";

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
            String apiStyle = normalizeApiStyle(resolveApiStyle(request));
            log.info("[LLM_TEXT_REQ] model={} style={}", resolveModel(request), apiStyle);
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[LLM_TEXT_FAIL] model={} status={} body={}", resolveModel(request), response.statusCode(), response.body());
                throw new IllegalStateException(DEFAULT_ERROR_PREFIX + response.statusCode());
            }

            String outputText = extractOutputText(response.body());
            if (!StringUtils.hasText(outputText) && shouldRetryEmptySseWithStream(apiStyle, response.body())) {
                log.warn("[LLM_TEXT_EMPTY_SSE_RETRY] model={}", resolveModel(request));
                outputText = retryEmptySseWithStream(request);
            }
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

    @Override
    public void streamText(LlmTextRequest request, LlmTextStreamHandler handler) {
        validateConfiguration(request);
        if (handler == null) {
            throw new IllegalArgumentException("LLM 流式回调不能为空");
        }

        String apiStyle = normalizeApiStyle(resolveApiStyle(request));
        if (!STYLE_CHAT_COMPLETIONS.equals(apiStyle)) {
            streamByChunkingPlainText(request, handler);
            return;
        }

        HttpRequest httpRequest = buildStreamRequest(request);
        try {
            log.info("[LLM_TEXT_STREAM_REQ] model={} style={}", resolveModel(request), apiStyle);
            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.warn("[LLM_TEXT_STREAM_FAIL] model={} status={} body={}",
                        resolveModel(request), response.statusCode(), body);
                throw new IllegalStateException(DEFAULT_ERROR_PREFIX + response.statusCode());
            }
            try (InputStream inputStream = response.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                readChatCompletionStream(reader, handler);
            }
            if (!handler.isCancelled()) {
                handler.onComplete();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[LLM_TEXT_STREAM_INTERRUPTED] model={}", resolveModel(request), e);
            throw new IllegalStateException(DEFAULT_ERROR_PREFIX + "请求被中断", e);
        } catch (IOException e) {
            log.error("[LLM_TEXT_STREAM_IO_FAIL] model={} message={}", resolveModel(request), e.getMessage(), e);
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
        if (!llmProperties.isEnabled() && !StringUtils.hasText(request.apiKey())) {
            throw new IllegalStateException("当前环境未开启 LLM 能力");
        }
        if (!StringUtils.hasText(resolveApiKey(request))) {
            throw new IllegalStateException("LLM_API_KEY 未配置");
        }
        if (!StringUtils.hasText(resolveModel(request))) {
            throw new IllegalStateException("LLM_MODEL 未配置");
        }
    }

    private HttpRequest buildRequest(LlmTextRequest request) {
        String apiStyle = normalizeApiStyle(resolveApiStyle(request));
        boolean useResponses = STYLE_RESPONSES.equals(apiStyle);
        String endpoint = useResponses ? resolveResponsesPath(request) : resolveChatCompletionsPath(request);
        JSONObject body = useResponses ? buildResponsesBody(request) : buildChatCompletionsBody(request);
        if (!useResponses) {
            body.put(FIELD_STREAM, false);
        }

        return HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl(resolveBaseUrl(request)) + normalizePath(endpoint)))
                .timeout(Duration.ofSeconds(resolveTimeoutSeconds(request)))
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_PREFIX + resolveApiKey(request).trim())
                .header(CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .build();
    }

    private HttpRequest buildStreamRequest(LlmTextRequest request) {
        JSONObject body = buildChatCompletionsBody(request);
        body.put(FIELD_STREAM, true);

        return HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl(resolveBaseUrl(request)) + normalizePath(resolveChatCompletionsPath(request))))
                .timeout(Duration.ofSeconds(resolveTimeoutSeconds(request)))
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_PREFIX + resolveApiKey(request).trim())
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
        if (!StringUtils.hasText(rawBody)) {
            return null;
        }
        if (isSseBody(rawBody)) {
            return extractOutputTextFromSse(rawBody);
        }

        JSONObject root = JSON.parseObject(rawBody);
        return extractOutputTextFromJson(root);
    }

    private boolean isSseBody(String rawBody) {
        return rawBody.trim().startsWith(SSE_DATA_PREFIX);
    }

    private String extractOutputTextFromSse(String rawBody) {
        StringBuilder outputText = new StringBuilder();
        for (String line : rawBody.split(LINE_BREAK_REGEX)) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(SSE_DATA_PREFIX)) {
                continue;
            }
            String payload = trimmed.substring(SSE_DATA_PREFIX.length()).trim();
            if (SSE_DONE_MARKER.equals(payload)) {
                continue;
            }
            String text = extractSsePayloadText(payload);
            if (StringUtils.hasText(text)) {
                outputText.append(text);
            }
        }
        return outputText.toString();
    }

    private boolean shouldRetryEmptySseWithStream(String apiStyle, String rawBody) {
        return STYLE_CHAT_COMPLETIONS.equals(apiStyle) && isSseBody(rawBody);
    }

    private String retryEmptySseWithStream(LlmTextRequest request) {
        StringBuilder outputText = new StringBuilder();
        streamText(request, new LlmTextStreamHandler() {
            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public void onTextDelta(String text) {
                outputText.append(text);
            }

            @Override
            public void onComplete() {
                // Completion is represented by returning the collected text.
            }
        });
        return outputText.toString();
    }

    private String extractSsePayloadText(String payload) {
        JSONObject root = JSON.parseObject(payload);
        String deltaText = extractDeltaText(root);
        if (StringUtils.hasText(deltaText)) {
            return deltaText;
        }
        return extractOutputTextFromJson(root);
    }

    private String extractOutputTextFromJson(JSONObject root) {
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

    private void readChatCompletionStream(BufferedReader reader, LlmTextStreamHandler handler) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (handler.isCancelled()) {
                return;
            }
            String trimmed = line.trim();
            if (!trimmed.startsWith(SSE_DATA_PREFIX)) {
                continue;
            }
            String payload = trimmed.substring(SSE_DATA_PREFIX.length()).trim();
            if (SSE_DONE_MARKER.equals(payload)) {
                return;
            }
            String deltaText = extractDeltaText(payload);
            if (StringUtils.hasText(deltaText)) {
                handler.onTextDelta(deltaText);
            }
        }
    }

    private String extractDeltaText(String payload) {
        return extractDeltaText(JSON.parseObject(payload));
    }

    private String extractDeltaText(JSONObject root) {
        JSONArray choices = root.getJSONArray(FIELD_CHOICES);
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        JSONObject firstChoice = choices.getJSONObject(0);
        JSONObject delta = firstChoice.getJSONObject(FIELD_DELTA);
        if (delta == null) {
            return null;
        }
        return delta.getString(FIELD_CONTENT);
    }

    private void streamByChunkingPlainText(LlmTextRequest request, LlmTextStreamHandler handler) {
        String plainText = generateText(request);
        if (!StringUtils.hasText(plainText)) {
            handler.onComplete();
            return;
        }
        for (String chunk : splitIntoDisplayChunks(plainText)) {
            if (handler.isCancelled()) {
                return;
            }
            handler.onTextDelta(chunk);
        }
        handler.onComplete();
    }

    private List<String> splitIntoDisplayChunks(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> chunks = new java.util.ArrayList<>();
        int cursor = 0;
        int step = 18;
        while (cursor < normalized.length()) {
            int next = Math.min(normalized.length(), cursor + step);
            chunks.add(normalized.substring(cursor, next));
            cursor = next;
        }
        return chunks;
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

    private int resolveTimeoutSeconds(LlmTextRequest request) {
        if (request != null && request.timeoutSeconds() != null) {
            return Math.max(request.timeoutSeconds(), MIN_TIMEOUT_SECONDS);
        }
        return resolveTimeoutSeconds();
    }

    private String resolveBaseUrl(LlmTextRequest request) {
        if (request != null && StringUtils.hasText(request.baseUrl())) {
            return request.baseUrl().trim();
        }
        return llmProperties.getBaseUrl();
    }

    private String resolveApiKey(LlmTextRequest request) {
        if (request != null && StringUtils.hasText(request.apiKey())) {
            return request.apiKey().trim();
        }
        return llmProperties.getApiKey();
    }

    private String resolveApiStyle(LlmTextRequest request) {
        if (request != null && StringUtils.hasText(request.apiStyle())) {
            return request.apiStyle().trim();
        }
        return llmProperties.getApiStyle();
    }

    private String resolveChatCompletionsPath(LlmTextRequest request) {
        if (request != null && StringUtils.hasText(request.chatCompletionsPath())) {
            return request.chatCompletionsPath().trim();
        }
        return llmProperties.getChatCompletionsPath();
    }

    private String resolveResponsesPath(LlmTextRequest request) {
        if (request != null && StringUtils.hasText(request.responsesPath())) {
            return request.responsesPath().trim();
        }
        return llmProperties.getResponsesPath();
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
