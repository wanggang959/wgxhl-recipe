package com.wgxhl.recipe.todo.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class ReminderGuardCleanupTask {
    private static final Logger log = LoggerFactory.getLogger(ReminderGuardCleanupTask.class);
    private final JdbcTemplate db;
    public ReminderGuardCleanupTask(JdbcTemplate db) { this.db = db; }

    @Scheduled(cron = "0 45 3 * * ?", zone = "Asia/Shanghai")
    public void cleanup() {
        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusDays(90));
        int total = 0;
        for (int i = 0; i < 10; i++) {
            int removed = db.update("delete from todo_delivery_guard where status in ('SUCCESS','FAILED','SKIP') and update_time < ? limit 1000", cutoff);
            total += removed;
            if (removed < 1000) break;
        }
        if (total > 0) log.info("Scheduled reminder claim cleanup removed {} rows", total);
    }
}
