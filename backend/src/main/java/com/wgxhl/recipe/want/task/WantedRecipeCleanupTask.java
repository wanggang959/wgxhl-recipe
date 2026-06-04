package com.wgxhl.recipe.want.task;

import com.wgxhl.recipe.want.service.UserWantedRecipeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 清除 planned_date 早于今天的想吃记录，避免占用购物车展示状态。
 */
@Component
public class WantedRecipeCleanupTask implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WantedRecipeCleanupTask.class);

    private final UserWantedRecipeService userWantedRecipeService;

    public WantedRecipeCleanupTask(UserWantedRecipeService userWantedRecipeService) {
        this.userWantedRecipeService = userWantedRecipeService;
    }

    /** 每天 0 点（上海时区）清理历史想吃 */
    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Shanghai")
    public void purgeExpiredDaily() {
        int removed = userWantedRecipeService.purgeExpiredBeforeToday();
        if (removed > 0) {
            log.info("Scheduled wanted recipe cleanup removed {} record(s)", removed);
        }
    }

    /** 启动时清理一次，立即修复历史脏数据 */
    @Override
    public void run(ApplicationArguments args) {
        int removed = userWantedRecipeService.purgeExpiredBeforeToday();
        if (removed > 0) {
            log.info("Startup wanted recipe cleanup removed {} record(s)", removed);
        }
    }
}
