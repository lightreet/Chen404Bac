package com.chen404.domain.event;

import com.chen404.domain.enums.AdminNotificationEventTypeEnum;
import com.chen404.domain.enums.AdminNotificationResourceTypeEnum;

/**
 * 内容写入成功后发布的轻量业务事件。
 */
public record AdminContentEvent(
        AdminNotificationEventTypeEnum eventType,
        Long actorUserId,
        AdminNotificationResourceTypeEnum resourceType,
        Long resourceId,
        String resourceTitle) {
}
