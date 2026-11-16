package com.wgxhl.recipe.todo.service.impl;

import com.wgxhl.recipe.todo.entity.Todo;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

final class CustomDayRepeat {
    private CustomDayRepeat() {}
    static boolean validInterval(Integer days) { return days != null && days >= 1 && days <= 3650; }
    static String validate(Todo requested, Todo existing) {
        String type = requested.getRepeatType() != null ? requested.getRepeatType() : existing == null ? null : existing.getRepeatType();
        if (!"CUSTOM_DAYS".equals(type)) return null;
        String category = requested.getCategory() != null ? requested.getCategory() : existing == null ? null : existing.getCategory();
        String calendar = requested.getDueCalendar() != null ? requested.getDueCalendar() : existing == null ? null : existing.getDueCalendar();
        if ("BIRTHDAY".equals(category) || "LUNAR".equals(calendar)) return "生日和农历待办不支持按天自定义重复";
        Integer days = requested.getRepeatIntervalDays() != null ? requested.getRepeatIntervalDays() : existing == null ? null : existing.getRepeatIntervalDays();
        if (!validInterval(days)) return "重复天数须为 1 至 3650 的整数";
        if (requested.getDueTime() == null && (existing == null || existing.getDueTime() == null)) return "请设置首次到期时间";
        return null;
    }
    static LocalDateTime next(LocalDateTime due, Integer days, LocalDateTime now) {
        if (due == null || now == null || !validInterval(days)) return null;
        long elapsed = Math.max(0, ChronoUnit.DAYS.between(due, now));
        long periods = Math.max(1, elapsed / days + 1);
        return due.plusDays(periods * days);
    }
}
