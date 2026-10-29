package com.wgxhl.recipe.user.service;

import com.wgxhl.recipe.user.util.SecurityTokenUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class LoginAttemptService {
    private final JdbcTemplate db;
    public LoginAttemptService(JdbcTemplate db) { this.db = db; }

    public String lockedMessage(String username, String clientIp) {
        String message = locked("USER", username, "密码错误次数过多，请 %d 分钟后再试");
        return message != null ? message : locked("IP", clientIp, "该网络登录失败次数过多，请 %d 分钟后再试");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onFailure(String username, String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        record("USER", username, 5, now);
        record("IP", clientIp, 20, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onSuccess(String username, String clientIp) {
        remove("USER", username);
        remove("IP", clientIp);
    }

    private String locked(String type, String value, String template) {
        if (!StringUtils.hasText(value)) return null;
        List<Timestamp> locks = db.query("select locked_until from login_attempt_bucket where bucket_type=? and bucket_hash=?",
                (rs, row) -> rs.getTimestamp(1), type, hash(value));
        if (locks.isEmpty() || locks.get(0) == null) return null;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = locks.get(0).toLocalDateTime();
        if (!until.isAfter(now)) return null;
        long minutes = Math.max(1, (Duration.between(now, until).getSeconds() + 59) / 60);
        return String.format(template, minutes);
    }

    private void record(String type, String value, int limit, LocalDateTime now) {
        if (!StringUtils.hasText(value)) return;
        Timestamp time = Timestamp.valueOf(now);
        db.update("insert into login_attempt_bucket (bucket_type,bucket_hash,failures,last_failure,locked_until) values (?,?,1,?,null) "
                        + "on duplicate key update failures=if(last_failure<? or (locked_until is not null and locked_until<=?),1,failures+1), "
                        + "locked_until=if(failures>=?,?,null), last_failure=?",
                type, hash(value), time, Timestamp.valueOf(now.minusHours(1)), time, limit, Timestamp.valueOf(now.plusMinutes(10)), time);
    }

    private void remove(String type, String value) {
        if (StringUtils.hasText(value)) db.update("delete from login_attempt_bucket where bucket_type=? and bucket_hash=?", type, hash(value));
    }

    private String hash(String value) { return SecurityTokenUtil.hash(value.trim().toLowerCase(Locale.ROOT)); }

    @Scheduled(cron = "0 15 4 * * ?", zone = "Asia/Shanghai")
    public void cleanup() {
        db.update("delete from login_attempt_bucket where last_failure<? and (locked_until is null or locked_until<?) limit 10000",
                Timestamp.valueOf(LocalDateTime.now().minusDays(1)), Timestamp.valueOf(LocalDateTime.now()));
    }
}
