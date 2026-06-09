package com.wgxhl.recipe.notification.service.impl;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.notification.entity.Notification;
import com.wgxhl.recipe.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "baseMapper", notificationMapper);
    }

    @Test
    void createSiteNotice_shouldPersistUnreadNotice() {
        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);

        Notification notice = notificationService.createSiteNotice(
                "user-1",
                "买豆腐",
                "还有30分钟。",
                "TODO",
                "todo-1"
        );

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());

        Notification saved = captor.getValue();
        assertEquals("user-1", saved.getUserId());
        assertEquals("买豆腐", saved.getTitle());
        assertEquals("还有30分钟。", saved.getContent());
        assertEquals("TODO", saved.getNotificationType());
        assertEquals("todo-1", saved.getRelatedId());
        assertFalse(saved.getIsRead());
        assertEquals("买豆腐", notice.getTitle());
    }

    @Test
    void markRead_shouldUpdateNoticeForOwner() {
        Notification notice = new Notification();
        notice.setId("notice-1");
        notice.setUserId("user-1");
        notice.setIsRead(false);
        when(notificationMapper.selectById("notice-1")).thenReturn(notice);
        when(notificationMapper.updateById(any(Notification.class))).thenReturn(1);

        ApiResponse<Void> response = notificationService.markRead("notice-1", "user-1");

        assertEquals(200, response.getStatus());
        assertTrue(notice.getIsRead());
        verify(notificationMapper).updateById(notice);
    }

    @Test
    void markRead_shouldRejectOtherUsersNotice() {
        Notification notice = new Notification();
        notice.setId("notice-1");
        notice.setUserId("user-1");
        when(notificationMapper.selectById("notice-1")).thenReturn(notice);

        ApiResponse<Void> response = notificationService.markRead("notice-1", "user-2");

        assertEquals(403, response.getStatus());
    }

    @Test
    void markRead_shouldFailWhenNoticeMissing() {
        when(notificationMapper.selectById("missing")).thenReturn(null);

        ApiResponse<Void> response = notificationService.markRead("missing", "user-1");

        assertEquals(500, response.getStatus());
        assertTrue(response.getMessage().contains("不存在"));
    }

    @Test
    void deleteRead_shouldRemoveReadNoticesForCurrentUser() {
        when(notificationMapper.delete(any())).thenReturn(2);

        ApiResponse<Void> response = notificationService.deleteRead("user-1");

        assertEquals(200, response.getStatus());
        verify(notificationMapper).delete(any());
    }

    @Test
    void deleteRead_shouldRejectWhenNotLoggedIn() {
        ApiResponse<Void> response = notificationService.deleteRead(null);

        assertEquals(500, response.getStatus());
        verify(notificationMapper, never()).delete(any());
    }
}
