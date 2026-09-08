package com.chen404.service.support;

import com.chen404.config.AiStreamProperties;
import com.chen404.config.AiStreamTaskConfig;
import com.chen404.config.LlmProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 用本地 HTTP 上游重现阻塞读取，不依赖真实模型或网络服务。 */
@Timeout(10)
class OpenAiCompatibleLlmClientStreamTest {
    private final ScheduledThreadPoolExecutor scheduler = new AiStreamTaskConfig().aiStreamScheduler();
    private final ExecutorService serverExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final CountDownLatch releaseServer = new CountDownLatch(1);
    private final CountDownLatch received = new CountDownLatch(1);
    private final AiStreamProperties limits = new AiStreamProperties();
    private final LlmProperties properties = new LlmProperties();
    private HttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(serverExecutor);
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setChatCompletionsPath("/stream");
        properties.setResponsesPath("/stream");
        limits.setTotalTimeoutMs(3000);
        limits.setIdleTimeoutMs(250);
    }

    @AfterEach
    void stop() throws Exception {
        releaseServer.countDown();
        server.stop(0);
        worker.shutdownNow();
        serverExecutor.shutdownNow();
        scheduler.shutdownNow();
        assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS), "LLM 工作线程必须退出");
        assertTrue(scheduler.awaitTermination(2, TimeUnit.SECONDS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"chat-completions", "responses"})
    void idleTimeoutMustCloseBodyEvenWhenReadNeverReturns(String style) throws Exception {
        properties.setApiStyle(style);
        start(exchange -> {
            sendHeaders(exchange);
            exchange.getResponseBody().write(" ".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            received.countDown();
            holdServer();
            exchange.close();
        });
        Handler handler = new Handler();
        Future<?> request = invoke(handler);
        assertTrue(received.await(2, TimeUnit.SECONDS));
        ExecutionException failure = assertThrows(ExecutionException.class, () -> request.get(2, TimeUnit.SECONDS));
        assertInstanceOf(LlmStreamTimeoutException.class, failure.getCause());
        assertEquals(0, handler.completed.get());
    }

    @Test
    void totalTimeoutMustStopAContinuouslyActiveStream() throws Exception {
        limits.setTotalTimeoutMs(450);
        limits.setIdleTimeoutMs(1000);
        start(exchange -> {
            sendHeaders(exchange);
            while (releaseServer.getCount() > 0) {
                try {
                    exchange.getResponseBody().write("data: {\"choices\":[{\"delta\":{\"content\":\"x\"}}]}\n\n"
                            .getBytes(StandardCharsets.UTF_8));
                    exchange.getResponseBody().flush();
                    if (releaseServer.await(40, TimeUnit.MILLISECONDS)) {
                        break;
                    }
                } catch (IOException ex) {
                    break;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            exchange.close();
        });
        Handler handler = new Handler();
        Future<?> request = invoke(handler);
        ExecutionException failure = assertThrows(ExecutionException.class, () -> request.get(2, TimeUnit.SECONDS));
        assertInstanceOf(LlmStreamTimeoutException.class, failure.getCause());
        assertFalse(handler.text.isEmpty());
        assertEquals(0, handler.completed.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationMustStopWaitingForHeadersOrBody(boolean headersSent) throws Exception {
        limits.setIdleTimeoutMs(5000);
        start(exchange -> {
            if (headersSent) {
                sendHeaders(exchange);
                exchange.getResponseBody().write(' ');
                exchange.getResponseBody().flush();
            }
            received.countDown();
            holdServer();
            exchange.close();
        });
        Handler handler = new Handler();
        Future<?> request = invoke(handler);
        assertTrue(received.await(2, TimeUnit.SECONDS));
        handler.cancel();
        request.get(1, TimeUnit.SECONDS);
        assertEquals(0, handler.completed.get());
    }

    @Test
    void shouldBoundUnterminatedSseLines() throws Exception {
        start(exchange -> {
            sendHeaders(exchange);
            try {
                exchange.getResponseBody().write("x".repeat(70_000).getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
            } catch (IOException expectedClose) {
                // 客户端达到长度上限后主动关闭。
            } finally {
                exchange.close();
            }
        });
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> invoke(new Handler()).get(2, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("SSE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"chat-completions", "responses"})
    void normalStreamMustCompleteExactlyOnceAndReleaseWatchdog(String style) throws Exception {
        properties.setApiStyle(style);
        start(exchange -> {
            sendHeaders(exchange);
            String body = "responses".equals(style) ? "{\"output_text\":\"hello\"}"
                    : "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\ndata: [DONE]\n\n";
            exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        Handler handler = new Handler();
        invoke(handler).get(2, TimeUnit.SECONDS);
        assertEquals("hello", handler.text.toString());
        assertEquals(1, handler.completed.get());
        assertEquals(0, scheduler.getQueue().size());
    }

    @Test
    void shouldStopWhenAccumulatedDeltasExceedResponseBudget() throws Exception {
        limits.setMaxResponseChars(5);
        start(exchange -> {
            sendHeaders(exchange);
            String delta = "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\n";
            exchange.getResponseBody().write((delta + delta).getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        Handler handler = new Handler();
        ExecutionException failure = assertThrows(ExecutionException.class, () -> invoke(handler).get(2, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals("hello", handler.text.toString());
        assertEquals(0, handler.completed.get());
    }

    private Future<?> invoke(Handler handler) {
        var client = new OpenAiCompatibleLlmClient(properties, limits, scheduler);
        return worker.submit(() -> client.streamText(LlmTextRequest.of("system", "hello"), handler));
    }

    private void start(HttpHandler handler) {
        server.createContext("/stream", handler);
        server.start();
    }

    private void sendHeaders(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
    }

    private void holdServer() {
        try {
            releaseServer.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static class Handler implements LlmTextStreamHandler {
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicReference<Runnable> cancellation = new AtomicReference<>();
        final AtomicInteger completed = new AtomicInteger();
        final StringBuilder text = new StringBuilder();

        @Override public boolean isCancelled() { return cancelled.get(); }
        @Override public void onTextDelta(String delta) { text.append(delta); }
        @Override public void onComplete() { completed.incrementAndGet(); }
        @Override public AutoCloseable onCancellation(Runnable action) {
            cancellation.set(action);
            if (cancelled.get()) { action.run(); }
            return () -> cancellation.compareAndSet(action, null);
        }
        void cancel() {
            cancelled.set(true);
            Runnable action = cancellation.get();
            if (action != null) { action.run(); }
        }
    }
}
