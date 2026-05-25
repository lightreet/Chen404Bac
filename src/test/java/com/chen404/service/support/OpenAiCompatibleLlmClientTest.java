package com.chen404.service.support;

import com.chen404.config.LlmProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleLlmClientTest {

    @Test
    void shouldUsePerRequestOverridesForChatCompletionCall() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = startServer(authorization, body);
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
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(AtomicReference<String> authorization, AtomicReference<String> body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> handleChatCompletion(exchange, authorization, body));
        server.start();
        return server;
    }

    private void handleChatCompletion(
            HttpExchange exchange,
            AtomicReference<String> authorization,
            AtomicReference<String> body) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = """
                {"choices":[{"message":{"content":"pong"}}]}
                """.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
