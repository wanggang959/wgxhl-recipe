package com.wgxhl.recipe.todo.service.impl;

import com.wgxhl.recipe.notification.entity.Notification;
import com.wgxhl.recipe.notification.service.NotificationService;
import com.wgxhl.recipe.push.service.UserPushSubscriptionService;
import com.wgxhl.recipe.todo.entity.Todo;
import com.wgxhl.recipe.todo.entity.TodoNoticeRule;
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
