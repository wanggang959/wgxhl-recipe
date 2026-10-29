package com.wgxhl.recipe.mail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named = "test.integration.enabled", matches = "true")
@Transactional
class MailDataCleanupIntegrationTest {
    @Autowired private JdbcTemplate db;
    @Autowired private MailDataCleanupTask cleanup;

    @Test
    void removesOnlyExpiredRecordsOutsideRetentionWindow() {
        LocalDateTime now = LocalDateTime.now();
        String oldMessage = UUID.randomUUID().toString();
        String newMessage = UUID.randomUUID().toString();
        String oldEvent = UUID.randomUUID().toString();
        String oldCode = UUID.randomUUID().toString();
        String activeCode = UUID.randomUUID().toString();
        db.update("insert into mail_message_log (id, message_id, recipient, mail_kind, create_time) values (?, ?, 'test@example.test', 'NOTIFICATION', ?)", oldMessage, oldMessage, Timestamp.valueOf(now.minusDays(91)));
        db.update("insert into mail_message_log (id, message_id, recipient, mail_kind, create_time) values (?, ?, 'test@example.test', 'NOTIFICATION', ?)", newMessage, newMessage, Timestamp.valueOf(now.minusDays(1)));
        db.update("insert into mail_delivery_event (id, event_id, event, create_time) values (?, ?, 'delivered', ?)", oldEvent, oldEvent, Timestamp.valueOf(now.minusDays(91)));
        db.update("insert into email_verification_code (id, email, code, expires_at) values (?, 'test@example.test', 'test', ?)", oldCode, Timestamp.valueOf(now.minusDays(8)));
        db.update("insert into email_verification_code (id, email, code, expires_at) values (?, 'test@example.test', 'test', ?)", activeCode, Timestamp.valueOf(now.plusMinutes(5)));

        cleanup.cleanup(now);
        assertEquals(0, count("mail_message_log", oldMessage));
        assertEquals(1, count("mail_message_log", newMessage));
        assertEquals(0, count("mail_delivery_event", oldEvent));
        assertEquals(0, count("email_verification_code", oldCode));
        assertEquals(1, count("email_verification_code", activeCode));
    }

    private int count(String table, String id) {
        return db.queryForObject("select count(*) from " + table + " where id = ?", Integer.class, id);
    }
}
