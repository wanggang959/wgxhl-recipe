package com.wgxhl.recipe.todo.task;

import com.wgxhl.recipe.todo.service.TodoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TodoNotifyTask {

    private static final Logger log = LoggerFactory.getLogger(TodoNotifyTask.class);

    private final TodoService todoService;

    public TodoNotifyTask(TodoService todoService) {
        this.todoService = todoService;
    }

    @Scheduled(cron = "0 */5 * * * ?")
    public void scan() {
        int count = todoService.scanAndNotify();
        if (count > 0) {
            log.info("Todo notify task sent {} notice(s)", count);
        }
    }
}
