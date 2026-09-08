package com.chen404.service.impl;

import com.chen404.config.AiRuntimeProperties;
import com.chen404.config.AiStreamProperties;
import com.chen404.config.AiStreamTaskConfig;
import com.chen404.domain.dto.AiAdminConfigDTO;
import com.chen404.domain.dto.AiChatMessageDTO;
import com.chen404.domain.dto.AiChatRequest;
import com.chen404.domain.entity.AiChatSession;
import com.chen404.exception.TooManyRequestsException;
import com.chen404.service.AiChatSessionService;
import com.chen404.service.AiConfigService;
import com.chen404.service.ArticleKnowledgeService;
import com.chen404.service.ArticleService;
import com.chen404.service.support.LlmTextStreamHandler;
import com.chen404.service.support.chat.AiStreamCoordinator;
import com.chen404.service.support.prompt.AiMaidPromptBuilder;
import com.chen404.service.support.scenario.AiScenarioExecutor;
import com.chen404.service.support.scenario.chat.MaidChatScenarioDefinition;
import com.chen404.service.support.scenario.chat.MaidChatScenarioResult;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/** 真实 Spring MVC 异步回调与服务/准入控制联动，模型使用可控测试替身。 */
class AiChatStreamLifecycleTest {
    private final AiStreamProperties limits = new AiStreamProperties();
    private final AiConfigService config = mock(AiConfigService.class);
    private final AiChatSessionService sessions = mock(AiChatSessionService.class);
    private final MaidChatScenarioDefinition scenario = mock(MaidChatScenarioDefinition.class);
    private final ArticleService articles = mock(ArticleService.class);
    private final ArticleKnowledgeService knowledge = mock(ArticleKnowledgeService.class);
    private ThreadPoolExecutor executor;
    private ScheduledThreadPoolExecutor scheduler;
    private AiStreamCoordinator coordinator;
    private AiChatServiceImpl service;
    private AiAdminConfigDTO effective;

    @BeforeEach
    void setUp() {
        limits.setMaxConcurrent(2);
        limits.setMaxPerUser(1);
        var taskConfig = new AiStreamTaskConfig();
        executor = taskConfig.aiStreamExecutor(limits);
        scheduler = taskConfig.aiStreamScheduler();
        coordinator = new AiStreamCoordinator(executor, scheduler, limits);
        service = new AiChatServiceImpl(mock(AiScenarioExecutor.class), new AiRuntimeProperties(), scenario,
                articles, knowledge, mock(AiMaidPromptBuilder.class), sessions, config, coordinator, limits);
        effective = new AiAdminConfigDTO();
        when(config.getEffectiveConfig()).thenReturn(effective);
        AiChatSession session = new AiChatSession();
        session.setSessionId("test-stream-session");
        when(sessions.ensureSession(any(), any(), any(), any(), any(), any())).thenReturn(session);
    }

    @AfterEach
    void stop() throws Exception {
        coordinator.shutdown();
        executor.shutdownNow();
        scheduler.shutdownNow();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
    }

    @Test
    void disabledFeatureMustNotCreateSessionOrLoadArticleContext() {
        effective.getChat().setEnabled(false);
        assertThrows(IllegalStateException.class, () -> service.streamChat(chatRequest(), 7L));
        effective.getChat().setEnabled(true);
        effective.getLlm().setEnabled(false);
        assertThrows(IllegalStateException.class, () -> service.streamChat(chatRequest(), 7L));
        verifyNoInteractions(sessions, articles, knowledge, scenario);
    }

    @Test
    void mvcDisconnectMustCancelUpstreamWithoutSavingAssistantReply() throws Exception {
        CountDownLatch streaming = new CountDownLatch(1);
        CountDownLatch upstreamCancelled = new CountDownLatch(1);
        doAnswer(invocation -> {
            LlmTextStreamHandler handler = invocation.getArgument(1);
            try (AutoCloseable ignored = handler.onCancellation(upstreamCancelled::countDown)) {
                streaming.countDown();
                try {
                    upstreamCancelled.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                assertTrue(handler.isCancelled());
            }
            return null;
        }).when(scenario).stream(any(), any());
        var mvc = MockMvcBuilders.standaloneSetup(new StreamController(service)).build();
        MvcResult result = mvc.perform(get("/test-stream")).andExpect(request().asyncStarted()).andReturn();
        assertTrue(streaming.await(2, TimeUnit.SECONDS));
        assertThrows(TooManyRequestsException.class, () -> service.streamChat(chatRequest(), 7L));
        verify(sessions, times(1)).ensureSession(any(), any(), any(), any(), any(), any());

        MockAsyncContext context = (MockAsyncContext) result.getRequest().getAsyncContext();
        for (AsyncListener listener : context.getListeners()) {
            listener.onError(new AsyncEvent(context, new IOException("client disconnected")));
        }
        assertTrue(upstreamCancelled.await(2, TimeUnit.SECONDS));
        await().atMost(2, TimeUnit.SECONDS).until(() -> executor.getActiveCount() == 0);
        verify(sessions, never()).saveAssistantMessage(anyString(), any());
    }

    @Test
    void overallTimeoutMustCancelWorkAndReleaseCapacity() throws Exception {
        limits.setTotalTimeoutMs(150);
        CountDownLatch cancelled = new CountDownLatch(1);
        doAnswer(invocation -> {
            LlmTextStreamHandler handler = invocation.getArgument(1);
            try (AutoCloseable ignored = handler.onCancellation(cancelled::countDown)) {
                try { cancelled.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
            return null;
        }).when(scenario).stream(any(), any());
        service.streamChat(chatRequest(), 7L);
        assertTrue(cancelled.await(2, TimeUnit.SECONDS));
        await().atMost(2, TimeUnit.SECONDS).until(() -> executor.getActiveCount() == 0);
        verify(sessions, never()).saveAssistantMessage(anyString(), any());
        assertEquals(0, scheduler.getQueue().size());
    }

    @Test
    void successfulStreamMustSaveOneReplyAndReleaseCapacity() {
        doAnswer(invocation -> {
            LlmTextStreamHandler handler = invocation.getArgument(1);
            handler.onTextDelta("hello");
            handler.onComplete();
            return null;
        }).when(scenario).stream(any(), any());
        when(scenario.buildStreamResult(eq("hello"), any()))
                .thenReturn(new MaidChatScenarioResult("hello", "hello", "happy", List.of()));
        service.streamChat(chatRequest(), 7L);
        await().atMost(2, TimeUnit.SECONDS).until(() -> executor.getCompletedTaskCount() == 1);
        verify(sessions, times(1)).saveAssistantMessage(eq("test-stream-session"), any());
        assertEquals(0, scheduler.getQueue().size());
    }

    private static AiChatRequest chatRequest() {
        AiChatMessageDTO message = new AiChatMessageDTO();
        message.setRole("user");
        message.setContent("你好");
        AiChatRequest request = new AiChatRequest();
        request.setMessages(List.of(message));
        request.setPageContext("home");
        return request;
    }

    @RestController
    static class StreamController {
        private final AiChatServiceImpl service;
        StreamController(AiChatServiceImpl service) { this.service = service; }
        @GetMapping("/test-stream") SseEmitter stream() { return service.streamChat(chatRequest(), 7L); }
    }
}
