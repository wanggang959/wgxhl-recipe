package com.wgxhl.recipe.common;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 业务日期统一按上海时区，与前端用户及定时任务保持一致。
 */
public final class AppZoneDates {

    public static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    private AppZoneDates() {
    }

    public static LocalDate today() {
        return LocalDate.now(APP_ZONE);
    }
}
