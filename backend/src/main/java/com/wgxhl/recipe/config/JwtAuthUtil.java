package com.wgxhl.recipe.config;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.wgxhl.recipe.user.entity.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtAuthUtil {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_USER_ROLE = "userRole";
    private static final String CLAIM_STATUS = "status";
    private static final String CLAIM_EXPIRE_AT = "expireAt";

    @Value("${auth.jwt.secret:wgxhl-recipe-secret-key}")
    private String secret;

    @Value("${auth.jwt.expire-hours:168}")
    private int expireHours;

    public String createToken(AppUser user) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(CLAIM_USER_ID, user.getId());
        payload.put(CLAIM_USERNAME, user.getUsername());
        payload.put(CLAIM_USER_ROLE, user.getUserRole());
        payload.put(CLAIM_STATUS, user.getStatus());
        long expireAt = System.currentTimeMillis() + expireHours * 3600L * 1000L;
        payload.put(CLAIM_EXPIRE_AT, expireAt);
        return JWTUtil.createToken(payload, secret.getBytes(StandardCharsets.UTF_8));
    }

    public JWT verifyAndGet(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        boolean valid = JWTUtil.verify(token, secret.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            return null;
        }
        JWT jwt = JWT.of(token);
        Object expireAt = jwt.getPayload(CLAIM_EXPIRE_AT);
        if (expireAt == null) {
            return null;
        }
        long expireAtMs;
        try {
            expireAtMs = Long.parseLong(String.valueOf(expireAt));
        } catch (Exception ex) {
            return null;
        }
        if (System.currentTimeMillis() >= expireAtMs) {
            return null;
        }
        return jwt;
    }

    public String getUserRole(JWT jwt) {
        if (jwt == null) {
            return null;
        }
        Object value = jwt.getPayload(CLAIM_USER_ROLE);
        return value == null ? null : String.valueOf(value);
    }
}
