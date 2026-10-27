package com.wgxhl.recipe.config;

import com.wgxhl.recipe.user.entity.AppUser;
import org.springframework.util.StringUtils;

public final class UserContext {

    public static final String ROLE_SUPER_ADMIN = "super_admin";
    public static final String MEMBER_OWNER = "owner";
    public static final String MEMBER_ADMIN = "admin";
    public static final String MEMBER_MEMBER = "member";
    public static final String MEMBER_GUEST = "guest";

    private static final ThreadLocal<AppUser> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_MEMBER_ROLE = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(AppUser user) {
        set(user, null);
    }

    public static void set(AppUser user, String memberRole) {
        CURRENT_USER.set(user);
        CURRENT_MEMBER_ROLE.set(memberRole);
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
        return user != null && ROLE_SUPER_ADMIN.equals(user.getUserRole());
    }

    /**
     * 当前家庭内的角色，来自 household_member.member_role
     */
    public static String memberRole() {
        return CURRENT_MEMBER_ROLE.get();
    }

    public static boolean isHouseholdOwner() {
        return MEMBER_OWNER.equals(CURRENT_MEMBER_ROLE.get());
    }

    /**
     * 家庭管理员：owner / admin；超级管理员在自己所属的家庭内同样视为管理员
     */
    public static boolean isHouseholdManager() {
        if (isSuperAdmin()) {
            return true;
        }
        String role = CURRENT_MEMBER_ROLE.get();
        return MEMBER_OWNER.equals(role) || MEMBER_ADMIN.equals(role);
    }

    public static boolean isGuest() {
        return MEMBER_GUEST.equals(CURRENT_MEMBER_ROLE.get());
    }

    public static void clear() {
        CURRENT_USER.remove();
        CURRENT_MEMBER_ROLE.remove();
    }
}
