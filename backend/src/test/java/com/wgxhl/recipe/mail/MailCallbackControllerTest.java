package com.wgxhl.recipe.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgxhl.recipe.mail.entity.MailDeliveryEvent;
import com.wgxhl.recipe.mail.entity.MailMessageLog;
import com.wgxhl.recipe.mail.mapper.MailDeliveryEventMapper;
import com.wgxhl.recipe.mail.mapper.MailMessageLogMapper;
import com.wgxhl.recipe.notification.service.NotificationService;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MailCallbackControllerTest {
    private static final String TOKEN = "test-callback-token";
    private final MailProperties properties = new MailProperties();
    private final MailDeliveryEventMapper events = mock(MailDeliveryEventMapper.class);
    private final MailMessageLogMapper messages = mock(MailMessageLogMapper.class);
    private final AppUserMapper users = mock(AppUserMapper.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ObjectMapper json = new ObjectMapper();
    private MailCallbackController controller;

    @BeforeEach
    void setup() {
        properties.setCallbackToken(TOKEN);
        controller = new MailCallbackController(properties, events, messages, users, notifications, json);
    }

    @Test
    void rejectsUnknownTokenAndMissingConfiguration() {
        assertEquals(HttpStatus.NOT_FOUND, controller.receive("wrong", "{\"event\":\"delivered\"}").getStatusCode());
        properties.setCallbackToken("");
        assertEquals(HttpStatus.NOT_FOUND, controller.receive(TOKEN, "{\"event\":\"delivered\"}").getStatusCode());
        verifyNoInteractions(events, messages, users, notifications);
    }

    @Test
    void rejectsMalformedAndOversizedBodies() {
        assertEquals(HttpStatus.BAD_REQUEST, controller.receive(TOKEN, null).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.receive(TOKEN, "not-json").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.receive(TOKEN, "{\"email\":\"a@b.test\"}").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                controller.receive(TOKEN, "{\"event\":\"delivered\",\"reason\":\"" + "x".repeat(9000) + "\"}").getStatusCode());
        verifyNoInteractions(events, messages, users, notifications);
    }

    @Test
    void storesDeliveredEventWithoutAlerting() {
        String body = "{\"event\":\"delivered\",\"eventid\":\"evt-1\",\"email\":\"user@example.test\","
                + "\"messageId\":\"m-1\",\"timestamp\":1761555487,\"templateId\":65716,"
                + "\"X-Tencentcloudses-Cb-Custom\":\"ignored\"}";
        assertEquals(HttpStatus.OK, controller.receive(TOKEN, body).getStatusCode());
        ArgumentCaptor<MailDeliveryEvent> captor = ArgumentCaptor.forClass(MailDeliveryEvent.class);
        verify(events).insert(captor.capture());
        MailDeliveryEvent saved = captor.getValue();
        assertEquals("evt-1", saved.getEventId());
        assertEquals("delivered", saved.getEvent());
        assertEquals("user@example.test", saved.getRecipient());
        assertEquals("m-1", saved.getMessageId());
        assertEquals(65716L, saved.getTemplateId());
        assertEquals(1761555487L, saved.getOccurredAt());
        assertNull(saved.getReason());
        verifyNoInteractions(notifications);
    }

    @Test
    void duplicateEventIsAcknowledgedWithoutAlertingAgain() {
        when(events.insert(any(MailDeliveryEvent.class))).thenThrow(new DuplicateKeyException("dup"));
        String body = "{\"event\":\"bounce\",\"eventid\":\"evt-2\",\"email\":\"user@example.test\","
                + "\"bounceType\":\"hard_bounce\",\"reason\":\"551 5.1.1 recipient is not exist\"}";
        assertEquals(HttpStatus.OK, controller.receive(TOKEN, body).getStatusCode());
        verifyNoInteractions(messages, users, notifications);
    }

    @Test
    void hardBounceAlertsSuperAdminWithMaskedRecipientAndMailKind() {
        MailMessageLog log = new MailMessageLog();
        log.setMessageId("m-2");
        log.setMailKind("PASSWORD_RESET");
        when(messages.selectOne(any())).thenReturn(log);
        AppUser admin = new AppUser();
        admin.setId("admin-wangshifu");
        when(users.selectList(any())).thenReturn(Collections.singletonList(admin));

        String body = "{\"event\":\"bounce\",\"eventid\":\"evt-3\",\"email\":\"user@example.test\","
                + "\"messageId\":\"m-2\",\"bounceType\":\"hard_bounce\",\"reason\":\"551 5.1.1 recipient is not exist\"}";
        assertEquals(HttpStatus.OK, controller.receive(TOKEN, body).getStatusCode());

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(notifications).createSiteNotice(eq("admin-wangshifu"), eq("邮件投递异常"),
                content.capture(), eq("MAIL"), anyString());
        assertTrue(content.getValue().contains("u***@example.test"));
        assertFalse(content.getValue().contains("user@example.test"));
        assertTrue(content.getValue().contains("PASSWORD_RESET"));
        assertTrue(content.getValue().contains("hard_bounce"));
    }

    @Test
    void softBounceAndDroppedAreStoredButOnlyHardBounceAlerts() {
        String soft = "{\"event\":\"bounce\",\"eventid\":\"evt-4\",\"bounceType\":\"soft_bounce\"}";
        String dropped = "{\"event\":\"dropped\",\"eventid\":\"evt-5\",\"reason\":\"suppressed\"}";
        assertEquals(HttpStatus.OK, controller.receive(TOKEN, soft).getStatusCode());
        assertEquals(HttpStatus.OK, controller.receive(TOKEN, dropped).getStatusCode());
        verify(events, times(2)).insert(any(MailDeliveryEvent.class));
        verifyNoInteractions(notifications);
    }

    @Test
    void alertFailureStillAcknowledgesTheCallback() {
        when(messages.selectOne(any())).thenThrow(new IllegalStateException("db down"));
        String body = "{\"event\":\"spamreport\",\"eventid\":\"evt-6\",\"email\":\"user@example.test\"}";
        assertEquals(HttpStatus.OK, controller.receive(TOKEN, body).getStatusCode());
    }

    @SuppressWarnings("unused")
    private List<AppUser> admins() {
        return Collections.emptyList();
    }
}
