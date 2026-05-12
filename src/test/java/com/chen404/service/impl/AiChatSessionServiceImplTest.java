package com.chen404.service.impl;

import com.chen404.domain.dto.AiChatSessionDetailResponse;
import com.chen404.domain.entity.AiChatMessage;
import com.chen404.domain.entity.AiChatSession;
import com.chen404.mapper.AiChatMessageMapper;
import com.chen404.mapper.AiChatSessionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiChatSessionServiceImplTest {

    @Test
    void shouldRestoreRelatedArticlesFromSessionHistory() {
        AiChatSessionMapper sessionMapper = mock(AiChatSessionMapper.class);
        AiChatMessageMapper messageMapper = mock(AiChatMessageMapper.class);
        AiChatSessionServiceImpl service = new AiChatSessionServiceImpl(sessionMapper, messageMapper);

        AiChatSession session = new AiChatSession();
        session.setSessionId("sess_test");
        session.setVisitorId("visitor-1");
        when(sessionMapper.selectById("sess_test")).thenReturn(session);

        AiChatMessage assistantMessage = new AiChatMessage();
        assistantMessage.setMessageId("msg_assistant");
        assistantMessage.setSessionId("sess_test");
        assistantMessage.setRole("assistant");
        assistantMessage.setContent("这两篇你也许会喜欢。");
        assistantMessage.setRelatedArticlesJson("[{\"articleId\":88,\"articleTitle\":\"相关文章\",\"url\":\"/article/88\"}]");
        when(messageMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(assistantMessage));

        AiChatSessionDetailResponse response = service.loadSessionDetail("sess_test", null, "visitor-1");

        assertEquals(1, response.getMessages().size());
        assertFalse(response.getMessages().get(0).getRelatedArticles().isEmpty());
        assertEquals(88L, response.getMessages().get(0).getRelatedArticles().get(0).getArticleId());
        assertEquals("/article/88", response.getMessages().get(0).getRelatedArticles().get(0).getUrl());
    }
}
