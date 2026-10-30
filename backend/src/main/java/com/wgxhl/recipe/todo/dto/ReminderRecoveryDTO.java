package com.wgxhl.recipe.todo.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Arrays;

@Data
public class ReminderRecoveryDTO {
    private String householdId;
    private String todoId;
    private String ruleId;
    private String recipientId;
    private String channel;
    private LocalDateTime targetTime;
    private LocalDateTime expectedUpdateTime;
    private String action;
    private boolean confirmed;

    public boolean valid() {
        return key(householdId) && key(todoId) && key(ruleId)
                && recipientId != null && recipientId.length() <= 36
                && Arrays.asList("site", "email", "push").contains(channel)
                && targetTime != null && expectedUpdateTime != null && confirmed
                && Arrays.asList("RETRY", "CLOSE").contains(action);
    }

    private boolean key(String value) { return value != null && !value.trim().isEmpty() && value.length() <= 36; }
}
