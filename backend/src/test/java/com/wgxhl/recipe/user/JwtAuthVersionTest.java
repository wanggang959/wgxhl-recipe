package com.wgxhl.recipe.user;

import cn.hutool.jwt.JWT;
import com.wgxhl.recipe.config.JwtAuthUtil;
import com.wgxhl.recipe.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtAuthVersionTest {
    @Test void oldTokensWorkUntilSecurityVersionChanges() {
        JwtAuthUtil auth = new JwtAuthUtil();
        AppUser user = new AppUser(); user.setAuthVersion(0L);
        JWT old = JWT.create().setPayload("userId", "id");
        assertTrue(auth.isVersionCurrent(old, user));
        user.setAuthVersion(1L);
        assertFalse(auth.isVersionCurrent(old, user));
        assertTrue(auth.isVersionCurrent(JWT.create().setPayload("authVersion", 1), user));
        assertFalse(auth.isVersionCurrent(JWT.create().setPayload("authVersion", "invalid"), user));
    }
}
