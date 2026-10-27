package com.wgxhl.recipe.user.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流：同一用户名连续失败 5 次锁定 10 分钟；同一来源 IP 连续失败 20 次锁定 10 分钟。
 * 单实例部署，直接放在内存里；重启后清零。
 */
@Service
public class LoginAttemptService {

    private static final int USER_MAX_FAILURES = 5;
    private static final int IP_MAX_FAILURES = 20;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);
    private static final Duration ATTEMPT_TTL = Duration.ofHours(1);
    private static final int PURGE_THRESHOLD = 500;

    private final Map<String, Attempt> byUsername = new ConcurrentHashMap<>();
    private final Map<String, Attempt> byIp = new ConcurrentHashMap<>();

    /**
     * @return 被锁定时返回提示语，否则返回 null
     */
    public String lockedMessage(String username, String clientIp) {
        String userMessage = lockedMessage(byUsername, normalize(username),
                "密码错误次数过多，请 %d 分钟后再试");
        if (userMessage != null) {
            return userMessage;
        }
        return lockedMessage(byIp, normalizeIp(clientIp),
                "该网络登录失败次数过多，请 %d 分钟后再试");
    }

    public void onFailure(String username, String clientIp) {
        record(byUsername, normalize(username), USER_MAX_FAILURES);
        record(byIp, normalizeIp(clientIp), IP_MAX_FAILURES);
    }

    public void onSuccess(String username, String clientIp) {
        remove(byUsername, normalize(username));
        remove(byIp, normalizeIp(clientIp));
    }

    private String lockedMessage(Map<String, Attempt> buckets, String key, String template) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        Attempt attempt = buckets.get(key);
        if (attempt == null || attempt.lockedUntil == null) {
            return null;
        }
        Instant now = Instant.now();
        if (attempt.lockedUntil.isAfter(now)) {
            long minutes = Math.max(1, Duration.between(now, attempt.lockedUntil).toMinutes() + 1);
            return String.format(template, minutes);
        }
        buckets.remove(key);
        return null;
    }

    private void record(Map<String, Attempt> buckets, String key, int maxFailures) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        purgeIfNeeded(buckets);
        buckets.compute(key, (ignored, current) -> {
            Attempt attempt = current == null ? new Attempt() : current;
            attempt.failures += 1;
            attempt.lastFailure = Instant.now();
            if (attempt.failures >= maxFailures) {
                attempt.lockedUntil = Instant.now().plus(LOCK_DURATION);
            }
            return attempt;
        });
    }

    private void remove(Map<String, Attempt> buckets, String key) {
        if (StringUtils.hasText(key)) {
            buckets.remove(key);
        }
    }

    private void purgeIfNeeded(Map<String, Attempt> buckets) {
        if (buckets.size() <= PURGE_THRESHOLD) {
            return;
        }
        Instant threshold = Instant.now().minus(ATTEMPT_TTL);
        buckets.entrySet().removeIf(entry -> entry.getValue().lastFailure.isBefore(threshold));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeIp(String ip) {
        return ip == null ? "" : ip.trim();
    }

    private static class Attempt {
        private int failures;
        private Instant lastFailure = Instant.now();
        private Instant lockedUntil;
    }
}
