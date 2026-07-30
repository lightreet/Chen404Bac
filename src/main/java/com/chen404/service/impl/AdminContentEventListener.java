package com.chen404.service.impl;

import com.chen404.config.SiteOwnerProperties;
import com.chen404.config.AdminNotificationProperties;
import com.chen404.domain.entity.AdminNotification;
import com.chen404.domain.entity.User;
import com.chen404.domain.enums.AdminNotificationEventTypeEnum;
import com.chen404.domain.event.AdminContentEvent;
import com.chen404.mapper.AdminNotificationMapper;
import com.chen404.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

/**
 * 在业务事务提交后生成管理员消息，消息失败不反向回滚已成功的内容写入。
 */
@Slf4j
@Component
public class AdminContentEventListener {

    private static final int MAX_SUMMARY_LENGTH = 500;

    private final AdminNotificationMapper adminNotificationMapper;
    private final UserMapper userMapper;
    private final SiteOwnerProperties siteOwnerProperties;
    private final AdminNotificationProperties adminNotificationProperties;

    public AdminContentEventListener(
            AdminNotificationMapper adminNotificationMapper,
            UserMapper userMapper,
            SiteOwnerProperties siteOwnerProperties,
            AdminNotificationProperties adminNotificationProperties) {
        this.adminNotificationMapper = adminNotificationMapper;
        this.userMapper = userMapper;
        this.siteOwnerProperties = siteOwnerProperties;
        this.adminNotificationProperties = adminNotificationProperties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(AdminContentEvent event) {
        if (!adminNotificationProperties.isEnabled()) {
            return;
        }
        if (event == null || event.eventType() == null || event.resourceType() == null || event.resourceId() == null) {
            log.warn("[ADMIN_NOTIFICATION_SKIP] reason=invalid_event event={}", event);
            return;
        }

        Long recipientUserId = siteOwnerProperties.getSiteOwnerUserId();
        AdminNotification notification = new AdminNotification();
        notification.setRecipientUserId(recipientUserId);
        notification.setEventType(event.eventType().name());
        notification.setActorUserId(event.actorUserId());
        notification.setResourceType(event.resourceType().name());
        notification.setResourceId(event.resourceId());
        notification.setTitle(resolveTitle(event.eventType()));
        notification.setSummary(truncate(buildSummary(event), MAX_SUMMARY_LENGTH));
        notification.setReadStatus(AdminNotification.UNREAD);
        notification.setDedupeKey(buildDedupeKey(recipientUserId, event));

        try {
            adminNotificationMapper.insert(notification);
            log.info("[ADMIN_NOTIFICATION_CREATED] notificationId={} eventType={} resourceType={} resourceId={} actorUserId={}",
                    notification.getId(),
                    event.eventType(),
                    event.resourceType(),
                    event.resourceId(),
                    event.actorUserId());
        } catch (DuplicateKeyException duplicate) {
            log.info("[ADMIN_NOTIFICATION_DUPLICATE] dedupeKey={}", notification.getDedupeKey());
        } catch (Exception ex) {
            log.error("[ADMIN_NOTIFICATION_CREATE_FAIL] eventType={} resourceType={} resourceId={} message={}",
                    event.eventType(),
                    event.resourceType(),
                    event.resourceId(),
                    ex.getMessage(),
                    ex);
        }
    }

    private String resolveTitle(AdminNotificationEventTypeEnum eventType) {
        return switch (eventType) {
            case ARTICLE_CREATED -> "知友新建了文章草稿";
            case ARTICLE_PUBLISHED -> "知友发布了新文章";
            case TRAVEL_MEMORY_CREATED -> "知友新增了旅行地点";
            case MUSIC_TRACK_CREATED -> "知友上传了音乐草稿";
            case MUSIC_TRACK_PUBLISHED -> "知友发布了新音乐";
            case READER_BOOK_IMPORTED -> "知友导入了新小说";
            case TRUST_REQUEST_CREATED -> "有新的知友申请";
        };
    }

    private String buildSummary(AdminContentEvent event) {
        String actorName = resolveActorName(event.actorUserId());
        String resourceTitle = StringUtils.hasText(event.resourceTitle()) ? event.resourceTitle().trim() : "未命名内容";
        return switch (event.eventType()) {
            case ARTICLE_CREATED -> actorName + " 新建了文章草稿《" + resourceTitle + "》";
            case ARTICLE_PUBLISHED -> actorName + " 发布了文章《" + resourceTitle + "》";
            case TRAVEL_MEMORY_CREATED -> actorName + " 新增了旅行地点《" + resourceTitle + "》";
            case MUSIC_TRACK_CREATED -> actorName + " 上传了音乐草稿《" + resourceTitle + "》";
            case MUSIC_TRACK_PUBLISHED -> actorName + " 发布了音乐《" + resourceTitle + "》";
            case READER_BOOK_IMPORTED -> actorName + " 导入了小说《" + resourceTitle + "》";
            case TRUST_REQUEST_CREATED -> actorName + " 提交了知友申请";
        };
    }

    private String resolveActorName(Long actorUserId) {
        if (actorUserId == null) {
            return "系统用户";
        }
        User user = userMapper.selectById(actorUserId);
        if (user == null) {
            return "用户 " + actorUserId;
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname().trim() : user.getUsername();
    }

    private String buildDedupeKey(Long recipientUserId, AdminContentEvent event) {
        return "ADMIN:%s:%s:%s:%s".formatted(
                recipientUserId,
                event.eventType().name(),
                event.resourceType().name(),
                event.resourceId()
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
