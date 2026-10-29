package com.wgxhl.recipe.todo.service.impl;

import com.wgxhl.recipe.household.service.HouseholdMemberService;
import com.wgxhl.recipe.notification.service.NotificationService;
import com.wgxhl.recipe.push.service.UserPushSubscriptionService;
import com.wgxhl.recipe.todo.entity.Todo;
import com.wgxhl.recipe.todo.mapper.TodoMapper;
import com.wgxhl.recipe.todo.task.ReminderDeliveryGuard;
import com.wgxhl.recipe.todo.mapper.TodoNoticeRuleMapper;
import com.wgxhl.recipe.todo.mapper.TodoOwnerMapper;
import com.wgxhl.recipe.todo.mapper.TodoSendLogMapper;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import com.wgxhl.recipe.user.service.impl.AppUserServiceImpl;
import com.wgxhl.recipe.mail.MailDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class TodoBirthdayCompleteTest {

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
    private MailDeliveryService mailDeliveryService;
    @Mock
    private TodoMapper todoMapper;
    @Mock
    private ReminderDeliveryGuard deliveryGuard;

    private TodoServiceImpl todoService;

    @BeforeEach
    void setUp() {
        todoService = new TodoServiceImpl(
                todoNoticeRuleMapper,
                todoOwnerMapper,
                todoSendLogMapper,
                appUserMapper,
                notificationService,
                userPushSubscriptionService,
                householdMemberService,
                mailDeliveryService,
                deliveryGuard
        );
        ReflectionTestUtils.setField(todoService, "baseMapper", todoMapper);
    }

    @Test
    void resolveNextBirthdayDueDate_lunar_shouldUseLunarConversionNotSolarPlusOneYear() {
        Todo todo = new Todo();
        todo.setCategory("BIRTHDAY");
        todo.setDueCalendar("LUNAR");
        todo.setLunarDueMonth(12);
        todo.setLunarDueDay(8);
        todo.setLunarDueLeap(false);

        LocalDate currentDue = AppUserServiceImpl.lunarToSolar(2025, 12, 8, false);
        assertNotNull(currentDue);
        todo.setDueTime(LocalDateTime.of(currentDue, LocalTime.of(11, 0)));

        LocalDate next = ReflectionTestUtils.invokeMethod(todoService, "resolveNextBirthdayDueDate", todo);
        LocalDate wrongSolarNext = currentDue.plusYears(1);

        assertNotNull(next);
        assertEquals(
                AppUserServiceImpl.lunarToSolar(2026, 12, 8, false),
                next
        );
        assertNotEquals(wrongSolarNext, next);
    }

    @Test
    void resolveNextBirthdayDueDate_solar_shouldAdvanceByCalendarYear() {
        Todo todo = new Todo();
        todo.setCategory("BIRTHDAY");
        todo.setDueCalendar("SOLAR");
        todo.setDueTime(LocalDateTime.of(LocalDate.of(2026, 5, 20), LocalTime.of(11, 0)));

        LocalDate next = ReflectionTestUtils.invokeMethod(todoService, "resolveNextBirthdayDueDate", todo);

        assertEquals(LocalDate.of(2027, 5, 20), next);
    }
}
