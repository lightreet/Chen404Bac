package com.chen404.service.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chen404.config.LlmProperties;
import com.chen404.config.AiStreamProperties;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

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
    private static final int MAX_SSE_LINE_CHARS = 65_536;
    private static final int MAX_STREAM_BODY_CHARS = 262_144;
    private static final String DEFAULT_ERROR_PREFIX = "LLM 服务调用失败：";
    private static final String EMPTY_TEXT_ERROR = "LLM 响应缺少文本内容";
    private static final String SSE_DONE_MARKER = "[DONE]";
    private static final String SSE_DATA_PREFIX = "data:";
    private static final String LINE_BREAK_REGEX = "\\R";

    private final LlmProperties llmProperties;
    private final HttpClient httpClient;
    private final AiStreamProperties streamProperties;
    private final ScheduledExecutorService streamScheduler;

    public OpenAiCompatibleLlmClient(LlmProperties llmProperties, AiStreamProperties streamProperties,
            @Qualifier("aiStreamScheduler") ScheduledExecutorService streamScheduler) {
        this.llmProperties = llmProperties;
        this.streamProperties = streamProperties;
        this.streamScheduler = streamScheduler;
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
                log.warn("[LLM_TEXT_FAIL] model={} status={} responseLength={}",
                        resolveModel(request), response.statusCode(), response.body().length());
                throw new IllegalStateException(DEFAULT_ERROR_PREFIX + response.statusCode());
            }

            String outputText = extractOutputText(response.body());
            if (!StringUtils.hasText(outputText) && shouldRetryEmptySseWithStream(apiStyle, response.body())) {
                log.warn("[LLM_TEXT_EMPTY_SSE_RETRY] model={}", resolveModel(request));
                outputText = retryEmptySseWithStream(request);
            }
            if (!StringUtils.hasText(outputText)) {
                log.warn("[LLM_TEXT_EMPTY] model={} responseLength={}",
                        resolveModel(request), response.body().length());
                throw new IllegalStateException(EMPTY_TEXT_ERROR);
            }
            log.info("[LLM_TEXT_OK] model={} textLength={}", resolveModel(request), outputText.length());
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

        if (handler.isCancelled()) {
            return;
        }
        String apiStyle = normalizeApiStyle(resolveApiStyle(request));
        HttpRequest httpRequest = STYLE_CHAT_COMPLETIONS.equals(apiStyle) ? buildStreamRequest(request) : buildRequest(request);
        long timeoutMs = Math.min(streamProperties.getTotalTimeoutMs(), Duration.ofSeconds(resolveTimeoutSeconds(request)).toMillis());
        LlmStreamControl control = new LlmStreamControl(handler, streamScheduler, timeoutMs, streamProperties.getIdleTimeoutMs());
        try (control) {
            log.info("[LLM_TEXT_STREAM_REQ] model={} style={}", resolveModel(request), apiStyle);
            var pending = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            control.track(pending);
            HttpResponse<InputStream> response = pending.get();
            try (InputStream inputStream = control.track(response.body());
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException(DEFAULT_ERROR_PREFIX + response.statusCode());
                }
                if (STYLE_CHAT_COMPLETIONS.equals(apiStyle)) {
                    readChatCompletionStream(reader, handler);
                } else {
                    streamByChunkingPlainText(reader, handler);
                }
            }
            if (control.timedOut()) {
                throw new LlmStreamTimeoutException();
            }
            if (!handler.isCancelled()) {
                handler.onComplete();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!handler.isCancelled()) {
                throw new IllegalStateException(DEFAULT_ERROR_PREFIX + "请求被中断", e);
            }
        } catch (IOException | ExecutionException | CancellationException e) {
            if (control.timedOut()) {
                log.warn("[LLM_STREAM_TIMEOUT] model={}", resolveModel(request));
                throw new LlmStreamTimeoutException();
            }
            if (!handler.isCancelled()) {
                throw new IllegalStateException(DEFAULT_ERROR_PREFIX + "网络异常", e);
            }
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
        int responseChars = 0;
        while (!handler.isCancelled() && (line = readBoundedLine(reader)) != null) {
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
                responseChars += deltaText.length();
                if (responseChars > streamProperties.getMaxResponseChars()) {
                    throw new IllegalStateException("LLM 流式响应超过长度限制");
                }
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

    /** Responses 兼容路径也使用相同的可取消读取，不能退回不可取消的同步调用。 */
    private void streamByChunkingPlainText(BufferedReader reader, LlmTextStreamHandler handler) throws IOException {
        StringBuilder body = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while (!handler.isCancelled() && (count = reader.read(buffer)) != -1) {
            if (body.length() + count > MAX_STREAM_BODY_CHARS) {
                throw new IllegalStateException("LLM 响应体超过长度限制");
            }
            body.append(buffer, 0, count);
        }
        if (handler.isCancelled()) {
            return;
        }
        String plainText = extractOutputText(body.toString());
        if (!StringUtils.hasText(plainText)) {
            return;
        }
        if (plainText.length() > streamProperties.getMaxResponseChars()) {
            throw new IllegalStateException("LLM 流式响应超过长度限制");
        }
        for (String chunk : splitIntoDisplayChunks(plainText)) {
            if (handler.isCancelled()) {
                return;
            }
            handler.onTextDelta(chunk);
        }
    }

    /** 限制单行长度，防止上游持续发送不换行的数据使 BufferedReader.readLine 无限增长。 */
    private String readBoundedLine(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int character;
        while ((character = reader.read()) != -1) {
            if (character == '\n') {
                return line.toString();
            }
            if (line.length() >= MAX_SSE_LINE_CHARS) {
                throw new IllegalStateException("LLM SSE 行超过长度限制");
            }
            line.append((char) character);
        }
        return line.isEmpty() ? null : line.toString();
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
