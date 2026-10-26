package com.wgxhl.recipe.todo.task;

import com.wgxhl.recipe.config.TenantContext;
import com.wgxhl.recipe.household.entity.Household;
import com.wgxhl.recipe.household.service.HouseholdService;
import com.wgxhl.recipe.todo.service.TodoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TodoNotifyTask {

    private static final Logger log = LoggerFactory.getLogger(TodoNotifyTask.class);

    private final TodoService todoService;
    private final HouseholdService householdService;

    public TodoNotifyTask(TodoService todoService, HouseholdService householdService) {
        this.todoService = todoService;
        this.householdService = householdService;
    }

    @Scheduled(cron = "0 */5 * * * ?")
    public void scan() {
        int count = 0;
        for (Household household : householdService.listActive()) {
            count += TenantContext.callWith(household.getId(), todoService::scanAndNotify);
        }
        if (count > 0) {
            log.info("Todo notify task sent {} notice(s)", count);
        }
    }
}
