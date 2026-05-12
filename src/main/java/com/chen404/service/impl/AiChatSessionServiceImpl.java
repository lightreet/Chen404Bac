package com.chen404.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chen404.domain.dto.AiChatCitationDTO;
import com.chen404.domain.dto.AiChatHistoryMessageDTO;
import com.chen404.domain.dto.AiChatRelatedArticleDTO;
import com.chen404.domain.dto.AiChatResponse;
import com.chen404.domain.dto.AiChatSessionDetailResponse;
import com.chen404.domain.entity.AiChatMessage;
import com.chen404.domain.entity.AiChatSession;
import com.chen404.exception.BadRequestException;
import com.chen404.exception.ForbiddenException;
import com.chen404.mapper.AiChatMessageMapper;
import com.chen404.mapper.AiChatSessionMapper;
import com.chen404.service.AiChatSessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * AI 女仆会话持久化服务实现。
 * <p>
 * 负责会话归属校验、历史消息保存与会话恢复，
 * 让前端在刷新或再次打开面板时仍能看到最近对话。
 */
@Service
public class AiChatSessionServiceImpl implements AiChatSessionService {

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String STATUS_COMPLETED = "completed";
    private static final int SESSION_STATUS_ACTIVE = 1;

    private final AiChatSessionMapper aiChatSessionMapper;
    private final AiChatMessageMapper aiChatMessageMapper;

    public AiChatSessionServiceImpl(
            AiChatSessionMapper aiChatSessionMapper,
            AiChatMessageMapper aiChatMessageMapper) {
        this.aiChatSessionMapper = aiChatSessionMapper;
        this.aiChatMessageMapper = aiChatMessageMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiChatSession ensureSession(
            String sessionId,
            Long requesterId,
            String visitorId,
            String sourcePage,
            Long sourceArticleId,
            String titleHint) {
        AiChatSession existing = null;
        if (StringUtils.hasText(sessionId)) {
            existing = aiChatSessionMapper.selectById(sessionId.trim());
        }

        if (existing != null) {
            assertSessionOwnership(existing, requesterId, visitorId);
            if (existing.getUserId() == null && requesterId != null) {
                existing.setUserId(requesterId);
            }
            if (!StringUtils.hasText(existing.getVisitorId()) && StringUtils.hasText(visitorId)) {
                existing.setVisitorId(visitorId.trim());
            }
            existing.setLastMessageAt(LocalDateTime.now());
            aiChatSessionMapper.updateById(existing);
            return existing;
        }

        AiChatSession session = new AiChatSession();
        session.setSessionId(resolveSessionId(sessionId));
        session.setUserId(requesterId);
        session.setVisitorId(StringUtils.hasText(visitorId) ? visitorId.trim() : null);
        session.setTitle(buildTitle(titleHint));
        session.setSourcePage(StringUtils.hasText(sourcePage) ? sourcePage.trim() : "unknown");
        session.setSourceArticleId(sourceArticleId);
        session.setStatus(SESSION_STATUS_ACTIVE);
        session.setLastMessageAt(LocalDateTime.now());
        aiChatSessionMapper.insert(session);
        return session;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUserMessage(String sessionId, String content) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(content)) {
            return;
        }
        AiChatMessage message = new AiChatMessage();
        message.setMessageId(buildMessageId());
        message.setSessionId(sessionId.trim());
        message.setRole(ROLE_USER);
        message.setContent(content.trim());
        message.setStatus(STATUS_COMPLETED);
        aiChatMessageMapper.insert(message);
        touchSession(sessionId.trim());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAssistantMessage(String sessionId, AiChatResponse response) {
        if (!StringUtils.hasText(sessionId) || response == null) {
            return;
        }
        AiChatMessage message = new AiChatMessage();
        message.setMessageId(response.getMessageId());
        message.setSessionId(sessionId.trim());
        message.setRole(ROLE_ASSISTANT);
        message.setScene(response.getScene());
        message.setContent(response.getReplyText());
        message.setMood(response.getMood());
        message.setStatus(STATUS_COMPLETED);
        message.setTraceId(response.getTraceId());
        message.setCitationsJson(JSON.toJSONString(response.getCitations()));
        message.setRelatedArticlesJson(JSON.toJSONString(response.getRelatedArticles()));
        message.setSuggestionsJson(JSON.toJSONString(response.getSuggestions()));
        aiChatMessageMapper.insert(message);
        touchSession(sessionId.trim());
    }

    @Override
    public AiChatSessionDetailResponse loadSessionDetail(String sessionId, Long requesterId, String visitorId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BadRequestException("会话 ID 不能为空");
        }
        AiChatSession session = aiChatSessionMapper.selectById(sessionId.trim());
        if (session == null) {
            throw new BadRequestException("会话不存在");
        }
        assertSessionOwnership(session, requesterId, visitorId);

        List<AiChatMessage> records = aiChatMessageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, session.getSessionId())
                .orderByAsc(AiChatMessage::getCreateTime));

        AiChatSessionDetailResponse response = new AiChatSessionDetailResponse();
        response.setSessionId(session.getSessionId());
        response.setTitle(session.getTitle());
        response.setSourcePage(session.getSourcePage());
        response.setSourceArticleId(session.getSourceArticleId());
        response.setMessages(records.stream().map(this::toHistoryMessage).toList());
        return response;
    }

    private AiChatHistoryMessageDTO toHistoryMessage(AiChatMessage message) {
        AiChatHistoryMessageDTO dto = new AiChatHistoryMessageDTO();
        dto.setMessageId(message.getMessageId());
        dto.setRole(message.getRole());
        dto.setContent(message.getContent());
        dto.setScene(message.getScene());
        dto.setMood(message.getMood());
        dto.setCitations(parseCitations(message.getCitationsJson()));
        dto.setRelatedArticles(parseRelatedArticles(message.getRelatedArticlesJson()));
        dto.setSuggestions(parseSuggestions(message.getSuggestionsJson()));
        return dto;
    }

    private List<AiChatCitationDTO> parseCitations(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return List.of();
        }
        return JSON.parseObject(rawJson, new TypeReference<List<AiChatCitationDTO>>() {
        });
    }

    private List<AiChatRelatedArticleDTO> parseRelatedArticles(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return List.of();
        }
        return JSON.parseObject(rawJson, new TypeReference<List<AiChatRelatedArticleDTO>>() {
        });
    }

    private List<String> parseSuggestions(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return List.of();
        }
        return JSON.parseObject(rawJson, new TypeReference<List<String>>() {
        });
    }

    private void touchSession(String sessionId) {
        AiChatSession session = aiChatSessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }
        session.setLastMessageAt(LocalDateTime.now());
        aiChatSessionMapper.updateById(session);
    }

    private void assertSessionOwnership(AiChatSession session, Long requesterId, String visitorId) {
        if (session.getUserId() != null && session.getUserId().equals(requesterId)) {
            return;
        }
        if (session.getUserId() == null && StringUtils.hasText(session.getVisitorId())
                && session.getVisitorId().equals(StringUtils.hasText(visitorId) ? visitorId.trim() : null)) {
            return;
        }
        if (session.getUserId() == null && !StringUtils.hasText(session.getVisitorId())
                && requesterId == null && !StringUtils.hasText(visitorId)) {
            return;
        }
        throw new ForbiddenException("当前会话无权访问");
    }

    private String resolveSessionId(String sessionId) {
        if (StringUtils.hasText(sessionId)) {
            return sessionId.trim();
        }
        return "sess_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String buildMessageId() {
        return "msg_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String buildTitle(String titleHint) {
        if (!StringUtils.hasText(titleHint)) {
            return "与 Lyra 的对话";
        }
        String normalized = titleHint.trim().replaceAll("\\s+", " ");
        return normalized.length() > 48 ? normalized.substring(0, 48) : normalized;
    }
}
