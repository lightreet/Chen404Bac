package com.chen404.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 通用大模型接入配置。
 * <p>
 * 该配置面向 OpenAI-compatible 与其他兼容厂商，业务层只依赖统一的参数。
 */
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-5.4-mini";
    private static final String DEFAULT_API_STYLE = "chat-completions";
    private static final String DEFAULT_CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String DEFAULT_RESPONSES_PATH = "/responses";
    private static final double DEFAULT_TEMPERATURE = 0.2d;
    private static final int DEFAULT_MAX_TOKENS = 512;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private boolean enabled;
    private String apiKey;
    private String baseUrl = DEFAULT_BASE_URL;
    private String model = DEFAULT_MODEL;
    private String apiStyle = DEFAULT_API_STYLE;
    private String chatCompletionsPath = DEFAULT_CHAT_COMPLETIONS_PATH;
    private String responsesPath = DEFAULT_RESPONSES_PATH;
    private double temperature = DEFAULT_TEMPERATURE;
    private int maxTokens = DEFAULT_MAX_TOKENS;
    private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiStyle() {
        return apiStyle;
    }

    public void setApiStyle(String apiStyle) {
        this.apiStyle = apiStyle;
    }

    public String getChatCompletionsPath() {
        return chatCompletionsPath;
    }

    public void setChatCompletionsPath(String chatCompletionsPath) {
        this.chatCompletionsPath = chatCompletionsPath;
    }

    public String getResponsesPath() {
        return responsesPath;
    }

    public void setResponsesPath(String responsesPath) {
        this.responsesPath = responsesPath;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
