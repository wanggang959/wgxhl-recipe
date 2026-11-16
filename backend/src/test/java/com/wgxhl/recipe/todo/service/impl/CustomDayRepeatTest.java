package com.wgxhl.recipe.todo.service.impl;

import com.wgxhl.recipe.todo.entity.Todo;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomDayRepeatTest {
    @Test void nextUsesScheduledTimeAndSkipsMissedCycles() {
        LocalDateTime due = LocalDateTime.of(2026, 11, 16, 9, 0);
        assertEquals(LocalDateTime.of(2026, 12, 16, 9, 0), CustomDayRepeat.next(due, 30, due.plusDays(3)));
        assertEquals(LocalDateTime.of(2027, 1, 15, 9, 0), CustomDayRepeat.next(due, 30, due.plusDays(30)));
        assertEquals(due.plusDays(6000), CustomDayRepeat.next(due, 1, due.plusDays(5999)));
        assertNull(CustomDayRepeat.next(due, 0, due));
    }
    @Test void validatesIntervalAndDueTime() {
        Todo todo = new Todo(); todo.setRepeatType("CUSTOM_DAYS"); todo.setRepeatIntervalDays(30);
        assertNotNull(CustomDayRepeat.validate(todo, null));
        todo.setDueTime(LocalDateTime.now()); assertNull(CustomDayRepeat.validate(todo, null));
        todo.setRepeatIntervalDays(3651); assertNotNull(CustomDayRepeat.validate(todo, null));
        todo.setRepeatIntervalDays(30); todo.setCategory("BIRTHDAY"); assertNotNull(CustomDayRepeat.validate(todo, null));
    }
}
