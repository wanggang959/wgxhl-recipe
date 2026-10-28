package com.wgxhl.recipe.todo.service.impl;

import com.wgxhl.recipe.notification.entity.Notification;
import com.wgxhl.recipe.household.service.HouseholdMemberService;
import com.wgxhl.recipe.notification.service.NotificationService;
import com.wgxhl.recipe.push.service.UserPushSubscriptionService;
import com.wgxhl.recipe.todo.entity.Todo;
import com.wgxhl.recipe.todo.entity.TodoNoticeRule;
import com.wgxhl.recipe.todo.entity.TodoOwner;
import com.wgxhl.recipe.todo.entity.TodoSendLog;
import com.wgxhl.recipe.todo.mapper.TodoMapper;
import com.wgxhl.recipe.todo.mapper.TodoNoticeRuleMapper;
import com.wgxhl.recipe.todo.mapper.TodoOwnerMapper;
import com.wgxhl.recipe.todo.mapper.TodoSendLogMapper;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoServiceImplNotifyTest {

    @Mock
    private TodoNoticeRuleMapper todoNoticeRuleMapper;
    @Mock
    private TodoOwnerMapper todoOwnerMapper;
    @Mock
    private TodoSendLogMapper todoSendLogMapper;
    @Mock
    private AppUserMapper appUserMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserPushSubscriptionService userPushSubscriptionService;
    @Mock
    private HouseholdMemberService householdMemberService;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private TodoMapper todoMapper;

    private TodoServiceImpl todoService;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> mailSenderProvider = mock(ObjectProvider.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

        todoService = new TodoServiceImpl(
                todoNoticeRuleMapper,
                todoOwnerMapper,
                todoSendLogMapper,
                appUserMapper,
                notificationService,
                userPushSubscriptionService,
                householdMemberService,
                mailSenderProvider
        );
        ReflectionTestUtils.setField(todoService, "baseMapper", todoMapper);
        ReflectionTestUtils.setField(todoService, "mailUsername", "noreply@wgxhl.space");
    }

    @Test
    void sendByRule_shouldCreateSiteNoticeWhenEnabled() {
        Todo todo = buildTodo(true, false, false);
        TodoNoticeRule rule = buildRule(0);
        AppUser owner = buildOwner("user-1", "test@example.com", "site,email");
        when(notificationService.createSiteNotice(any(), any(), any(), any(), any()))
                .thenReturn(new Notification());

        int sent = invokeSendByRule(todo, rule, owner);

        assertEquals(1, sent);
        verify(notificationService).createSiteNotice(
                eq("user-1"),
                eq("买豆腐"),
                any(String.class),
                eq("TODO"),
                eq("todo-1")
        );
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(todoSendLogMapper).insert(any(TodoSendLog.class));
    }

    @Test
    void sendByRule_shouldSendEmailWhenEnabled() {
        Todo todo = buildTodo(false, true, false);
        TodoNoticeRule rule = buildRule(0);
        AppUser owner = buildOwner("user-1", "wang@test.com", "site,email");

        int sent = invokeSendByRule(todo, rule, owner);

        assertEquals(1, sent);
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertEquals("noreply@wgxhl.space", message.getFrom());
        assertEquals("wang@test.com", message.getTo()[0]);
        assertTrue(message.getSubject().contains("买豆腐"));
        verify(notificationService, never()).createSiteNotice(any(), any(), any(), any(), any());
    }

    @Test
    void sendByRule_shouldSkipEmailWhenPreferenceDisabled() {
        Todo todo = buildTodo(false, true, false);
        TodoNoticeRule rule = buildRule(0);
        AppUser owner = buildOwner("user-1", "wang@test.com", "site");

        int sent = invokeSendByRule(todo, rule, owner);

        assertEquals(0, sent);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(notificationService, never()).createSiteNotice(any(), any(), any(), any(), any());
    }

    @Test
    void sendByRule_shouldSendBothSiteAndEmailWhenBothEnabled() {
        Todo todo = buildTodo(true, true, false);
        TodoNoticeRule rule = buildRule(30);
        AppUser owner = buildOwner("user-1", "wang@test.com", "site,email");
        when(notificationService.createSiteNotice(any(), any(), any(), any(), any()))
                .thenReturn(new Notification());

        int sent = invokeSendByRule(todo, rule, owner);

        assertEquals(2, sent);
        verify(notificationService).createSiteNotice(
                eq("user-1"),
                eq("买豆腐"),
                any(String.class),
                eq("TODO"),
                eq("todo-1")
        );
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void retryFailedChannelWithoutRepeatingSuccessfulChannel() {
        Todo todo = buildTodo(true, true, false);
        TodoNoticeRule rule = buildRule(0);
        AppUser owner = buildOwner("user-1", "wang@test.com", "site,email");
        TodoSendLog siteSuccess = new TodoSendLog();
        siteSuccess.setSendStatus("SUCCESS");
        TodoSendLog emailFailure = new TodoSendLog();
        emailFailure.setSendStatus("FAIL");
        when(todoSendLogMapper.selectList(any())).thenReturn(
                Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(siteSuccess), Collections.singletonList(emailFailure));
        when(notificationService.createSiteNotice(any(), any(), any(), any(), any())).thenReturn(new Notification());
        doThrow(new org.springframework.mail.MailSendException("temporary"))
                .doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        assertEquals(1, invokeSendByRule(todo, rule, owner));
        assertEquals(1, invokeSendByRule(todo, rule, owner));

        verify(notificationService, times(1)).createSiteNotice(any(), any(), any(), any(), any());
        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
        ArgumentCaptor<TodoSendLog> logs = ArgumentCaptor.forClass(TodoSendLog.class);
        verify(todoSendLogMapper, times(3)).insert(logs.capture());
        assertTrue(logs.getAllValues().stream().allMatch(entry -> "user-1".equals(entry.getRecipientId())
                && todo.getDueTime().equals(entry.getTargetTime())));
    }

    @Test
    void stopRetryingAfterThreeFailures() {
        TodoSendLog failed = new TodoSendLog();
        failed.setSendStatus("FAIL");
        when(todoSendLogMapper.selectList(any())).thenReturn(Arrays.asList(failed, failed, failed));
        boolean result = ReflectionTestUtils.invokeMethod(todoService, "shouldAttempt", buildTodo(false, true, false),
                buildRule(0), LocalDateTime.now(), "email", buildOwner("user-1", "wang@test.com", "email"));
        assertEquals(false, result);
    }

    @Test
    void successfulRecipientDoesNotSuppressAnotherRecipient() {
        Todo todo = buildTodo(true, false, false);
        TodoNoticeRule rule = buildRule(0);
        TodoOwner first = new TodoOwner();
        first.setOwnerId("user-1");
        TodoOwner second = new TodoOwner();
        second.setOwnerId("user-2");
        when(todoOwnerMapper.selectList(any())).thenReturn(Arrays.asList(first, second));
        when(appUserMapper.selectById("user-1")).thenReturn(buildOwner("user-1", "one@test.com", "site"));
        when(appUserMapper.selectById("user-2")).thenReturn(buildOwner("user-2", "two@test.com", "site"));
        TodoSendLog success = new TodoSendLog();
        success.setSendStatus("SUCCESS");
        when(todoSendLogMapper.selectList(any())).thenReturn(Collections.singletonList(success), Collections.emptyList());
        when(notificationService.createSiteNotice(any(), any(), any(), any(), any())).thenReturn(new Notification());

        Integer sent = ReflectionTestUtils.invokeMethod(todoService, "sendByRule", todo, rule, todo.getDueTime());
        assertEquals(1, sent.intValue());

        verify(notificationService, never()).createSiteNotice(eq("user-1"), any(), any(), any(), any());
        verify(notificationService).createSiteNotice(eq("user-2"), any(), any(), any(), any());
    }

    @Test
    void scanWindowDoesNotSendEarlyOrOldRules() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 28, 10, 0);
        assertEquals(true, ReflectionTestUtils.invokeMethod(todoService, "isInNotifyWindow", now.minusMinutes(6), now));
        assertEquals(false, ReflectionTestUtils.invokeMethod(todoService, "isInNotifyWindow", now.minusMinutes(6).minusSeconds(1), now));
        assertEquals(false, ReflectionTestUtils.invokeMethod(todoService, "isInNotifyWindow", now.plusSeconds(1), now));
    }

    @Test
    void unchangedBirthdayRulesKeepTheirIds() {
        TodoNoticeRule sevenDays = buildRule(10080);
        sevenDays.setId("seven-days");
        TodoNoticeRule threeDays = buildRule(4320);
        threeDays.setId("three-days");
        TodoNoticeRule onTime = buildRule(0);
        onTime.setId("on-time");
        when(todoNoticeRuleMapper.selectList(any())).thenReturn(Arrays.asList(sevenDays, threeDays, onTime));

        ReflectionTestUtils.invokeMethod(todoService, "replaceRules", "todo-1", Arrays.asList(10080, 4320, 0));

        verify(todoNoticeRuleMapper, never()).deleteById(any(String.class));
        verify(todoNoticeRuleMapper, never()).insert(any(TodoNoticeRule.class));
    }

    @Test
    void changedRulesRetainMatchingIds() {
        TodoNoticeRule existing = buildRule(10);
        existing.setId("existing");
        TodoNoticeRule removed = buildRule(60);
        removed.setId("removed");
        when(todoNoticeRuleMapper.selectList(any())).thenReturn(Arrays.asList(existing, removed));

        ReflectionTestUtils.invokeMethod(todoService, "replaceRules", "todo-1", Arrays.asList(10, 0));

        verify(todoNoticeRuleMapper).deleteById("removed");
        verify(todoNoticeRuleMapper, never()).deleteById("existing");
        ArgumentCaptor<TodoNoticeRule> inserted = ArgumentCaptor.forClass(TodoNoticeRule.class);
        verify(todoNoticeRuleMapper).insert(inserted.capture());
        assertEquals(0, inserted.getValue().getBeforeMinutes());
    }

    @Test
    void resolveNextNotifyLabel_shouldPickEarliestUnsentNotice() {
        Todo todo = new Todo();
        todo.setId("todo-1");
        todo.setNotifyEmail(true);
        todo.setDueTime(LocalDateTime.of(2026, 12, 29, 11, 0));

        TodoNoticeRule rule7d = new TodoNoticeRule();
        rule7d.setId("rule-7d");
        rule7d.setTodoId("todo-1");
        rule7d.setBeforeMinutes(10080);

        TodoNoticeRule rule3d = new TodoNoticeRule();
        rule3d.setId("rule-3d");
        rule3d.setTodoId("todo-1");
        rule3d.setBeforeMinutes(4320);

        when(todoNoticeRuleMapper.selectList(any())).thenReturn(Arrays.asList(rule7d, rule3d));
        when(todoSendLogMapper.selectCount(any())).thenReturn(0L);

        String label = ReflectionTestUtils.invokeMethod(todoService, "resolveNextNotifyLabel", todo);

        assertEquals("2026-12-22 11:00", label);
    }

    private int invokeSendByRule(Todo todo, TodoNoticeRule rule, AppUser owner) {
        when(todoOwnerMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(appUserMapper.selectById(owner.getId())).thenReturn(owner);
        todo.setOwnerId(owner.getId());
        LocalDateTime targetTime = todo.getDueTime().minusMinutes(rule.getBeforeMinutes());
        return ReflectionTestUtils.invokeMethod(todoService, "sendByRule", todo, rule, targetTime);
    }

    private Todo buildTodo(boolean site, boolean email, boolean push) {
        Todo todo = new Todo();
        todo.setId("todo-1");
        todo.setTitle("买豆腐");
        todo.setDescription("去超市");
        todo.setStatus("TODO");
        todo.setDueTime(LocalDateTime.now().plusMinutes(10));
        todo.setNotifySite(site);
        todo.setNotifyEmail(email);
        todo.setNotifyPush(push);
        return todo;
    }

    private TodoNoticeRule buildRule(int beforeMinutes) {
        TodoNoticeRule rule = new TodoNoticeRule();
        rule.setId("rule-1");
        rule.setTodoId("todo-1");
        rule.setBeforeMinutes(beforeMinutes);
        return rule;
    }

    private AppUser buildOwner(String id, String email, String preference) {
        AppUser owner = new AppUser();
        owner.setId(id);
        owner.setEmail(email);
        owner.setNotificationPreference(preference);
        owner.setStatus("normal");
        return owner;
    }
}
