package com.wgxhl.recipe.todo.task;

import com.wgxhl.recipe.config.TenantContext;
import com.wgxhl.recipe.todo.entity.Todo;
import com.wgxhl.recipe.todo.entity.TodoNoticeRule;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.todo.service.TodoService;
import com.wgxhl.recipe.mail.MailDeliveryService;
import com.wgxhl.recipe.mail.RetryableMailException;
import com.wgxhl.recipe.admin.PlatformAdminController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named = "test.integration.enabled", matches = "true")
class ReminderReliabilityIntegrationTest {
    @Autowired private JdbcTemplate db;
    @SpyBean private ReminderDeliveryGuard guard;
    @Autowired private ReminderScanCoordinator coordinator;
    @Autowired private TodoService todos;
    @Autowired private TodoNotifyTask task;
    @Autowired private PlatformAdminController admin;
    @MockBean private MailDeliveryService mail;
    @AfterEach void clearContext() { TenantContext.clear(); }

    @Test
    @Transactional
    void missedRulesAreCoalescedAndRepeatedScansDoNotDuplicate() {
        String household = UUID.randomUUID().toString();
        String todo = UUID.randomUUID().toString();
        String oldRule = UUID.randomUUID().toString(), latestRule = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        db.update("insert into todo (id, household_id, title, category, owner_id, due_time, status, notify_site, notify_email, notify_push, create_time) values (?,?,'test','OTHER','admin-wangshifu',?,'TODO',1,0,0,?)",
                todo, household, Timestamp.valueOf(now.plusHours(1)), Timestamp.valueOf(now.minusDays(3)));
        for (String id : new String[]{oldRule, latestRule}) {
            int minutes = id.equals(oldRule) ? 180 : 120;
            db.update("insert into todo_notice_rule (id, household_id, todo_id, before_type, before_minutes, create_time) values (?,?,?,'BEFORE',?,?)",
                    id, household, todo, minutes, Timestamp.valueOf(now.minusDays(3)));
        }
        TenantContext.set(household);
        assertEquals(1, todos.scanAndNotify(now.minusDays(1), now));
        assertEquals(0, todos.scanAndNotify(now.minusDays(1), now));
        db.update("update todo set notify_email=1 where id=?", todo);
        db.update("update app_user set email='later-test@example.test', notification_preference='site,email' where id='admin-wangshifu'");
        assertEquals(0, todos.scanAndNotify(now.minusDays(1), now));
        verify(mail, never()).sendNotification(anyString(), any(), anyString(), anyString());
        assertEquals(1, db.queryForObject("select count(*) from notification where household_id=? and related_id=?", Integer.class, household, todo));
        assertEquals(latestRule, db.queryForObject("select rule_id from todo_send_log where household_id=? and todo_id=? and send_status='SUCCESS'", String.class, household, todo));
        assertEquals(1, db.queryForObject("select count(*) from todo_send_log where household_id=? and todo_id=? and notify_type='coalesced' and send_status='SKIP' and rule_id=?", Integer.class, household, todo, oldRule));
    }

    @Test
    @Transactional
    void alreadyAcceptedOlderRuleIsNotLabeledAsCoalesced() {
        String household=UUID.randomUUID().toString(), todo=UUID.randomUUID().toString();
        String older=UUID.randomUUID().toString(), recent=UUID.randomUUID().toString();
        LocalDateTime now=LocalDateTime.now().withNano(0);
        db.update("insert into todo (id,household_id,title,category,owner_id,due_time,status,notify_site,notify_email,notify_push,create_time) values (?,?,'test','OTHER','admin-wangshifu',?,'TODO',1,0,0,?)",
                todo,household,Timestamp.valueOf(now.plusHours(1)),Timestamp.valueOf(now.minusDays(3)));
        for (String rule:new String[]{older,recent}) db.update("insert into todo_notice_rule (id,household_id,todo_id,before_type,before_minutes,create_time) values (?,?,?,'BEFORE',?,?)",
                rule,household,todo,rule.equals(older)?180:120,Timestamp.valueOf(now.minusDays(3)));
        db.update("insert into todo_send_log (id,household_id,todo_id,rule_id,notify_type,send_status,send_time) values (?,?,?,?,'site','SUCCESS',?)",
                UUID.randomUUID().toString(),household,todo,older,Timestamp.valueOf(now.minusHours(2)));
        TenantContext.set(household);
        assertEquals(1,todos.scanAndNotify(now.minusDays(1),now));
        assertEquals(0,db.queryForObject("select count(*) from todo_send_log where household_id=? and notify_type='coalesced'",Integer.class,household));
    }

    @Test
    @Transactional
    void explicitlyRejectedMailIsRetriedAndThenRecordedAsSuccessful() {
        String household = UUID.randomUUID().toString(), todo = UUID.randomUUID().toString(), rule = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        db.update("update app_user set email='retry-test@example.test', notification_preference='email' where id='admin-wangshifu'");
        db.update("insert into todo (id, household_id, title, category, owner_id, due_time, status, notify_site, notify_email, notify_push, create_time) values (?,?,'test','OTHER','admin-wangshifu',?,'TODO',0,1,0,?)",
                todo, household, Timestamp.valueOf(now.plusHours(1)), Timestamp.valueOf(now.minusDays(3)));
        db.update("insert into todo_notice_rule (id, household_id, todo_id, before_type, before_minutes, create_time) values (?,?,?,'BEFORE',120,?)",
                rule, household, todo, Timestamp.valueOf(now.minusDays(3)));
        doThrow(new RetryableMailException("test")).doNothing().when(mail)
                .sendNotification(anyString(), any(), anyString(), anyString());
        TenantContext.set(household);
        assertThrows(PendingReminderRetryException.class, () -> todos.scanAndNotify(now.minusDays(1), now));
        db.update("update todo_delivery_guard set update_time=?,next_retry_time=? where household_id=?", Timestamp.valueOf(now.minusMinutes(2)), Timestamp.valueOf(now.minusMinutes(1)), household);
        assertEquals(1, todos.scanAndNotify(now.minusDays(1), now));
        assertEquals(0, todos.scanAndNotify(now.minusDays(1), now));
        verify(mail, times(2)).sendNotification(anyString(), any(), anyString(), anyString());
        assertEquals("SUCCESS", db.queryForObject("select status from todo_delivery_guard where household_id=? and notify_type='email'", String.class, household));
    }

    @Test
    @Transactional
    void scheduledScanCatchesUpFromCheckpointOnce() {
        String household = UUID.randomUUID().toString(), todo = UUID.randomUUID().toString(), rule = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        db.update("insert into household (id,household_name,owner_user_id,status,create_time) values (?,?, 'admin-wangshifu','normal',?)", household, household, Timestamp.valueOf(now));
        db.update("insert into household_member (id,household_id,user_id,member_role,status) values (?,?,'admin-wangshifu','owner','normal')", UUID.randomUUID().toString(), household);
        db.update("insert into todo (id,household_id,title,category,owner_id,due_time,status,notify_site,notify_email,notify_push,create_time) values (?,?,'test','OTHER','admin-wangshifu',?,'TODO',1,0,0,?)",
                todo, household, Timestamp.valueOf(now.plusHours(1)), Timestamp.valueOf(now.minusDays(3)));
        db.update("insert into todo_notice_rule (id,household_id,todo_id,before_type,before_minutes,create_time) values (?,?,?,'BEFORE',120,?)",
                rule, household, todo, Timestamp.valueOf(now.minusDays(3)));
        db.update("insert into todo_scan_checkpoint (household_id,last_success_time,update_time) values (?,?,?)",
                household, Timestamp.valueOf(now.minusHours(2)), Timestamp.valueOf(now.minusHours(2)));
        task.scan();
        task.scan();
        assertEquals(1, db.queryForObject("select count(*) from notification where household_id=? and related_id=?", Integer.class, household, todo));
        assertTrue(db.queryForObject("select last_success_time from todo_scan_checkpoint where household_id=?", Timestamp.class, household).toLocalDateTime().isAfter(now.minusMinutes(1)));
    }

    @Test
    @Transactional
    void oneTodoFailureDoesNotBlockOtherTodosInHousehold() {
        String household = UUID.randomUUID().toString();
        String broken = UUID.randomUUID().toString(), healthy = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        for (String id : new String[]{broken, healthy}) {
            db.update("insert into todo (id,household_id,title,category,owner_id,due_time,status,notify_site,notify_email,notify_push,create_time) values (?,?,'test','OTHER','admin-wangshifu',?,'TODO',1,0,0,?)",
                    id, household, Timestamp.valueOf(now.plusHours(1)), Timestamp.valueOf(now.minusDays(3)));
            db.update("insert into todo_notice_rule (id,household_id,todo_id,before_type,before_minutes,create_time) values (?,?,?,'BEFORE',120,?)",
                    UUID.randomUUID().toString(), household, id, Timestamp.valueOf(now.minusDays(3)));
        }
        doThrow(new IllegalStateException("simulated failure")).when(guard)
                .claim(argThat(item -> item != null && broken.equals(item.getId())), any(), any(), anyString(), any());
        TenantContext.set(household);
        assertThrows(IllegalStateException.class, () -> todos.scanAndNotify(now.minusDays(1), now));
        assertEquals(1, db.queryForObject("select count(*) from notification where household_id=? and related_id=?", Integer.class, household, healthy));
        assertEquals(0, db.queryForObject("select count(*) from notification where household_id=? and related_id=?", Integer.class, household, broken));
    }

    @Test
    @Transactional
    @SuppressWarnings("unchecked")
    void adminCanInspectUncertainClaimsAndMissedWindows() {
        String household = UUID.randomUUID().toString(), todo = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        db.update("insert into todo_delivery_guard (household_id,todo_id,rule_id,target_time,notify_type,recipient_key,status,attempts,update_time) values (?,?,?,?,'email','admin-wangshifu','UNKNOWN',1,?)",
                household, todo, UUID.randomUUID().toString(), Timestamp.valueOf(now), Timestamp.valueOf(now));
        db.update("insert into todo_scan_checkpoint (household_id,last_success_time,last_gap_start,last_gap_end,update_time) values (?,?,?,?,?)",
                household, Timestamp.valueOf(now), Timestamp.valueOf(now.minusDays(2)), Timestamp.valueOf(now.minusDays(1)), Timestamp.valueOf(now));
        Map<String, Object> result = admin.uncertain(1).getData();
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("items");
        List<Map<String, Object>> gaps = (List<Map<String, Object>>) result.get("gaps");
        assertTrue(rows.stream().anyMatch(row -> todo.equals(row.get("todo_id"))));
        assertTrue(gaps.stream().anyMatch(row -> household.equals(row.get("household_id"))));
    }

    @Test
    void concurrentScansClaimOnlyOnceAndUncertainIsNotRetried() throws Exception {
        String household = UUID.randomUUID().toString();
        Todo todo = new Todo(); todo.setId(UUID.randomUUID().toString());
        TodoNoticeRule rule = new TodoNoticeRule(); rule.setId(UUID.randomUUID().toString());
        AppUser owner = new AppUser(); owner.setId(UUID.randomUUID().toString());
        LocalDateTime target = LocalDateTime.now().withNano(0);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> action = () -> {
                TenantContext.set(household);
                try { start.await(); return guard.claim(todo, rule, target, "email", owner); }
                finally { TenantContext.clear(); }
            };
            Future<Boolean> first = pool.submit(action), second = pool.submit(action);
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            TenantContext.set(household);
            guard.finish(todo, rule, target, "email", owner, "UNKNOWN");
            assertFalse(guard.claim(todo, rule, target, "email", owner));
        } finally {
            TenantContext.clear(); pool.shutdownNow();
            db.update("delete from todo_delivery_guard where household_id=?", household);
        }
    }

    @Test
    void explicitFailureCanRetryOnlyAfterDelayAndAtMostThreeTimes() {
        String household = UUID.randomUUID().toString();
        Todo todo = new Todo(); todo.setId(UUID.randomUUID().toString());
        TodoNoticeRule rule = new TodoNoticeRule(); rule.setId(UUID.randomUUID().toString());
        AppUser owner = new AppUser(); owner.setId(UUID.randomUUID().toString());
        LocalDateTime target = LocalDateTime.now().withNano(0);
        TenantContext.set(household);
        try {
            assertTrue(guard.claim(todo, rule, target, "email", owner));
            for (int attempt = 1; attempt <= 3; attempt++) {
                guard.finish(todo, rule, target, "email", owner, "FAILED");
                assertEquals(attempt < 3, guard.hasRetryable(todo, rule, target));
                assertFalse(guard.claim(todo, rule, target, "email", owner));
                db.update("update todo_delivery_guard set update_time=? where household_id=?", Timestamp.valueOf(LocalDateTime.now().minusMinutes(2)), household);
                assertEquals(attempt < 3, guard.claim(todo, rule, target, "email", owner));
            }
        } finally {
            TenantContext.clear(); db.update("delete from todo_delivery_guard where household_id=?", household);
        }
    }

    @Test
    void checkpointLimitsCatchupAndDoesNotAdvanceOnFailure() {
        String household = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        try {
            db.update("insert into todo_scan_checkpoint (household_id,last_success_time,update_time) values (?,?,?)",
                    household, Timestamp.valueOf(now.minusDays(2)), Timestamp.valueOf(now));
            assertEquals(now.minusHours(24), coordinator.windowStart(household, now));
            assertEquals(Timestamp.valueOf(now.minusDays(2)), db.queryForObject("select last_gap_start from todo_scan_checkpoint where household_id=?", Timestamp.class, household));
            assertEquals(Timestamp.valueOf(now.minusHours(24)), db.queryForObject("select last_gap_end from todo_scan_checkpoint where household_id=?", Timestamp.class, household));
            coordinator.retainWindow(household, now.minusHours(24));
            assertEquals(now.minusHours(24), coordinator.windowStart(household, now));
            coordinator.markSuccess(household, now);
            assertEquals(now.minusMinutes(6), coordinator.windowStart(household, now));
        } finally { db.update("delete from todo_scan_checkpoint where household_id=?", household); }
    }

    @Test
    void databaseLockPreventsConcurrentScans() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            Future<Boolean> first = pool.submit(() -> coordinator.runWithLock(() -> {
                entered.countDown();
                try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertFalse(coordinator.runWithLock(() -> fail("second scan must not run")));
            release.countDown();
            assertTrue(first.get(10, TimeUnit.SECONDS));
        } finally { release.countDown(); pool.shutdownNow(); }
    }
}
