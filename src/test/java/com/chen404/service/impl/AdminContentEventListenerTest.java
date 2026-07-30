package com.chen404.service.impl;

import com.chen404.config.SiteOwnerProperties;
import com.chen404.config.AdminNotificationProperties;
import com.chen404.domain.entity.AdminNotification;
import com.chen404.domain.entity.User;
import com.chen404.domain.enums.AdminNotificationEventTypeEnum;
import com.chen404.domain.enums.AdminNotificationResourceTypeEnum;
import com.chen404.domain.event.AdminContentEvent;
import com.chen404.mapper.AdminNotificationMapper;
import com.chen404.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class AdminContentEventListenerTest {

    @Test
    void shouldCreateUnreadOwnerNotificationForNewArticle() {
        AdminNotificationMapper notificationMapper = mock(AdminNotificationMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        SiteOwnerProperties ownerProperties = new SiteOwnerProperties();
        ownerProperties.setSiteOwnerUserId(1L);
        AdminNotificationProperties notificationProperties = new AdminNotificationProperties();
        notificationProperties.setEnabled(true);
        User actor = new User();
        actor.setNickname("知友小陈");
        when(userMapper.selectById(7L)).thenReturn(actor);

        AdminContentEventListener listener = new AdminContentEventListener(
                notificationMapper,
                userMapper,
                ownerProperties,
                notificationProperties
        );
        listener.handle(new AdminContentEvent(
                AdminNotificationEventTypeEnum.ARTICLE_PUBLISHED,
                7L,
                AdminNotificationResourceTypeEnum.ARTICLE,
                99L,
                "夏日来信"
        ));

        ArgumentCaptor<AdminNotification> captor = ArgumentCaptor.forClass(AdminNotification.class);
        verify(notificationMapper).insert(captor.capture());
        AdminNotification notification = captor.getValue();
        assertEquals(1L, notification.getRecipientUserId());
        assertEquals(AdminNotification.UNREAD, notification.getReadStatus());
        assertEquals("知友小陈 发布了文章《夏日来信》", notification.getSummary());
        assertFalse(notification.getDedupeKey().isBlank());
    }

    @Test
    void shouldDescribeImportedReaderBookInAdminNotification() {
        AdminNotificationMapper notificationMapper = mock(AdminNotificationMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        SiteOwnerProperties ownerProperties = new SiteOwnerProperties();
        ownerProperties.setSiteOwnerUserId(1L);
        AdminNotificationProperties notificationProperties = new AdminNotificationProperties();
        notificationProperties.setEnabled(true);
        User actor = new User();
        actor.setNickname("知友小陈");
        when(userMapper.selectById(7L)).thenReturn(actor);

        AdminContentEventListener listener = new AdminContentEventListener(
                notificationMapper,
                userMapper,
                ownerProperties,
                notificationProperties
        );
        listener.handle(new AdminContentEvent(
                AdminNotificationEventTypeEnum.READER_BOOK_IMPORTED,
                7L,
                AdminNotificationResourceTypeEnum.READER_BOOK,
                42L,
                "夜航故事"
        ));

        ArgumentCaptor<AdminNotification> captor = ArgumentCaptor.forClass(AdminNotification.class);
        verify(notificationMapper).insert(captor.capture());
        AdminNotification notification = captor.getValue();
        assertEquals("知友导入了新小说", notification.getTitle());
        assertEquals("知友小陈 导入了小说《夜航故事》", notification.getSummary());
        assertEquals(AdminNotificationResourceTypeEnum.READER_BOOK.name(), notification.getResourceType());
    }

    @Test
    void duplicateOrFailedNotificationWriteShouldNotEscapeToContentTransaction() {
        AdminContentEvent event = new AdminContentEvent(
                AdminNotificationEventTypeEnum.MUSIC_TRACK_PUBLISHED,
                7L,
                AdminNotificationResourceTypeEnum.MUSIC_TRACK,
                88L,
                "夜航"
        );

        assertListenerWriteFailureIsolated(new DuplicateKeyException("duplicate"), event);
        assertListenerWriteFailureIsolated(new IllegalStateException("storage unavailable"), event);
    }

    private void assertListenerWriteFailureIsolated(RuntimeException failure, AdminContentEvent event) {
        AdminNotificationMapper notificationMapper = mock(AdminNotificationMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        doThrow(failure).when(notificationMapper).insert(org.mockito.ArgumentMatchers.any());
        SiteOwnerProperties ownerProperties = new SiteOwnerProperties();
        ownerProperties.setSiteOwnerUserId(1L);
        AdminNotificationProperties notificationProperties = new AdminNotificationProperties();
        notificationProperties.setEnabled(true);
        AdminContentEventListener listener = new AdminContentEventListener(
                notificationMapper,
                userMapper,
                ownerProperties,
                notificationProperties
        );

        assertDoesNotThrow(() -> listener.handle(event));
    }
}
