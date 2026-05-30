package com.chen404.service.support;

import com.chen404.config.LlmProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleLlmClientTest {

    @Test
    void shouldUsePerRequestOverridesForChatCompletionCall() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = startServer("""
                {"choices":[{"message":{"content":"pong"}}]}
                """, authorization, body);
        try {
            LlmProperties properties = new LlmProperties();
            properties.setEnabled(true);
            properties.setApiKey("default-key");
            properties.setBaseUrl("http://127.0.0.1:1");
            properties.setModel("default-model");
            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties);

            String text = client.generateText(new LlmTextRequest(
                    "override-model",
                    "system",
                    "hello",
                    0.4,
                    900,
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "override-key",
                    "chat-completions",
                    "/chat/completions",
                    "/responses",
                    10
            ));

            assertEquals("pong", text);
            assertEquals("Bearer override-key", authorization.get());
            assertTrue(body.get().contains("\"model\":\"override-model\""));
            assertTrue(body.get().contains("\"max_tokens\":900"));
            assertTrue(body.get().contains("\"stream\":false"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldExtractTextFromSseChatCompletionResponse() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = startServer("""
                data: {"choices":[{"delta":{"content":"红薯"}}]}

                data: {"choices":[{"delta":{"content":"玫瑰"}}]}

                data: [DONE]

                """, authorization, body);
        try {
            LlmProperties properties = new LlmProperties();
            properties.setEnabled(true);
            properties.setApiKey("default-key");
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setModel("default-model");
            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties);

            String text = client.generateText(new LlmTextRequest(
                    null,
                    "system",
                    "hello",
                    null,
                    null,
                    null,
                    null,
                    "chat-completions",
                    "/chat/completions",
                    "/responses",
                    null
            ));

            assertEquals("红薯玫瑰", text);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRetryEmptySseResponseWithRealStreamRequest() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requestCount.incrementAndGet();
            String responseBody = requestBody.contains("\"stream\":true")
                    ? """
                    data: {"choices":[{"delta":{"content":"补全"}}]}

                    data: {"choices":[{"delta":{"content":"成功"}}]}

                    data: [DONE]

                    """
                    : """
                    data: [DONE]

                    """;
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            LlmProperties properties = new LlmProperties();
            properties.setEnabled(true);
            properties.setApiKey("default-key");
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setModel("default-model");
            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties);

            String text = client.generateText(new LlmTextRequest(
                    null,
                    "system",
                    "hello",
                    null,
                    null,
                    null,
                    null,
                    "chat-completions",
                    "/chat/completions",
                    "/responses",
                    null
            ));

            assertEquals("补全成功", text);
            assertEquals(2, requestCount.get());
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(
            String responseBody,
            AtomicReference<String> authorization,
            AtomicReference<String> body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> handleChatCompletion(exchange, responseBody, authorization, body));
        server.start();
        return server;
    }

    private void handleChatCompletion(
            HttpExchange exchange,
            String responseBody,
            AtomicReference<String> authorization,
            AtomicReference<String> body) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
