package com.wgxhl.recipe.todo.task;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReminderGuardCleanupTaskTest {
    @Test
    void retainsUncertainClaimsAndTodoSendLogs() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        new ReminderGuardCleanupTask(db).cleanup();
        verify(db).update(contains("status in ('SUCCESS','FAILED','SKIP')"), any(Timestamp.class));
        verifyNoMoreInteractions(db);
    }
}
