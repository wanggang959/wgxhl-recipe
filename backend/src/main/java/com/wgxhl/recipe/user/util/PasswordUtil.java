package com.wgxhl.recipe.user.util;

import cn.hutool.crypto.digest.BCrypt;
import org.springframework.util.StringUtils;

public final class PasswordUtil {

    private PasswordUtil() {
    }

    public static String hash(String raw) {
        return BCrypt.hashpw(raw, BCrypt.gensalt());
    }

    public static boolean isHashed(String stored) {
        return StringUtils.hasText(stored)
                && (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$"));
    }

    public static boolean matches(String raw, String stored) {
        if (!StringUtils.hasText(raw) || !StringUtils.hasText(stored)) {
            return false;
        }
        if (!isHashed(stored)) {
            return raw.equals(stored);
        }
        try {
            return BCrypt.checkpw(raw, stored);
        } catch (Exception ex) {
            return false;
        }
    }
}
