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
public class ReminderHistoryCleanupTask {
    private static final Logger log = LoggerFactory.getLogger(ReminderHistoryCleanupTask.class);
    private static final int BATCH_SIZE = 1000;
    private static final int MAX_BATCHES = 10;
    private final JdbcTemplate db;

    public ReminderHistoryCleanupTask(JdbcTemplate db) { this.db = db; }

    @Scheduled(cron = "0 55 3 * * ?", zone = "Asia/Shanghai")
    public void cleanupDaily() {
        cleanup(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
    }

    void cleanup(LocalDateTime now) {
        Timestamp cutoff = Timestamp.valueOf(now.minusDays(90));
        deleteBatches("notification", "DELETE FROM notification WHERE is_read=1 AND update_time < ? "
                + "AND create_time < ? ORDER BY update_time, id LIMIT " + BATCH_SIZE, cutoff, cutoff);

        // Keep every row that can still affect any current rule, including legacy rows without target_time.
        String sameTodo = "t.id=todo_send_log.todo_id AND t.household_id=todo_send_log.household_id";
        String sameRule = "r.todo_id=t.id AND r.household_id=t.household_id";
        String target = "TIMESTAMPADD(MINUTE, -GREATEST(COALESCE(r.before_minutes,0),0), t.due_time)";
        deleteBatches("todo_send_log", "DELETE FROM todo_send_log WHERE create_time < ? "
                + "AND (update_time IS NULL OR update_time < ?) AND send_time < ? AND ("
                + "NOT EXISTS (SELECT 1 FROM todo t WHERE " + sameTodo + ") OR "
                + "EXISTS (SELECT 1 FROM todo t WHERE " + sameTodo
                + " AND t.due_time IS NOT NULL AND todo_send_log.send_time < t.due_time"
                + " AND EXISTS (SELECT 1 FROM todo_notice_rule r WHERE " + sameRule + ")"
                + " AND NOT EXISTS (SELECT 1 FROM todo_notice_rule r WHERE " + sameRule
                + " AND (todo_send_log.send_time >= " + target + " OR todo_send_log.target_time >= " + target + "))))"
                + " ORDER BY create_time, id LIMIT " + BATCH_SIZE, cutoff, cutoff, cutoff);
    }

    private void deleteBatches(String table, String sql, Object... args) {
        try {
            int total = 0;
            for (int i = 0; i < MAX_BATCHES; i++) {
                int removed = db.update(sql, args);
                total += removed;
                if (removed < BATCH_SIZE) break;
            }
            if (total > 0) log.info("Scheduled reminder history cleanup removed {} rows from {}", total, table);
        } catch (Exception ex) {
            log.error("Scheduled reminder history cleanup failed for {}", table, ex);
        }
    }
}
