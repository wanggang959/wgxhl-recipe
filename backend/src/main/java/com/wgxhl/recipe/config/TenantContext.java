package com.wgxhl.recipe.config;

import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/*
 * 当前请求/任务所属家庭。所有租户表查询都依赖该上下文，缺失时直接失败而不是退化为全表查询。
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_HOUSEHOLD = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String householdId) {
        CURRENT_HOUSEHOLD.set(householdId);
    }

    public static String get() {
        return CURRENT_HOUSEHOLD.get();
    }

    public static String require() {
        String householdId = CURRENT_HOUSEHOLD.get();
        if (!StringUtils.hasText(householdId)) {
            throw new IllegalStateException("缺少家庭上下文");
        }
        return householdId;
    }

    public static void clear() {
        CURRENT_HOUSEHOLD.remove();
    }

    public static <T> T callWith(String householdId, Supplier<T> action) {
        String previous = CURRENT_HOUSEHOLD.get();
        CURRENT_HOUSEHOLD.set(householdId);
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    public static void runWith(String householdId, Runnable action) {
        callWith(householdId, () -> {
            action.run();
            return null;
        });
    }

    private static void restore(String previous) {
        if (previous == null) {
            CURRENT_HOUSEHOLD.remove();
        } else {
            CURRENT_HOUSEHOLD.set(previous);
        }
    }
}
