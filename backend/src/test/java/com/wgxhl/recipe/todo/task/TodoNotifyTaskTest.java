package com.wgxhl.recipe.todo.task;

import com.wgxhl.recipe.household.entity.Household;
import com.wgxhl.recipe.household.service.HouseholdService;
import com.wgxhl.recipe.todo.service.TodoService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TodoNotifyTaskTest {
    private ReminderScanCoordinator coordinator() {
        ReminderScanCoordinator coordinator = mock(ReminderScanCoordinator.class);
        when(coordinator.runWithLock(any())).thenAnswer(invocation -> { invocation.<Runnable>getArgument(0).run(); return true; });
        when(coordinator.windowStart(anyString(), any())).thenAnswer(invocation -> invocation.<LocalDateTime>getArgument(1).minusMinutes(6));
        return coordinator;
    }
    @Test
    void scheduleRunsEveryMinute() throws Exception {
        assertEquals("0 * * * * ?", TodoNotifyTask.class.getMethod("scan").getAnnotation(Scheduled.class).cron());
    }

    @Test
    void overlappingScanIsSkipped() throws Exception {
        TodoService service = mock(TodoService.class);
        HouseholdService households = mock(HouseholdService.class);
        Household home = new Household();
        home.setId("home-1");
        when(households.listActive()).thenReturn(Arrays.asList(home));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(service.scanAndNotify(any(), any())).thenAnswer(invocation -> {
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timed out");
            return 0;
        });
        TodoNotifyTask task = new TodoNotifyTask(service, households, coordinator());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = executor.submit(task::scan);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            task.scan();
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            verify(service, times(1)).scanAndNotify(any(), any());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void oneHouseholdFailureDoesNotBlockOthers() {
        TodoService service = mock(TodoService.class);
        HouseholdService households = mock(HouseholdService.class);
        Household first = new Household();
        first.setId("home-1");
        Household second = new Household();
        second.setId("home-2");
        when(households.listActive()).thenReturn(Arrays.asList(first, second));
        when(service.scanAndNotify(any(), any())).thenThrow(new IllegalStateException("test")).thenReturn(1);
        ReminderScanCoordinator coordinator = coordinator();
        new TodoNotifyTask(service, households, coordinator).scan();
        verify(service, times(2)).scanAndNotify(any(), any());
        verify(coordinator, never()).markSuccess(eq("home-1"), any());
        verify(coordinator).markSuccess(eq("home-2"), any());
    }
}
