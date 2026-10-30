package com.wgxhl.recipe.todo.task;

import com.wgxhl.recipe.todo.entity.Todo;
import com.wgxhl.recipe.todo.entity.TodoNoticeRule;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class ReminderWindowTest {
    private TodoNoticeRule rule(int minutes, LocalDateTime created) {
        TodoNoticeRule rule = new TodoNoticeRule(); rule.setBeforeMinutes(minutes); rule.setCreateTime(created); return rule;
    }

    @Test
    void coalescesMissedRulesToTheMostRecent() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 12, 0);
        Todo todo = new Todo(); todo.setDueTime(now.plusHours(1)); todo.setCreateTime(now.minusDays(10));
        TodoNoticeRule old = rule(1440, now.minusDays(10));
        TodoNoticeRule recent = rule(120, now.minusDays(10));
        TodoNoticeRule future = rule(0, now.minusDays(10));
        assertSame(recent, ReminderWindow.latestEligible(todo, Arrays.asList(old, future, recent), now.minusDays(1), now));
        assertEquals(Arrays.asList(old, recent), ReminderWindow.eligible(todo, Arrays.asList(old, future, recent), now.minusDays(1), now));
    }

    @Test
    void neverReplaysTriggersThatPredateTaskOrRuleCreation() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 12, 0);
        Todo todo = new Todo(); todo.setDueTime(now.plusHours(1)); todo.setCreateTime(now.minusMinutes(20));
        TodoNoticeRule old = rule(120, now.minusMinutes(5));
        assertNull(ReminderWindow.latestEligible(todo, Arrays.asList(old), now.minusDays(1), now));
    }

    @Test
    void respectsInclusiveWindowAndDoesNotSendEarly() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 12, 0);
        Todo todo = new Todo(); todo.setDueTime(now);
        TodoNoticeRule rule = rule(6, null);
        assertSame(rule, ReminderWindow.latestEligible(todo, Arrays.asList(rule), now.minusMinutes(6), now));
        assertNull(ReminderWindow.latestEligible(todo, Arrays.asList(rule), now.minusMinutes(5), now));
        todo.setDueTime(now.plusSeconds(1)); rule.setBeforeMinutes(0);
        assertNull(ReminderWindow.latestEligible(todo, Arrays.asList(rule), now.minusMinutes(6), now));
    }
}
