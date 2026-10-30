package com.wgxhl.recipe.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class MailDataCleanupTask {
    private static final Logger log = LoggerFactory.getLogger(MailDataCleanupTask.class);
    private static final int BATCH_SIZE = 1000;
    private static final int MAX_BATCHES = 10;
    private final JdbcTemplate db;

    public MailDataCleanupTask(JdbcTemplate db) { this.db = db; }

    @Scheduled(cron = "0 35 3 * * ?", zone = "Asia/Shanghai")
    public void cleanupDaily() {
        cleanup(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
    }

    void cleanup(LocalDateTime now) {
        deleteOld("mail_delivery_event", "create_time", now.minusDays(90));
        deleteOld("mail_message_log", "create_time", now.minusDays(90));
        deleteOld("email_verification_code", "expires_at", now.minusDays(7));
    }

    private void deleteOld(String table, String column, LocalDateTime cutoff) {
        try {
            int total = 0;
            for (int i = 0; i < MAX_BATCHES; i++) {
                int removed = db.update("DELETE FROM " + table + " WHERE " + column + " < ? LIMIT " + BATCH_SIZE,
                        Timestamp.valueOf(cutoff));
                total += removed;
                if (removed < BATCH_SIZE) break;
            }
            if (total > 0) log.info("Scheduled mail data cleanup removed {} rows from {}", total, table);
        } catch (Exception ex) {
            log.error("Scheduled mail data cleanup failed for {}", table, ex);
        }
    }
}
