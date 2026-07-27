package com.chen404.service;

import com.chen404.domain.PageResult;
import com.chen404.domain.dto.AdminNotificationVO;

/**
 * 管理员消息查询与已读状态服务。
 */
public interface AdminNotificationService {

    PageResult<AdminNotificationVO> listNotifications(
            Long recipientUserId,
            Integer page,
            Integer size,
            Integer readStatus,
            String eventType);

    long countUnread(Long recipientUserId);

    void markRead(Long notificationId, Long recipientUserId);

    void markAllRead(Long recipientUserId);

    void deleteNotification(Long notificationId, Long recipientUserId);
}
