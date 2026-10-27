package com.wgxhl.recipe.user.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.entity.EmailVerificationCode;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import com.wgxhl.recipe.user.mapper.EmailVerificationCodeMapper;
import com.wgxhl.recipe.user.service.EmailVerificationCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
public class EmailVerificationCodeServiceImpl extends ServiceImpl<EmailVerificationCodeMapper, EmailVerificationCode>
        implements EmailVerificationCodeService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationCodeServiceImpl.class);
    private static final String PURPOSE_REGISTER = "REGISTER";
    private static final int CODE_EXPIRE_MINUTES = 10;
    private static final int RESEND_SECONDS = 60;
    private static final int HOURLY_LIMIT = 5;
    private static final int MAX_ATTEMPTS = 5;

    private final AppUserMapper appUserMapper;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public EmailVerificationCodeServiceImpl(AppUserMapper appUserMapper,
                                            ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.appUserMapper = appUserMapper;
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    @Override
    public ApiResponse<Void> sendRegisterCode(String email) {
        String target = normalizeEmail(email);
        if (!com.wgxhl.recipe.user.util.EmailAddress.isValid(target)) {
            return ApiResponse.fail("请输入有效的邮箱地址");
        }
        if (appUserMapper.countEmailUsedByAnotherUser(target, null) > 0) {
            return ApiResponse.fail("该邮箱已注册，请直接登录");
        }
        LocalDateTime now = LocalDateTime.now();
        EmailVerificationCode latest = lambdaQuery()
                .eq(EmailVerificationCode::getEmail, target)
                .eq(EmailVerificationCode::getPurpose, PURPOSE_REGISTER)
                .orderByDesc(EmailVerificationCode::getCreateTime)
                .last("limit 1")
                .one();
        if (latest != null && latest.getCreateTime() != null
                && latest.getCreateTime().isAfter(now.minusSeconds(RESEND_SECONDS))) {
            return ApiResponse.fail("验证码发送太频繁，请稍后再试");
        }
        long hourlyCount = lambdaQuery()
                .eq(EmailVerificationCode::getEmail, target)
                .eq(EmailVerificationCode::getPurpose, PURPOSE_REGISTER)
                .ge(EmailVerificationCode::getCreateTime, now.minusHours(1))
                .count();
        if (hourlyCount >= HOURLY_LIMIT) {
            return ApiResponse.fail("验证码发送次数过多，请稍后再试");
        }
        if (mailSender == null || !StringUtils.hasText(mailUsername)) {
            return ApiResponse.fail("邮箱服务未配置，暂时无法发送验证码");
        }

        String code = RandomUtil.randomNumbers(6);
        EmailVerificationCode entity = new EmailVerificationCode();
        entity.setId(IdUtil.simpleUUID());
        entity.setEmail(target);
        entity.setCode(code);
        entity.setPurpose(PURPOSE_REGISTER);
        entity.setExpiresAt(now.plusMinutes(CODE_EXPIRE_MINUTES));
        entity.setUsed(false);
        entity.setAttempts(0);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        save(entity);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailUsername);
            message.setTo(target);
            message.setSubject("【王师傅生活助手】注册验证码");
            message.setText("你的注册验证码是 " + code + "，"
                    + CODE_EXPIRE_MINUTES + " 分钟内有效。如非本人操作，请忽略此邮件。");
            mailSender.send(message);
        } catch (Exception ex) {
            removeById(entity.getId());
            log.warn("Register verification email failed, email={}, reason={}", maskEmail(target), ex.getMessage());
            return ApiResponse.fail("验证码发送失败，请稍后重试");
        }
        log.info("Register verification email sent, email={}", maskEmail(target));
        return ApiResponse.success("验证码已发送", null);
    }

    @Override
    public String verifyAndConsume(String email, String code) {
        String target = normalizeEmail(email);
        if (!StringUtils.hasText(code)) {
            return "请输入验证码";
        }
        EmailVerificationCode entity = lambdaQuery()
                .eq(EmailVerificationCode::getEmail, target)
                .eq(EmailVerificationCode::getPurpose, PURPOSE_REGISTER)
                .eq(EmailVerificationCode::getUsed, false)
                .orderByDesc(EmailVerificationCode::getCreateTime)
                .last("limit 1")
                .one();
        if (entity == null) {
            return "验证码已失效，请重新获取";
        }
        LocalDateTime now = LocalDateTime.now();
        if (entity.getExpiresAt() == null || entity.getExpiresAt().isBefore(now)) {
            markUsed(entity.getId());
            return "验证码已过期，请重新获取";
        }
        int attempts = entity.getAttempts() == null ? 0 : entity.getAttempts();
        if (attempts >= MAX_ATTEMPTS) {
            markUsed(entity.getId());
            return "验证码尝试次数过多，请重新获取";
        }
        if (!code.trim().equals(entity.getCode())) {
            EmailVerificationCode patch = new EmailVerificationCode();
            patch.setId(entity.getId());
            patch.setAttempts(attempts + 1);
            patch.setUsed(attempts + 1 >= MAX_ATTEMPTS);
            patch.setUpdateTime(now);
            updateById(patch);
            return "验证码错误";
        }
        markUsed(entity.getId());
        return null;
    }

    private void markUsed(String id) {
        EmailVerificationCode patch = new EmailVerificationCode();
        patch.setId(id);
        patch.setUsed(true);
        patch.setUpdateTime(LocalDateTime.now());
        updateById(patch);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
