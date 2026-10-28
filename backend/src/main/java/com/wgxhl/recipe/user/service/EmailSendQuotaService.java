package com.wgxhl.recipe.user.service;

import cn.hutool.core.util.IdUtil;
import com.wgxhl.recipe.user.entity.EmailSendQuota;
import com.wgxhl.recipe.user.mapper.EmailSendQuotaMapper;
import com.wgxhl.recipe.user.util.SecurityTokenUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmailSendQuotaService {
    private final EmailSendQuotaMapper mapper;

    public EmailSendQuotaService(EmailSendQuotaMapper mapper) { this.mapper = mapper; }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public String reserve(List<String> accounts, List<String> addresses, String ip) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        List<String> emails = addresses.stream().filter(StringUtils::hasText)
                .map(e -> e.trim().toLowerCase(Locale.ROOT)).distinct().sorted().collect(Collectors.toList());
        if (emails.isEmpty()) return "收件邮箱不能为空";
        int amount = emails.size();
        List<EmailSendQuota> locked = new ArrayList<>();
        // All requests lock IP first, then sorted account/email buckets, to prevent deadlocks.
        EmailSendQuota ipQuota = lock("IP", StringUtils.hasText(ip) ? ip.trim() : "unknown", now);
        if (ipQuota.getUsedCount() + amount > 20) return "该网络今日安全邮件发送已达上限";
        locked.add(ipQuota);
        for (String account : accounts.stream().filter(StringUtils::hasText).distinct().sorted().collect(Collectors.toList())) {
            EmailSendQuota quota = lock("ACCOUNT", account, now);
            if (quota.getUsedCount() + amount > 3) return "该账号今日安全邮件发送已达上限";
            locked.add(quota);
        }
        List<EmailSendQuota> emailBuckets = new ArrayList<>();
        for (String email : emails) {
            EmailSendQuota quota = lock("EMAIL", email, now);
            if (quota.getUsedCount() >= 3) return "该邮箱今日安全邮件发送已达上限";
            LocalDateTime lastSent = mapper.lastEmailSent(SecurityTokenUtil.hash(email), now.toLocalDate().minusDays(1));
            if (lastSent != null && lastSent.isAfter(now.minusSeconds(60))) {
                return "验证码或重置邮件发送太频繁，请至少等待 60 秒";
            }
            emailBuckets.add(quota);
        }
        for (EmailSendQuota quota : locked) increment(quota, amount, now);
        for (EmailSendQuota quota : emailBuckets) increment(quota, 1, now);
        return null;
    }

    private EmailSendQuota lock(String type, String value, LocalDateTime now) {
        String hash = SecurityTokenUtil.hash(value);
        mapper.ensure(IdUtil.simpleUUID(), type, hash, now.toLocalDate(), now);
        EmailSendQuota quota = mapper.lock(type, hash, now.toLocalDate());
        if (quota == null) throw new IllegalStateException("邮箱发送额度锁定失败");
        return quota;
    }

    private void increment(EmailSendQuota quota, int amount, LocalDateTime now) {
        quota.setUsedCount(quota.getUsedCount() + amount);
        quota.setLastSentAt(now);
        quota.setUpdateTime(now);
        mapper.updateById(quota);
    }
}
