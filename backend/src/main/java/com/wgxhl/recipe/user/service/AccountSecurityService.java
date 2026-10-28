package com.wgxhl.recipe.user.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.TenantContext;
import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.household.entity.Household;
import com.wgxhl.recipe.household.entity.HouseholdMember;
import com.wgxhl.recipe.household.service.HouseholdMemberService;
import com.wgxhl.recipe.household.service.HouseholdService;
import com.wgxhl.recipe.user.entity.AccountSecurityTicket;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.mapper.AccountSecurityTicketMapper;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import com.wgxhl.recipe.user.util.EmailAddress;
import com.wgxhl.recipe.user.util.PasswordUtil;
import com.wgxhl.recipe.user.util.SecurityTokenUtil;
import com.wgxhl.recipe.mail.MailDeliveryService;
import com.wgxhl.recipe.mail.MailDeliveryService.Kind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AccountSecurityService {
    private static final Logger log = LoggerFactory.getLogger(AccountSecurityService.class);
    private static final String RESET = "RESET_PASSWORD";
    private static final String CHANGE_EMAIL = "CHANGE_EMAIL";
    private static final String RESET_ACCEPTED = "如该邮箱已完成验证，将收到密码重置邮件，请查收（也请查看垃圾邮件）";
    private final AppUserMapper users;
    private final AccountSecurityTicketMapper tickets;
    private final HouseholdMemberService members;
    private final HouseholdService households;
    private final EmailSendQuotaService quotas;
    private final LoginAttemptService attempts;
    private final MailDeliveryService mailDeliveryService;

    @Value("${account.security.public-url:https://recipe.wgxhl.space}") private String publicUrl;

    public AccountSecurityService(AppUserMapper users, AccountSecurityTicketMapper tickets,
                                  HouseholdMemberService members, HouseholdService households,
                                  EmailSendQuotaService quotas, LoginAttemptService attempts,
                                  MailDeliveryService mailDeliveryService) {
        this.users = users;
        this.tickets = tickets;
        this.members = members;
        this.households = households;
        this.quotas = quotas;
        this.attempts = attempts;
        this.mailDeliveryService = mailDeliveryService;
    }

    public ApiResponse<Map<String, Object>> status() {
        AppUser user = users.selectById(UserContext.requireUserId());
        if (!active(user)) return ApiResponse.fail("账号不可用");
        return ApiResponse.success(Map.of("email", email(user.getEmail()), "verified", Boolean.TRUE.equals(user.getEmailVerified())));
    }

    public ApiResponse<Void> requestReset(String address, String ip) {
        String target = email(address);
        if (!EmailAddress.isValid(target)) return ApiResponse.fail("请输入有效的邮箱地址");
        if (!mailDeliveryService.isReady(Kind.PASSWORD_RESET)) return ApiResponse.fail("邮箱服务暂不可用，请稍后重试");
        List<AppUser> matches = users.findLoginByEmail(target);
        AppUser user = matches.size() == 1 ? matches.get(0) : null;
        // Keep public responses identical for unknown, unverified and rate-limited accounts.
        if (active(user) && Boolean.TRUE.equals(user.getEmailVerified())) {
            String error = quotas.reserve(List.of(user.getId()), List.of(target), ip);
            if (error == null) issueReset(user, null, null);
        }
        return ApiResponse.success(RESET_ACCEPTED, null);
    }

    public ApiResponse<Map<String, Object>> memberOptions(String userId) {
        AppUser target = users.selectById(userId);
        String error = ownerError(target);
        if (error != null) return ApiResponse.fail(403, error);
        String mode = isManaged(target) ? "DIRECT" : Boolean.TRUE.equals(target.getEmailVerified())
                && EmailAddress.isValid(email(target.getEmail())) ? "EMAIL" : "UNAVAILABLE";
        return ApiResponse.success(Map.of("mode", mode, "memberName", StringUtils.hasText(target.getNickname()) ? target.getNickname() : target.getUsername()));
    }

    public ApiResponse<Void> requestMemberReset(String userId, String ip) {
        AppUser target = users.selectById(userId);
        String error = ownerError(target);
        if (error != null) return ApiResponse.fail(403, error);
        if (!Boolean.TRUE.equals(target.getEmailVerified()) || !EmailAddress.isValid(email(target.getEmail()))) {
            return ApiResponse.fail("请该成员先登录并验证自己的邮箱");
        }
        if (!mailDeliveryService.isReady(Kind.PASSWORD_RESET)) return ApiResponse.fail("邮箱服务暂不可用，请稍后重试");
        error = quotas.reserve(List.of(target.getId(), UserContext.requireUserId()), List.of(email(target.getEmail())), ip);
        if (error != null) return ApiResponse.fail(error);
        return issueReset(target, UserContext.requireUserId(), TenantContext.require())
                ? ApiResponse.success("重置链接已发送至成员本人邮箱", null) : ApiResponse.fail("邮件发送失败，请稍后重试");
    }

    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> resetManaged(String userId, String currentPassword, String newPassword) {
        if (!validPassword(newPassword)) return ApiResponse.fail("新密码长度须为 8 至 64 位");
        if (Objects.equals(userId, UserContext.requireUserId())) return ApiResponse.fail("请通过修改密码入口操作");
        // Stable user lock order also covers simultaneous owner operations.
        List<String> ids = new ArrayList<>(List.of(UserContext.requireUserId(), userId == null ? "" : userId));
        Collections.sort(ids);
        Map<String, AppUser> locked = new HashMap<>();
        for (String id : ids) locked.put(id, users.lockById(id));
        AppUser target = locked.get(userId);
        String error = ownerError(target);
        if (error != null) return ApiResponse.fail(403, error);
        AppUser owner = locked.get(UserContext.requireUserId());
        String passwordError = passwordError(owner, currentPassword);
        if (passwordError != null) return ApiResponse.fail(passwordError);
        if (!isManaged(target)) return ApiResponse.fail(403, "此账号只能由成员本人通过已验证邮箱重置密码");
        users.replacePassword(target.getId(), PasswordUtil.hash(newPassword));
        log.info("Managed account password reset, owner={}, target={}, household={}", owner.getId(), target.getId(), TenantContext.require());
        return ApiResponse.success("密码已重置，成员需要重新登录", null);
    }

    public ApiResponse<Void> resetInfo(String token) {
        AccountSecurityTicket ticket = findToken(token, false);
        AppUser user = ticket == null ? null : users.selectById(ticket.getUserId());
        return validTicket(ticket, user, RESET) ? ApiResponse.success(null) : ApiResponse.fail("重置链接已失效或已使用，请重新申请");
    }

    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> confirmReset(String token, String newPassword) {
        if (!validPassword(newPassword)) return ApiResponse.fail("新密码长度须为 8 至 64 位");
        AccountSecurityTicket ticket = findToken(token, true);
        AppUser user = ticket == null ? null : users.lockById(ticket.getUserId());
        if (!validTicket(ticket, user, RESET)) return ApiResponse.fail("重置链接已失效或已使用，请重新申请");
        users.replacePassword(user.getId(), PasswordUtil.hash(newPassword));
        consume(ticket);
        log.info("Email password reset completed, user={}", user.getId());
        return ApiResponse.success("密码已重置，请重新登录", null);
    }

    public ApiResponse<Map<String, Object>> sendEmailChange(String newEmail, String currentPassword, String ip) {
        AppUser user = users.selectById(UserContext.requireUserId());
        if (!active(user) || UserContext.isGuest()) return ApiResponse.fail(403, "无法执行此操作");
        String passwordError = passwordError(user, currentPassword);
        if (passwordError != null) return ApiResponse.fail(passwordError);
        String target = email(newEmail);
        String original = email(user.getEmail());
        if (!EmailAddress.isValid(target)) return ApiResponse.fail("请输入有效的邮箱地址");
        if (target.equals(original) && Boolean.TRUE.equals(user.getEmailVerified())) return ApiResponse.fail("此邮箱已经验证");
        if (users.countEmailUsedByAnotherUser(target, user.getId()) > 0) return ApiResponse.fail("该邮箱已被使用");
        boolean needsOldCode = !original.isEmpty() && !original.equals(target);
        if (needsOldCode && !EmailAddress.isValid(original)) return ApiResponse.fail("原邮箱格式异常，请联系管理员处理");
        if (!mailDeliveryService.isReady(Kind.CODE)) return ApiResponse.fail("邮箱服务暂不可用，请稍后重试");
        List<String> recipients = needsOldCode ? List.of(original, target) : List.of(target);
        String error = quotas.reserve(List.of(user.getId()), recipients, ip);
        if (error != null) return ApiResponse.fail(error);
        String oldCode = needsOldCode ? SecurityTokenUtil.code() : null;
        String newCode = SecurityTokenUtil.code();
        AccountSecurityTicket ticket = newTicket(user, CHANGE_EMAIL);
        ticket.setNewEmail(target);
        ticket.setExpiresAt(now().plusMinutes(10));
        ticket.setCodeNewHash(PasswordUtil.hash(newCode));
        if (needsOldCode) ticket.setCodeOldHash(PasswordUtil.hash(oldCode));
        tickets.insert(ticket);
        try {
            if (needsOldCode) mailDeliveryService.sendCode(original, "更换邮箱确认", oldCode);
            mailDeliveryService.sendCode(target, "邮箱验证", newCode);
        } catch (Exception ex) {
            consume(ticket);
            log.warn("Account email verification delivery failed, user={}, type={}", user.getId(), ex.getClass().getSimpleName());
            return ApiResponse.fail("邮件发送失败，请稍后重试");
        }
        return ApiResponse.success(Map.of("requestId", ticket.getId(), "needsOldCode", needsOldCode));
    }

    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> confirmEmailChange(String requestId, String oldCode, String newCode) {
        if (!StringUtils.hasText(requestId) || requestId.length() > 36) return ApiResponse.fail("验证请求无效");
        AccountSecurityTicket ticket = tickets.lockById(requestId);
        if (ticket == null || !Objects.equals(ticket.getUserId(), UserContext.requireUserId())) return ApiResponse.fail("验证请求无效");
        AppUser user = users.lockById(ticket.getUserId());
        if (!validTicket(ticket, user, CHANGE_EMAIL)) return ApiResponse.fail("验证码已失效，请重新获取");
        boolean matches = matchesCode(newCode, ticket.getCodeNewHash())
                && (ticket.getCodeOldHash() == null || matchesCode(oldCode, ticket.getCodeOldHash()));
        if (!matches) {
            ticket.setAttempts(ticket.getAttempts() + 1);
            ticket.setUsed(ticket.getAttempts() >= 5);
            ticket.setUpdateTime(now());
            tickets.updateById(ticket);
            return ApiResponse.fail(ticket.getUsed() ? "尝试次数过多，请重新获取验证码" : "验证码不正确");
        }
        if (users.countEmailUsedByAnotherUser(ticket.getNewEmail(), user.getId()) > 0) return ApiResponse.fail("该邮箱已被使用");
        try {
            users.replaceVerifiedEmail(user.getId(), ticket.getNewEmail());
            consume(ticket);
        } catch (DuplicateKeyException ex) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.fail("该邮箱已被使用");
        }
        return ApiResponse.success("邮箱验证成功，请重新登录", null);
    }

    private boolean issueReset(AppUser user, String requesterId, String householdId) {
        String token = SecurityTokenUtil.token();
        AccountSecurityTicket ticket = newTicket(user, RESET);
        ticket.setRequesterId(requesterId);
        ticket.setInitiatorHouseholdId(householdId);
        ticket.setTokenHash(SecurityTokenUtil.hash(token));
        tickets.insert(ticket);
        try {
            mailDeliveryService.sendPasswordReset(email(user.getEmail()), resetUrl(token));
            return true;
        } catch (Exception ex) {
            consume(ticket);
            log.warn("Password reset email failed, user={}, type={}", user.getId(), ex.getClass().getSimpleName());
            return false;
        }
    }

    private String ownerError(AppUser target) {
        String currentId = UserContext.requireUserId();
        String householdId = TenantContext.require();
        Household household = households.getById(householdId);
        HouseholdMember owner = members.find(householdId, currentId);
        HouseholdMember membership = target == null ? null : members.find(householdId, target.getId());
        if (!UserContext.isHouseholdOwner() || household == null || !currentId.equals(household.getOwnerUserId())
                || !"normal".equals(household.getStatus()) || owner == null || !"normal".equals(owner.getStatus())
                || !UserContext.MEMBER_OWNER.equals(owner.getMemberRole())) return "只有当前家庭的唯一拥有者可以重置成员密码";
        if (!active(target) || membership == null || !"normal".equals(membership.getStatus())
                || UserContext.MEMBER_GUEST.equals(membership.getMemberRole()) || "guest".equals(target.getId())
                || UserContext.ROLE_SUPER_ADMIN.equals(target.getUserRole()) || currentId.equals(target.getId())) return "此账号不能通过成员重置入口操作";
        return null;
    }

    private boolean isManaged(AppUser target) {
        return "HOUSEHOLD".equals(target.getAccountOrigin())
                && Objects.equals(target.getCreatedByUserId(), UserContext.requireUserId())
                && Objects.equals(target.getCreatedHouseholdId(), TenantContext.require())
                && members.count(new LambdaQueryWrapper<HouseholdMember>().eq(HouseholdMember::getUserId, target.getId())) == 1;
    }

    private AccountSecurityTicket newTicket(AppUser user, String purpose) {
        AccountSecurityTicket ticket = new AccountSecurityTicket();
        ticket.setId(IdUtil.simpleUUID());
        ticket.setUserId(user.getId());
        ticket.setPurpose(purpose);
        ticket.setOriginalEmail(email(user.getEmail()));
        ticket.setIssuedAuthVersion(version(user));
        ticket.setExpiresAt(now().plusMinutes(30));
        ticket.setAttempts(0);
        ticket.setUsed(false);
        ticket.setCreateTime(now());
        ticket.setUpdateTime(now());
        return ticket;
    }

    private AccountSecurityTicket findToken(String token, boolean lock) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return null;
        String hash = SecurityTokenUtil.hash(token);
        return lock ? tickets.lockByToken(hash) : tickets.selectOne(new LambdaQueryWrapper<AccountSecurityTicket>()
                .eq(AccountSecurityTicket::getTokenHash, hash).eq(AccountSecurityTicket::getPurpose, RESET));
    }

    private boolean validTicket(AccountSecurityTicket ticket, AppUser user, String purpose) {
        return ticket != null && active(user) && purpose.equals(ticket.getPurpose()) && !Boolean.TRUE.equals(ticket.getUsed())
                && ticket.getAttempts() != null && ticket.getAttempts() < 5 && ticket.getExpiresAt() != null
                && ticket.getExpiresAt().isAfter(now()) && Objects.equals(ticket.getIssuedAuthVersion(), version(user))
                && Objects.equals(ticket.getOriginalEmail(), email(user.getEmail()))
                && (!RESET.equals(purpose) || Boolean.TRUE.equals(user.getEmailVerified()));
    }

    private void consume(AccountSecurityTicket ticket) {
        AccountSecurityTicket patch = new AccountSecurityTicket();
        patch.setId(ticket.getId());
        patch.setUsed(true);
        patch.setUpdateTime(now());
        tickets.updateById(patch);
    }

    private boolean active(AppUser user) { return user != null && "normal".equals(user.getStatus()); }
    private long version(AppUser user) { return user.getAuthVersion() == null ? 0L : user.getAuthVersion(); }
    private LocalDateTime now() { return LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")); }
    private String email(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private boolean validPassword(String value) { return value != null && value.length() >= 8 && value.length() <= 64 && StringUtils.hasText(value); }
    private boolean matchesCode(String code, String hash) { return code != null && code.matches("[0-9]{6}") && PasswordUtil.matches(code, hash); }

    private String passwordError(AppUser user, String password) {
        if (!active(user)) return "账号不可用";
        String key = "security:" + user.getId();
        String locked = attempts.lockedMessage(key, "");
        if (locked != null) return locked;
        if (!PasswordUtil.matches(password, user.getPassword())) {
            attempts.onFailure(key, "");
            return "当前密码不正确";
        }
        attempts.onSuccess(key, "");
        return null;
    }

    private String resetUrl(String token) {
        URI uri = URI.create(publicUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalStateException("Invalid account security public URL");
        return publicUrl.replaceAll("/+$", "") + "/#/reset-password?token=" + token;
    }
}
