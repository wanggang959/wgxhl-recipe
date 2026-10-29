package com.wgxhl.recipe.todo.task;

import com.wgxhl.recipe.todo.entity.Todo;
import com.wgxhl.recipe.todo.entity.TodoNoticeRule;
import java.time.LocalDateTime;
import java.util.List;

/** Selects only the most recent eligible rule when a scan covers missed intervals. */
public final class ReminderWindow {
    private ReminderWindow() {}

    public static TodoNoticeRule latestEligible(Todo todo, List<TodoNoticeRule> rules,
                                                LocalDateTime from, LocalDateTime now) {
        if (todo == null || todo.getDueTime() == null || rules == null || from == null || now == null) return null;
        TodoNoticeRule selected = null;
        LocalDateTime latest = null;
        for (TodoNoticeRule rule : rules) {
            int minutes = Math.max(0, rule.getBeforeMinutes() == null ? 0 : rule.getBeforeMinutes());
            LocalDateTime target = todo.getDueTime().minusMinutes(minutes);
            if (target.isBefore(from) || target.isAfter(now)
                    || (todo.getCreateTime() != null && target.isBefore(todo.getCreateTime()))
                    || (rule.getCreateTime() != null && target.isBefore(rule.getCreateTime()))) continue;
            if (latest == null || target.isAfter(latest)) { selected = rule; latest = target; }
        }
        return selected;
    }
}
