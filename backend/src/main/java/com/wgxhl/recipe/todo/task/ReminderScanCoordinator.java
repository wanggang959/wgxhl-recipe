package com.wgxhl.recipe.todo.task;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Component
public class ReminderScanCoordinator {
    private static final String LOCK_NAME = "wgxhl_recipe_todo_scan";
    private final JdbcTemplate db;
    public ReminderScanCoordinator(JdbcTemplate db) { this.db = db; }

    public boolean runWithLock(Runnable action) {
        Boolean acquired = db.execute((ConnectionCallback<Boolean>) connection -> {
            try (PreparedStatement lock = connection.prepareStatement("select get_lock(?, 0)")) {
                lock.setString(1, LOCK_NAME);
                try (ResultSet result = lock.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) return false;
                }
            }
            try {
                action.run();
                return true;
            } finally {
                try (PreparedStatement unlock = connection.prepareStatement("select release_lock(?)")) {
                    unlock.setString(1, LOCK_NAME);
                    unlock.executeQuery().close();
                }
            }
        });
        return Boolean.TRUE.equals(acquired);
    }

    public LocalDateTime windowStart(String householdId, LocalDateTime now) {
        Timestamp timestamp = Timestamp.valueOf(now);
        db.update("insert into todo_scan_checkpoint (household_id,last_success_time,update_time) values (?,?,?) on duplicate key update household_id=household_id",
                householdId, timestamp, timestamp);
        Timestamp previous = db.queryForObject("select last_success_time from todo_scan_checkpoint where household_id=?",
                Timestamp.class, householdId);
        if (previous == null) throw new IllegalStateException("Missing reminder scan checkpoint");
        LocalDateTime minimumWindow = now.minusMinutes(6);
        LocalDateTime previousTime = previous.toLocalDateTime();
        LocalDateTime start = previousTime.isBefore(minimumWindow) ? previousTime : minimumWindow;
        LocalDateTime limit = now.minusHours(24);
        if (start.isBefore(limit)) {
            db.update("update todo_scan_checkpoint set last_gap_start=?, last_gap_end=?, update_time=? where household_id=?",
                    Timestamp.valueOf(start), Timestamp.valueOf(limit), timestamp, householdId);
        }
        return start.isBefore(limit) ? limit : start;
    }

    public void markSuccess(String householdId, LocalDateTime scanStartedAt) {
        Timestamp time = Timestamp.valueOf(scanStartedAt);
        db.update("update todo_scan_checkpoint set last_success_time=greatest(last_success_time,?), update_time=? where household_id=?",
                time, time, householdId);
    }

    public void retainWindow(String householdId, LocalDateTime from) {
        db.update("update todo_scan_checkpoint set last_success_time=least(last_success_time,?), update_time=? where household_id=?",
                Timestamp.valueOf(from), Timestamp.valueOf(LocalDateTime.now()), householdId);
    }
}
