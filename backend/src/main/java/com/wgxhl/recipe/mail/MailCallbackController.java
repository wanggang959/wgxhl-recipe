package com.wgxhl.recipe.mail;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgxhl.recipe.mail.entity.MailDeliveryEvent;
import com.wgxhl.recipe.mail.entity.MailMessageLog;
import com.wgxhl.recipe.mail.mapper.MailDeliveryEventMapper;
import com.wgxhl.recipe.mail.mapper.MailMessageLogMapper;
import com.wgxhl.recipe.notification.service.NotificationService;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/mail/ses/callback")
public class MailCallbackController {
    private static final Logger log = LoggerFactory.getLogger(MailCallbackController.class);
    private static final int MAX_BODY_BYTES = 8192;
    private static final Set<String> ALERT_EVENTS = new HashSet<>(Arrays.asList("bounce", "spamreport", "dropped"));

    private final MailProperties properties;
    private final MailDeliveryEventMapper events;
    private final MailMessageLogMapper messages;
    private final AppUserMapper users;
    private final NotificationService notifications;
    private final ObjectMapper json;

    public MailCallbackController(MailProperties properties, MailDeliveryEventMapper events,
                                  MailMessageLogMapper messages, AppUserMapper users,
                                  NotificationService notifications, ObjectMapper json) {
        this.properties = properties;
        this.events = events;
        this.messages = messages;
        this.users = users;
        this.notifications = notifications;
        this.json = json;
    }

    @PostMapping("/{token}")
    public ResponseEntity<String> receive(@PathVariable String token, @RequestBody(required = false) String body) {
        if (!callbackAllowed(token)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!StringUtils.hasText(body) || body.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
            return ResponseEntity.badRequest().build();
        }
        SesCallbackEvent payload;
        try {
            payload = json.readValue(body, SesCallbackEvent.class);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().build();
        }
        if (payload == null || !StringUtils.hasText(payload.getEvent())) {
            return ResponseEntity.badRequest().build();
        }
        MailDeliveryEvent event = toEntity(payload);
        try {
            events.insert(event);
        } catch (DuplicateKeyException ex) {
            return ResponseEntity.ok("ok");
        }
        // 只对首次出现的异常投递告警，避免重试和重复回调反复打扰
        if (ALERT_EVENTS.contains(event.getEvent()) && alertWorthy(event)) {
            alert(event);
        }
        return ResponseEntity.ok("ok");
    }

    private boolean callbackAllowed(String token) {
        String expected = properties.getCallbackToken();
        return StringUtils.hasText(expected) && token != null
                && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private MailDeliveryEvent toEntity(SesCallbackEvent payload) {
        MailDeliveryEvent event = new MailDeliveryEvent();
        event.setId(IdUtil.simpleUUID());
        String eventId = payload.getEventid();
        if (!StringUtils.hasText(eventId)) {
            eventId = payload.getMessageId() + ":" + payload.getEvent() + ":" + payload.getTimestamp();
        }
        event.setEventId(truncate(eventId, 120));
        event.setMessageId(truncate(payload.getMessageId(), 120));
        event.setEvent(truncate(payload.getEvent().toLowerCase(Locale.ROOT), 32));
        event.setRecipient(truncate(payload.getEmail(), 200));
        event.setBounceType(truncate(payload.getBounceType(), 20));
        event.setReason(truncate(payload.getReason(), 500));
        event.setOccurredAt(payload.getTimestamp());
        event.setTemplateId(payload.getTemplateId());
        event.setCreateTime(LocalDateTime.now());
        return event;
    }

    private boolean alertWorthy(MailDeliveryEvent event) {
        if (!"bounce".equals(event.getEvent())) {
            return true;
        }
        return "hard_bounce".equalsIgnoreCase(event.getBounceType());
    }

    private void alert(MailDeliveryEvent event) {
        try {
            MailMessageLog message = StringUtils.hasText(event.getMessageId())
                    ? messages.selectOne(new LambdaQueryWrapper<MailMessageLog>()
                    .eq(MailMessageLog::getMessageId, event.getMessageId()).last("limit 1"))
                    : null;
            String kind = message == null ? "未知类型" : message.getMailKind();
            String content = "收件人：" + maskEmail(event.getRecipient())
                    + "\n邮件类型：" + kind
                    + "\n事件：" + event.getEvent()
                    + (StringUtils.hasText(event.getBounceType()) ? "（" + event.getBounceType() + "）" : "")
                    + (StringUtils.hasText(event.getReason()) ? "\n原因：" + event.getReason() : "");
            List<AppUser> admins = users.selectList(new LambdaQueryWrapper<AppUser>()
                    .eq(AppUser::getUserRole, "super_admin")
                    .eq(AppUser::getStatus, "normal"));
            for (AppUser admin : admins) {
                notifications.createSiteNotice(admin.getId(), "邮件投递异常", content, "MAIL", event.getId());
            }
        } catch (Exception ex) {
            // 告警失败不能影响回调应答，否则腾讯云会持续重试
            log.warn("Mail delivery alert failed, eventId={}, type={}", event.getEventId(), ex.getClass().getSimpleName());
        }
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String truncate(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
