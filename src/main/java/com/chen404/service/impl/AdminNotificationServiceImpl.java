package com.chen404.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chen404.domain.PageResult;
import com.chen404.domain.dto.AdminNotificationActorVO;
import com.chen404.domain.dto.AdminNotificationVO;
import com.chen404.domain.entity.AdminNotification;
import com.chen404.domain.entity.User;
import com.chen404.exception.BadRequestException;
import com.chen404.exception.ResourceNotFoundException;
import com.chen404.mapper.AdminNotificationMapper;
import com.chen404.mapper.UserMapper;
import com.chen404.service.AdminNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员消息查询与状态变更实现。
 */
@Service
public class AdminNotificationServiceImpl implements AdminNotificationService {

    private static final long DEFAULT_PAGE = 1L;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private final AdminNotificationMapper adminNotificationMapper;
    private final UserMapper userMapper;

    public AdminNotificationServiceImpl(
            AdminNotificationMapper adminNotificationMapper,
            UserMapper userMapper) {
        this.adminNotificationMapper = adminNotificationMapper;
        this.userMapper = userMapper;
    }

    @Override
    public PageResult<AdminNotificationVO> listNotifications(
            Long recipientUserId,
            Integer page,
            Integer size,
            Integer readStatus,
            String eventType) {
        validateReadStatus(readStatus);
        long current = page == null || page < 1 ? DEFAULT_PAGE : page;
        long pageSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        LambdaQueryWrapper<AdminNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AdminNotification::getRecipientUserId, recipientUserId);
        if (readStatus != null) {
            wrapper.eq(AdminNotification::getReadStatus, readStatus);
        }
        if (StringUtils.hasText(eventType)) {
            wrapper.eq(AdminNotification::getEventType, eventType.trim().toUpperCase());
        }
        wrapper.orderByDesc(AdminNotification::getCreateTime).orderByDesc(AdminNotification::getId);

        Page<AdminNotification> result = adminNotificationMapper.selectPage(new Page<>(current, pageSize), wrapper);
        List<AdminNotificationVO> records = toVOList(result.getRecords());
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public long countUnread(Long recipientUserId) {
        return adminNotificationMapper.selectCount(new LambdaQueryWrapper<AdminNotification>()
                .eq(AdminNotification::getRecipientUserId, recipientUserId)
                .eq(AdminNotification::getReadStatus, AdminNotification.UNREAD));
    }

    @Override
    @Transactional
    public void markRead(Long notificationId, Long recipientUserId) {
        AdminNotification notification = requireOwnedNotification(notificationId, recipientUserId);
        if (Objects.equals(notification.getReadStatus(), AdminNotification.READ)) {
            return;
        }
        notification.setReadStatus(AdminNotification.READ);
        notification.setReadTime(LocalDateTime.now());
        adminNotificationMapper.updateById(notification);
    }

    @Override
    @Transactional
    public void markAllRead(Long recipientUserId) {
        LambdaUpdateWrapper<AdminNotification> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AdminNotification::getRecipientUserId, recipientUserId)
                .eq(AdminNotification::getReadStatus, AdminNotification.UNREAD)
                .set(AdminNotification::getReadStatus, AdminNotification.READ)
                .set(AdminNotification::getReadTime, LocalDateTime.now());
        adminNotificationMapper.update(null, wrapper);
    }

    @Override
    @Transactional
    public void deleteNotification(Long notificationId, Long recipientUserId) {
        requireOwnedNotification(notificationId, recipientUserId);
        adminNotificationMapper.deleteById(notificationId);
    }

    private AdminNotification requireOwnedNotification(Long notificationId, Long recipientUserId) {
        AdminNotification notification = adminNotificationMapper.selectById(notificationId);
        if (notification == null || !Objects.equals(notification.getRecipientUserId(), recipientUserId)) {
            throw new ResourceNotFoundException("管理员消息不存在");
        }
        return notification;
    }

    private void validateReadStatus(Integer readStatus) {
        if (readStatus == null) {
            return;
        }
        if (!Objects.equals(readStatus, AdminNotification.UNREAD)
                && !Objects.equals(readStatus, AdminNotification.READ)) {
            throw new BadRequestException("无效的消息已读状态");
        }
    }

    private List<AdminNotificationVO> toVOList(List<AdminNotification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return List.of();
        }
        Set<Long> actorIds = notifications.stream()
                .map(AdminNotification::getActorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> actorMap = actorIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(actorIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        return notifications.stream().map(notification -> toVO(notification, actorMap)).toList();
    }

    private AdminNotificationVO toVO(AdminNotification notification, Map<Long, User> actorMap) {
        AdminNotificationVO vo = new AdminNotificationVO();
        vo.setId(notification.getId());
        vo.setEventType(notification.getEventType());
        vo.setTitle(notification.getTitle());
        vo.setSummary(notification.getSummary());
        vo.setResourceType(notification.getResourceType());
        vo.setResourceId(notification.getResourceId());
        vo.setRead(Objects.equals(notification.getReadStatus(), AdminNotification.READ));
        vo.setReadTime(notification.getReadTime());
        vo.setCreateTime(notification.getCreateTime());

        User actor = actorMap.get(notification.getActorUserId());
        if (actor != null) {
            AdminNotificationActorVO actorVO = new AdminNotificationActorVO();
            actorVO.setId(actor.getId());
            actorVO.setNickname(StringUtils.hasText(actor.getNickname()) ? actor.getNickname() : actor.getUsername());
            actorVO.setAvatar(actor.getAvatar());
            vo.setActor(actorVO);
        }
        return vo;
    }
}
