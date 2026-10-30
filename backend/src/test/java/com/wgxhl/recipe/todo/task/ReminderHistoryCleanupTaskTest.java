package com.wgxhl.recipe.todo.task;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReminderHistoryCleanupTaskTest {
    @Test
    void limitsEachTableToTenSmallBatches() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.update(startsWith("DELETE FROM notification"), any(Timestamp.class), any(Timestamp.class))).thenReturn(1000);
        when(db.update(startsWith("DELETE FROM todo_send_log"), any(Timestamp.class), any(Timestamp.class), any(Timestamp.class))).thenReturn(1000);
        new ReminderHistoryCleanupTask(db).cleanup(LocalDateTime.now());
        verify(db, times(10)).update(startsWith("DELETE FROM notification"), any(Timestamp.class), any(Timestamp.class));
        verify(db, times(10)).update(startsWith("DELETE FROM todo_send_log"), any(Timestamp.class), any(Timestamp.class), any(Timestamp.class));
        verifyNoMoreInteractions(db);
    }

    @Test
    void failureInOneTableDoesNotPreventCleaningTheOther() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.update(startsWith("DELETE FROM notification"), any(Timestamp.class), any(Timestamp.class)))
                .thenThrow(new IllegalStateException("test failure"));
        new ReminderHistoryCleanupTask(db).cleanup(LocalDateTime.now());
        verify(db).update(startsWith("DELETE FROM todo_send_log"), any(Timestamp.class), any(Timestamp.class), any(Timestamp.class));
    }
}
