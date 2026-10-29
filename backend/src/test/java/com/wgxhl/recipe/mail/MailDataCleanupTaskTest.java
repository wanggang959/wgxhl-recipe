package com.wgxhl.recipe.mail;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MailDataCleanupTaskTest {
    @Test
    void cleansOnlyOldMailEventsMessagesAndExpiredCodes() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 3, 35);
        new MailDataCleanupTask(db).cleanup(now);
        verify(db).update(contains("mail_delivery_event WHERE create_time"), eq(Timestamp.valueOf(now.minusDays(90))));
        verify(db).update(contains("mail_message_log WHERE create_time"), eq(Timestamp.valueOf(now.minusDays(90))));
        verify(db).update(contains("email_verification_code WHERE expires_at"), eq(Timestamp.valueOf(now.minusDays(7))));
        verifyNoMoreInteractions(db);
    }

    @Test
    void boundsBatchesAndContinuesAfterOneTableFails() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.update(contains("mail_delivery_event"), any(Timestamp.class))).thenThrow(new IllegalStateException("test"));
        when(db.update(contains("mail_message_log"), any(Timestamp.class))).thenReturn(1000);
        new MailDataCleanupTask(db).cleanup(LocalDateTime.of(2026, 10, 29, 3, 35));
        verify(db, times(10)).update(contains("mail_message_log"), any(Timestamp.class));
        verify(db).update(contains("email_verification_code"), any(Timestamp.class));
    }
}
