package com.wgxhl.recipe.config;

import com.wgxhl.recipe.user.entity.AppUser;
import org.springframework.util.StringUtils;

public final class UserContext {

    private static final ThreadLocal<AppUser> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(AppUser user) {
        CURRENT_USER.set(user);
    }

    public static AppUser get() {
        return CURRENT_USER.get();
    }

    public static AppUser require() {
        AppUser user = CURRENT_USER.get();
        if (user == null || !StringUtils.hasText(user.getId())) {
            throw new IllegalStateException("缺少登录用户上下文");
        }
        return user;
    }

    public static String requireUserId() {
        return require().getId();
    }

    public static boolean isSuperAdmin() {
        AppUser user = CURRENT_USER.get();
        return user != null && "super_admin".equals(user.getUserRole());
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
