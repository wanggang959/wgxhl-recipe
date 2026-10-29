package com.wgxhl.recipe.todo.task;

import com.wgxhl.recipe.config.TenantContext;
import com.wgxhl.recipe.household.entity.Household;
import com.wgxhl.recipe.household.service.HouseholdService;
import com.wgxhl.recipe.todo.service.TodoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.time.LocalDateTime;

@Component
public class TodoNotifyTask {

    private static final Logger log = LoggerFactory.getLogger(TodoNotifyTask.class);

    private final TodoService todoService;
    private final HouseholdService householdService;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile LocalDateTime lastStartedAt;
    private volatile LocalDateTime lastFinishedAt;

    public TodoNotifyTask(TodoService todoService, HouseholdService householdService) {
        this.todoService = todoService;
        this.householdService = householdService;
    }

    @Scheduled(cron = "0 * * * * ?")
    public void scan() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Todo notify scan skipped because a previous scan is still running");
            return;
        }
        lastStartedAt = LocalDateTime.now();
        try {
            int count = 0;
            for (Household household : householdService.listActive()) {
                try {
                    count += TenantContext.callWith(household.getId(), todoService::scanAndNotify);
                } catch (Exception ex) {
                    log.error("Todo notify scan failed for household={}", household.getId(), ex);
                }
            }
            if (count > 0) log.info("Todo notify task sent {} notice(s)", count);
        } finally {
            lastFinishedAt = LocalDateTime.now();
            running.set(false);
        }
    }

    public LocalDateTime getLastStartedAt() { return lastStartedAt; }
    public LocalDateTime getLastFinishedAt() { return lastFinishedAt; }
    public boolean isRunning() { return running.get(); }
}
