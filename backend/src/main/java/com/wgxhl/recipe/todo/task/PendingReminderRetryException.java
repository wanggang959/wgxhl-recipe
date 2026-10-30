package com.wgxhl.recipe.todo.task;

public class PendingReminderRetryException extends RuntimeException {
    public PendingReminderRetryException() { super("Reminder delivery is waiting for a bounded retry"); }
}
