package com.wgxhl.recipe.user;

import cn.hutool.core.util.IdUtil;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.JwtAuthUtil;
import com.wgxhl.recipe.config.TenantContext;
import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.household.entity.Household;
import com.wgxhl.recipe.household.entity.HouseholdMember;
import com.wgxhl.recipe.household.service.HouseholdMemberService;
import com.wgxhl.recipe.household.service.HouseholdService;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import com.wgxhl.recipe.user.service.AccountSecurityService;
import com.wgxhl.recipe.user.service.AppUserService;
import com.wgxhl.recipe.user.service.EmailSendQuotaService;
import com.wgxhl.recipe.user.service.EmailVerificationCodeService;
import com.wgxhl.recipe.user.util.PasswordUtil;
import com.wgxhl.recipe.user.util.SecurityTokenUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties = {
        "app.mail.provider=smtp",
        "spring.mail.username=security-test@example.test",
        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
})
@ActiveProfiles("integration")
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "test.integration.enabled", matches = "true")
class AccountSecurityIntegrationTest {
    @Autowired AccountSecurityService service;
    @Autowired AppUserService appUsers;
    @Autowired AppUserMapper users;
    @Autowired HouseholdMemberService members;
    @Autowired HouseholdService households;
    @Autowired EmailSendQuotaService quotas;
    @Autowired EmailVerificationCodeService registerCodes;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtAuthUtil jwt;
    @Autowired MockMvc mvc;
    @MockBean JavaMailSender mail;
    private final List<String> userIds = new ArrayList<>();
    private String home;
    private AppUser owner;

    @BeforeEach void setup() {
        owner = user(true);
        home = IdUtil.simpleUUID();
        Household household = new Household();
        household.setId(home); household.setOwnerUserId(owner.getId()); household.setHouseholdName("Security test"); household.setStatus("normal");
        households.save(household);
        addMember(owner, home, "owner");
        UserContext.set(owner, "owner"); TenantContext.set(home);
    }

    @AfterEach void cleanup() {
        UserContext.clear(); TenantContext.clear();
        for (String id : userIds) {
            jdbc.update("delete from account_security_ticket where user_id=?", id);
            jdbc.update("delete from household_member where user_id=?", id);
            jdbc.update("delete from app_user where id=?", id);
        }
        jdbc.update("delete from household where id=?", home);
        userIds.clear();
    }

    @Test void resetIsSingleUseAndInvalidatesAllOldSessions() {
        AppUser target = user(true);
        String originalToken = jwt.createToken(target, home);
        assertEquals(200, service.requestReset(target.getEmail(), IdUtil.simpleUUID()).getStatus());
        String token = resetToken(sentMessages().get(0));
        assertEquals(200, service.resetInfo(token).getStatus());
        assertEquals(200, service.resetInfo(token).getStatus());
        String hash = jdbc.queryForObject("select token_hash from account_security_ticket where user_id=?", String.class, target.getId());
        assertEquals(SecurityTokenUtil.hash(token), hash);
        assertEquals(200, service.confirmReset(token, "new-password-123").getStatus());
        assertTrue(PasswordUtil.matches("new-password-123", users.selectById(target.getId()).getPassword()));
        assertFalse(jwt.isVersionCurrent(jwt.verifyAndGet(originalToken), users.selectById(target.getId())));
        assertNotEquals(200, service.confirmReset(token, "another-password").getStatus());
    }

    @Test void onlyOneConcurrentPasswordResetCanSucceed() throws Exception {
        AppUser target = user(true);
        service.requestReset(target.getEmail(), IdUtil.simpleUUID());
        String token = resetToken(sentMessages().get(0));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> reset = () -> { start.await(); return service.confirmReset(token, "race-password-123").getStatus() == 200; };
            Future<Boolean> first = pool.submit(reset), second = pool.submit(reset);
            start.countDown();
            assertNotEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }

    @Test void changingPasswordOrEmailInvalidatesPreviouslyIssuedLink() {
        AppUser target = user(true);
        service.requestReset(target.getEmail(), IdUtil.simpleUUID());
        String token = resetToken(sentMessages().get(0));
        users.replacePassword(target.getId(), PasswordUtil.hash("other-password"));
        assertNotEquals(200, service.confirmReset(token, "new-password-123").getStatus());
    }

    @Test void expiredAndUnverifiedResetRequestsNeverChangePassword() {
        AppUser target = user(false);
        assertEquals(200, service.requestReset(target.getEmail(), IdUtil.simpleUUID()).getStatus());
        verify(mail, never()).send(any(SimpleMailMessage.class));
        jdbc.update("update app_user set email_verified=1 where id=?", target.getId());
        ageEmail(target.getEmail());
        service.requestReset(target.getEmail(), IdUtil.simpleUUID());
        String token = resetToken(sentMessages().get(0));
        jdbc.update("update account_security_ticket set expires_at=? where user_id=?", LocalDateTime.now().minusDays(1), target.getId());
        assertNotEquals(200, service.confirmReset(token, "new-password-123").getStatus());
        assertTrue(PasswordUtil.matches("password-123", users.selectById(target.getId()).getPassword()));
    }

    @Test void bindEmailRequiresPasswordAndOneTimeCode() {
        AppUser target = user(false);
        jdbc.update("update app_user set email=null where id=?", target.getId());
        target = users.selectById(target.getId());
        UserContext.set(target, "member");
        String address = IdUtil.simpleUUID() + "@example.test";
        assertNotEquals(200, service.sendEmailChange(address, "wrong", IdUtil.simpleUUID()).getStatus());
        ApiResponse<Map<String, Object>> sent = service.sendEmailChange(address, "password-123", IdUtil.simpleUUID());
        assertEquals(200, sent.getStatus());
        assertEquals(false, sent.getData().get("needsOldCode"));
        String requestId = sent.getData().get("requestId").toString();
        String code = code(sentMessages().get(0));
        String stored = jdbc.queryForObject("select code_new_hash from account_security_ticket where id=?", String.class, requestId);
        assertNotEquals(code, stored);
        assertEquals(200, service.confirmEmailChange(requestId, null, code).getStatus());
        AppUser updated = users.selectById(target.getId());
        assertEquals(address, updated.getEmail());
        assertTrue(updated.getEmailVerified());
        assertEquals(1L, updated.getAuthVersion());
        assertNotEquals(200, service.confirmEmailChange(requestId, null, code).getStatus());
    }

    @Test void changingExistingEmailRequiresBothMailboxesAndRejectsAnotherUser() {
        AppUser target = user(true);
        UserContext.set(target, "member");
        String address = IdUtil.simpleUUID() + "@example.test";
        Map<String, Object> result = service.sendEmailChange(address, "password-123", IdUtil.simpleUUID()).getData();
        assertEquals(true, result.get("needsOldCode"));
        List<SimpleMailMessage> sent = sentMessages();
        assertEquals(2, sent.size());
        String requestId = result.get("requestId").toString();
        UserContext.set(owner, "owner");
        assertNotEquals(200, service.confirmEmailChange(requestId, code(sent.get(0)), code(sent.get(1))).getStatus());
        UserContext.set(target, "member");
        String wrongOld = code(sent.get(0)).equals("000000") ? "111111" : "000000";
        assertNotEquals(200, service.confirmEmailChange(requestId, wrongOld, code(sent.get(1))).getStatus());
        assertEquals(target.getEmail(), users.selectById(target.getId()).getEmail());
        assertEquals(200, service.confirmEmailChange(requestId, code(sent.get(0)), code(sent.get(1))).getStatus());
        assertEquals(address, users.selectById(target.getId()).getEmail());
    }

    @Test void verificationLocksAfterFiveWrongCodesAndDuplicateEmailIsRejected() {
        AppUser target = user(false);
        UserContext.set(target, "member");
        assertNotEquals(200, service.sendEmailChange(owner.getEmail(), "password-123", IdUtil.simpleUUID()).getStatus());
        String requestId = service.sendEmailChange(target.getEmail(), "password-123", IdUtil.simpleUUID()).getData().get("requestId").toString();
        String correct = code(sentMessages().get(0));
        String wrong = correct.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) assertNotEquals(200, service.confirmEmailChange(requestId, null, wrong).getStatus());
        assertNotEquals(200, service.confirmEmailChange(requestId, null, correct).getStatus());
        assertFalse(users.selectById(target.getId()).getEmailVerified());
    }

    @Test void ownerCannotResetIndependentMultiHouseholdOrProtectedAccountsDirectly() {
        AppUser target = user(true);
        addMember(target, home, "member");
        assertEquals("EMAIL", service.memberOptions(target.getId()).getData().get("mode"));
        assertEquals(403, service.resetManaged(target.getId(), "password-123", "new-password-123").getStatus());
        jdbc.update("update app_user set account_origin='HOUSEHOLD',created_by_user_id=?,created_household_id=? where id=?", owner.getId(), home, target.getId());
        assertEquals("DIRECT", service.memberOptions(target.getId()).getData().get("mode"));
        assertNotEquals(200, service.resetManaged(target.getId(), "wrong", "new-password-123").getStatus());
        assertEquals(200, service.resetManaged(target.getId(), "password-123", "new-password-123").getStatus());
        addMember(target, "other-home-" + home.substring(0, 10), "member");
        assertEquals(403, service.resetManaged(target.getId(), "password-123", "other-password").getStatus());
        assertEquals(403, service.memberOptions("admin-wangshifu").getStatus());
        UserContext.set(owner, "admin");
        assertEquals(403, service.memberOptions(target.getId()).getStatus());
    }

    @Test void ordinaryProfileUpdateCannotChangePasswordsOrMailbox() {
        AppUser target = user(true);
        addMember(target, home, "member");
        AppUser patch = new AppUser(); patch.setId(target.getId()); patch.setPassword("new-password");
        assertEquals(403, appUsers.update(patch).getStatus());
        patch.setPassword(null); patch.setEmail("attacker@example.test");
        assertEquals(403, appUsers.update(patch).getStatus());
        assertEquals(target.getEmail(), users.selectById(target.getId()).getEmail());
        assertTrue(PasswordUtil.matches("password-123", users.selectById(target.getId()).getPassword()));
    }

    @Test void profileUpdateIgnoresSecurityMetadataAndBlankPassword() {
        AppUser target = user(true);
        addMember(target, home, "member");
        AppUser patch = new AppUser(); patch.setId(target.getId()); patch.setPassword("   ");
        patch.setAuthVersion(999L); patch.setEmailVerified(false); patch.setAccountOrigin("HOUSEHOLD");
        patch.setCreatedByUserId(owner.getId()); patch.setCreatedHouseholdId(home);
        assertEquals(200, appUsers.update(patch).getStatus());
        AppUser updated = users.selectById(target.getId());
        assertEquals(0L, updated.getAuthVersion()); assertTrue(updated.getEmailVerified());
        assertEquals("EMAIL", updated.getAccountOrigin()); assertNull(updated.getCreatedByUserId());
        assertTrue(PasswordUtil.matches("password-123", updated.getPassword()));
    }

    @Test void publicResetRoutesWorkWithoutLoginButEmailSecurityRequiresCurrentSession() throws Exception {
        mvc.perform(post("/user/security/resetInfo").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"invalid\"}"))
                .andExpect(jsonPath("$.status").value(500));
        mvc.perform(post("/user/security/status")).andExpect(jsonPath("$.status").value(401));
        String token = jwt.createToken(owner, home);
        mvc.perform(post("/user/security/status").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value(200));
        users.replacePassword(owner.getId(), PasswordUtil.hash("new-password-123"));
        mvc.perform(post("/user/security/status").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test void currentPasswordChecksAreRateLimited() {
        AppUser target = user(false);
        UserContext.set(target, "member");
        for (int i = 0; i < 5; i++) assertNotEquals(200, service.sendEmailChange(target.getEmail(), "wrong", IdUtil.simpleUUID()).getStatus());
        assertNotEquals(200, service.sendEmailChange(target.getEmail(), "password-123", IdUtil.simpleUUID()).getStatus());
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test void limitsAreSharedAcrossFeaturesAndRestartSafe() {
        AppUser target = user(false);
        String ip = IdUtil.simpleUUID();
        UserContext.set(target, "member");
        assertEquals(200, service.sendEmailChange(target.getEmail(), "password-123", ip).getStatus());
        assertNotNull(quotas.reserve(List.of(target.getId()), List.of(target.getEmail()), ip));
        ageEmail(target.getEmail());
        jdbc.update("update app_user set email_verified=1 where id=?", target.getId());
        assertEquals(200, service.requestReset(target.getEmail(), ip).getStatus());
        ageEmail(target.getEmail());
        assertNull(quotas.reserve(List.of(target.getId()), List.of(target.getEmail()), ip));
        ageEmail(target.getEmail());
        assertNotNull(quotas.reserve(List.of(target.getId()), List.of(target.getEmail()), IdUtil.simpleUUID()));
        assertNotNull(quotas.reserve(List.of(target.getId()), List.of(IdUtil.simpleUUID() + "@example.test"), ip));
        assertNotNull(quotas.reserve(List.of(), List.of(target.getEmail()), IdUtil.simpleUUID()));
    }

    @Test void ipDailyLimitAndAllOrNothingReservation() {
        String ip = IdUtil.simpleUUID();
        for (int i = 0; i < 20; i++) assertNull(quotas.reserve(List.of(), List.of(IdUtil.simpleUUID() + "@example.test"), ip));
        assertNotNull(quotas.reserve(List.of(), List.of(IdUtil.simpleUUID() + "@example.test"), ip));
        String account = IdUtil.simpleUUID(), mailbox = IdUtil.simpleUUID() + "@example.test";
        assertNotNull(quotas.reserve(List.of(account), List.of(mailbox), ip));
        assertEquals(0, jdbc.queryForObject("select count(*) from email_send_quota where bucket_type='ACCOUNT' and bucket_hash=?", Integer.class, SecurityTokenUtil.hash(account)));
    }

    @Test void concurrentReservationsCannotExceedTheDailyAccountLimit() throws Exception {
        String account = IdUtil.simpleUUID();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 6; i++) results.add(pool.submit(() -> {
                start.await(); return quotas.reserve(List.of(account), List.of(IdUtil.simpleUUID() + "@example.test"), IdUtil.simpleUUID()) == null;
            }));
            start.countDown();
            int accepted = 0;
            for (Future<Boolean> result : results) if (result.get(15, TimeUnit.SECONDS)) accepted++;
            assertEquals(3, accepted);
        } finally { pool.shutdownNow(); }
    }

    @Test void registerCodesShareEmailQuotaAndAreStoredHashed() {
        String address = IdUtil.simpleUUID() + "@example.test", ip = IdUtil.simpleUUID();
        assertEquals(200, registerCodes.sendRegisterCode(address, ip).getStatus());
        String code = code(sentMessages().get(0));
        String stored = jdbc.queryForObject("select code from email_verification_code where email=?", String.class, address);
        assertTrue(PasswordUtil.isHashed(stored));
        assertNotNull(quotas.reserve(List.of(), List.of(address), IdUtil.simpleUUID()));
        assertNull(registerCodes.verifyAndConsume(address, code));
        assertNotNull(registerCodes.verifyAndConsume(address, code));
        jdbc.update("delete from email_verification_code where email=?", address);
    }

    @Test void midnightDoesNotBypassSixtySecondInterval() {
        String address = IdUtil.simpleUUID() + "@example.test", hash = SecurityTokenUtil.hash(address);
        // A prior-day bucket can still contain a recent timestamp (clock/day rollover).
        jdbc.update("insert into email_send_quota(id,bucket_type,bucket_hash,quota_date,used_count,last_sent_at,create_time,update_time) values (?,'EMAIL',?,?,1,?,?,?)",
                IdUtil.simpleUUID(), hash, LocalDate.now().minusDays(1), LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
        assertNotNull(quotas.reserve(List.of(), List.of(address), IdUtil.simpleUUID()));
    }

    @Test void smtpFailuresConsumeReservedQuotaWithoutLeavingUsableResetTicket() {
        AppUser target = user(true);
        doThrow(new org.springframework.mail.MailSendException("simulated")).when(mail).send(any(SimpleMailMessage.class));
        assertEquals(200, service.requestReset(target.getEmail(), IdUtil.simpleUUID()).getStatus());
        assertEquals(1, jdbc.queryForObject("select used from account_security_ticket where user_id=?", Integer.class, target.getId()));
        assertNotNull(quotas.reserve(List.of(target.getId()), List.of(target.getEmail()), IdUtil.simpleUUID()));
    }

    private AppUser user(boolean verified) {
        AppUser user = new AppUser();
        user.setId(IdUtil.simpleUUID()); userIds.add(user.getId());
        user.setUsername("test-" + user.getId().substring(0, 12)); user.setNickname("Security test");
        user.setEmail(user.getId() + "@example.test"); user.setEmailVerified(verified);
        user.setPassword(PasswordUtil.hash("password-123")); user.setUserRole("user"); user.setStatus("normal");
        user.setAuthVersion(0L); user.setAccountOrigin("EMAIL");
        user.setCreateTime(LocalDateTime.now()); user.setUpdateTime(LocalDateTime.now());
        users.insert(user);
        return user;
    }

    private void addMember(AppUser user, String householdId, String role) {
        HouseholdMember member = new HouseholdMember(); member.setUserId(user.getId()); member.setHouseholdId(householdId); member.setMemberRole(role);
        members.save(member);
    }

    private void ageEmail(String address) {
        jdbc.update("update email_send_quota set last_sent_at=? where bucket_type='EMAIL' and bucket_hash=?", LocalDateTime.now().minusMinutes(2), SecurityTokenUtil.hash(address));
    }

    private List<SimpleMailMessage> sentMessages() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, atLeastOnce()).send(captor.capture());
        return captor.getAllValues();
    }

    private String resetToken(SimpleMailMessage message) {
        Matcher matcher = Pattern.compile("token=([A-Za-z0-9_-]{43})").matcher(Objects.requireNonNull(message.getText()));
        assertTrue(matcher.find()); return matcher.group(1);
    }

    private String code(SimpleMailMessage message) {
        Matcher matcher = Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])").matcher(Objects.requireNonNull(message.getText()));
        assertTrue(matcher.find()); return matcher.group();
    }
}
