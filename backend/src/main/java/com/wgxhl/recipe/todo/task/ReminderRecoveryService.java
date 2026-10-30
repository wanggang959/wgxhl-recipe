package com.wgxhl.recipe.todo.task;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.TenantContext;
import com.wgxhl.recipe.todo.dto.ReminderRecoveryDTO;
import com.wgxhl.recipe.todo.service.TodoService;
import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ReminderRecoveryService {
    private final ReminderScanCoordinator coordinator;
    private final TodoService todos;
    public ReminderRecoveryService(ReminderScanCoordinator coordinator, TodoService todos) {
        this.coordinator = coordinator; this.todos = todos;
    }

    public ApiResponse<Void> recover(ReminderRecoveryDTO request, String actor) {
        if (request == null || !request.valid()) return ApiResponse.fail("请确认处理方式并刷新记录后重试");
        AtomicReference<ApiResponse<Void>> result = new AtomicReference<>();
        boolean acquired = coordinator.runWithLock(() -> result.set(TenantContext.callWith(request.getHouseholdId(),
                () -> todos.recoverReminder(request, actor))));
        return acquired ? result.get() : ApiResponse.fail("提醒正在发送或处理中，请稍后刷新再试");
    }
}
