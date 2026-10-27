package com.wgxhl.recipe.user.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流：同一用户名连续失败 5 次锁定 10 分钟。
 * 单实例部署，直接放在内存里；重启后清零。
 */
@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);
    private static final Duration ATTEMPT_TTL = Duration.ofHours(1);
    private static final int PURGE_THRESHOLD = 500;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /**
     * @return 被锁定时返回提示语，否则返回 null
     */
    public String lockedMessage(String username) {
        Attempt attempt = attempts.get(normalize(username));
        if (attempt == null || attempt.lockedUntil == null) {
            return null;
        }
        Instant now = Instant.now();
        if (attempt.lockedUntil.isAfter(now)) {
            long minutes = Math.max(1, Duration.between(now, attempt.lockedUntil).toMinutes() + 1);
            return "密码错误次数过多，请 " + minutes + " 分钟后再试";
        }
        attempts.remove(normalize(username));
        return null;
    }

    public void onFailure(String username) {
        String key = normalize(username);
        if (!StringUtils.hasText(key)) {
            return;
        }
        purgeIfNeeded();
        attempts.compute(key, (k, current) -> {
            Attempt attempt = current == null ? new Attempt() : current;
            attempt.failures += 1;
            attempt.lastFailure = Instant.now();
            if (attempt.failures >= MAX_FAILURES) {
                attempt.lockedUntil = Instant.now().plus(LOCK_DURATION);
            }
            return attempt;
        });
    }

    public void onSuccess(String username) {
        attempts.remove(normalize(username));
    }

    private void purgeIfNeeded() {
        if (attempts.size() <= PURGE_THRESHOLD) {
            return;
        }
        Instant threshold = Instant.now().minus(ATTEMPT_TTL);
        attempts.entrySet().removeIf(entry -> entry.getValue().lastFailure.isBefore(threshold));
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static class Attempt {
        private int failures;
        private Instant lastFailure = Instant.now();
        private Instant lockedUntil;
    }
}
