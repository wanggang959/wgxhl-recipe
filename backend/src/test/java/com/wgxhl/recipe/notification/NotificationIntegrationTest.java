package com.wgxhl.recipe.notification;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.notification.entity.Notification;
import com.wgxhl.recipe.notification.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StringUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("dev")
class NotificationIntegrationTest {

    private static final String TEST_USER_ID = "admin-wangshifu";

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired
    private NotificationService notificationService;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    private String createdNoticeId;

    @AfterEach
    void cleanup() {
        if (StringUtils.hasText(createdNoticeId)) {
            notificationService.removeById(createdNoticeId);
            createdNoticeId = null;
        }
    }

    @Test
    void sendRealEmail_shouldDeliverToConfiguredMailbox() {
        Assumptions.assumeTrue(mailSender != null, "JavaMailSender 未配置");
        Assumptions.assumeTrue(StringUtils.hasText(mailUsername), "spring.mail.username 未配置");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(mailUsername);
        message.setSubject("【王师傅家常菜谱】邮件通知联调测试");
        message.setText("这是一封自动测试邮件，用于验证待办邮箱通知配置是否可用。");

        mailSender.send(message);
    }

    @Test
    void createSiteNotice_shouldPersistAndBeUnread() {
        long before = unreadTotal(TEST_USER_ID);

        Notification notice = notificationService.createSiteNotice(
                TEST_USER_ID,
                "站内通知联调测试",
                "这是一条自动测试站内通知。",
                "TEST",
                "integration-test"
        );
        createdNoticeId = notice.getId();

        assertNotNull(notice.getId());
        assertEquals(TEST_USER_ID, notice.getUserId());
        assertEquals("站内通知联调测试", notice.getTitle());
        assertFalse(Boolean.TRUE.equals(notice.getIsRead()));

        Notification loaded = notificationService.getById(notice.getId());
        assertNotNull(loaded);
        assertEquals("这是一条自动测试站内通知。", loaded.getContent());

        long after = unreadTotal(TEST_USER_ID);
        assertTrue(after >= before + 1);

        ApiResponse<Void> readResult = notificationService.markRead(notice.getId(), TEST_USER_ID);
        assertEquals(200, readResult.getStatus());
        assertTrue(Boolean.TRUE.equals(notificationService.getById(notice.getId()).getIsRead()));
    }

    private long unreadTotal(String userId) {
        ApiResponse<Long> response = notificationService.unreadCount(userId);
        assertEquals(200, response.getStatus());
        return response.getData() == null ? 0L : response.getData();
    }
}
