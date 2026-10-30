package com.wgxhl.recipe.todo.task;

import com.wgxhl.recipe.config.TenantContext;
import com.wgxhl.recipe.mail.MailDeliveryService;
import com.wgxhl.recipe.push.service.UserPushSubscriptionService;
import com.wgxhl.recipe.todo.dto.ReminderRecoveryDTO;
import com.wgxhl.recipe.todo.entity.Todo;
import com.wgxhl.recipe.todo.entity.TodoNoticeRule;
import com.wgxhl.recipe.user.entity.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named="test.integration.enabled", matches="true")
@Transactional
class ReminderRecoveryIntegrationTest {
    @Autowired private JdbcTemplate db;
    @Autowired private ReminderRecoveryService recovery;
    @Autowired private ReminderDeliveryGuard guard;
    @MockBean private MailDeliveryService mail;
    @MockBean private UserPushSubscriptionService push;
    private String home, owner, todo, rule;
    private LocalDateTime target;

    @BeforeEach void setup() {
        home=id(); owner=id(); todo=id(); rule=id();
        target=LocalDateTime.now().withNano(0).minusMinutes(20);
        db.update("insert into household (id,household_name,owner_user_id,status) values (?,'recovery-test',?,'normal')",home,owner);
        db.update("insert into app_user (id,username,status,user_role,email,notification_preference) values (?,?,'normal','user','recovery@example.test','site,email,push')",owner,owner);
        db.update("insert into household_member (id,household_id,user_id,member_role,status) values (?,?,?,'owner','normal')",id(),home,owner);
        db.update("insert into todo (id,household_id,title,category,status,owner_id,due_time,notify_site,notify_email,notify_push) values (?,?,'recovery-test','OTHER','TODO',?,?,1,1,1)",todo,home,owner,Timestamp.valueOf(target));
        db.update("insert into todo_notice_rule (id,household_id,todo_id,before_type,before_minutes) values (?,?,?,'BEFORE',0)",rule,home,todo);
        db.update("insert into todo_delivery_guard (household_id,todo_id,rule_id,target_time,notify_type,recipient_key,status,attempts,update_time) values (?,?,?,?,'email',?,'UNKNOWN',1,?)",
                home,todo,rule,Timestamp.valueOf(target),owner,Timestamp.valueOf(target));
    }

    @Test void confirmationRequiredAndStalePagesCannotResend() {
        ReminderRecoveryDTO r=request("RETRY");
        r.setConfirmed(false);
        assertNotEquals(200,recovery.recover(r,"admin").getStatus());
        r.setConfirmed(true); r.setExpectedUpdateTime(target.minusSeconds(1));
        assertNotEquals(200,recovery.recover(r,"admin").getStatus());
        verifyNoInteractions(mail,push);
        assertEquals("UNKNOWN",status());
        assertNull(TenantContext.get());
        db.update("update todo_delivery_guard set status='IN_FLIGHT',update_time=? where todo_id=?",Timestamp.valueOf(LocalDateTime.now()),todo);
        assertNotEquals(200,recovery.recover(request("CLOSE"),"admin").getStatus());
        assertEquals("IN_FLIGHT",status());
    }

    @Test void explicitRetryOnlySendsSelectedChannelOnceAndAuditsActor() {
        ReminderRecoveryDTO r=request("RETRY");
        assertEquals(200,recovery.recover(r,"admin-audit").getStatus());
        assertEquals("SUCCESS",status());
        assertNotEquals(200,recovery.recover(r,"admin-audit").getStatus());
        verify(mail,times(1)).sendNotification(eq("recovery@example.test"),any(),anyString(),anyString());
        verifyNoInteractions(push);
        assertEquals(0,db.queryForObject("select count(*) from notification where related_id=?",Integer.class,todo));
        assertEquals(1,db.queryForObject("select count(*) from todo_send_log where todo_id=? and send_status='SUCCESS' and error_msg like '%admin-audit%'",Integer.class,todo));
    }

    @Test void unknownFailureReturnsToReviewWithoutAutomaticRetry() {
        doThrow(new IllegalStateException("timeout")).when(mail).sendNotification(anyString(),any(),anyString(),anyString());
        assertNotEquals(200,recovery.recover(request("RETRY"),"admin").getStatus());
        assertEquals("UNKNOWN",status());
        assertEquals(1,db.queryForObject("select count(*) from todo_send_log where todo_id=? and send_status='FAIL'",Integer.class,todo));
        assertFalse(TenantContext.callWith(home,()->guard.hasRetryable(todo(),rule(),target)));
    }

    @Test void interruptedDeliveryCanBeClosedEvenAfterTaskDeletion() {
        db.update("update todo_delivery_guard set status='IN_FLIGHT' where todo_id=?",todo);
        db.update("delete from todo where id=?",todo);
        assertEquals(200,recovery.recover(request("CLOSE"),"admin-close").getStatus());
        assertEquals("SKIP",status());
        assertEquals(1,db.queryForObject("select count(*) from todo_send_log where todo_id=? and send_status='SKIP' and error_msg like '%admin-close%'",Integer.class,todo));
        verifyNoInteractions(mail,push);
    }

    @Test void changedCycleCannotBeRetried() {
        db.update("update todo set due_time=? where id=?",Timestamp.valueOf(target.plusDays(1)),todo);
        assertNotEquals(200,recovery.recover(request("RETRY"),"admin").getStatus());
        verifyNoInteractions(mail,push);
        assertEquals("UNKNOWN",status());
    }

    @Test void disabledRecipientCannotBeRetried() {
        db.update("update app_user set status='disabled' where id=?",owner);
        assertNotEquals(200,recovery.recover(request("RETRY"),"admin").getStatus());
        verifyNoInteractions(mail,push);
        assertEquals("UNKNOWN",status());
    }

    @Test void activeScannerLockPreventsRecoveryFromRacing() throws Exception {
        try (Connection other=db.getDataSource().getConnection(); Statement statement=other.createStatement()) {
            try (ResultSet result=statement.executeQuery("select get_lock('wgxhl_recipe_todo_scan',0)")) { assertTrue(result.next()); assertEquals(1,result.getInt(1)); }
            try {
                assertNotEquals(200,recovery.recover(request("RETRY"),"admin").getStatus());
                assertEquals("UNKNOWN",status());
                verifyNoInteractions(mail,push);
            } finally { statement.execute("select release_lock('wgxhl_recipe_todo_scan')"); }
        }
    }

    @Test void explicitTemporaryRejectionWaitsUntilRetryTime() {
        db.update("delete from todo_delivery_guard where todo_id=?",todo);
        TenantContext.runWith(home,()-> {
            AppUser user=new AppUser(); user.setId(owner);
            assertTrue(guard.claim(todo(),rule(),target,"email",user));
            guard.retryLater(todo(),rule(),target,"email",user,10);
            db.update("update todo_delivery_guard set update_time=? where todo_id=?",Timestamp.valueOf(target),todo);
            assertFalse(guard.claim(todo(),rule(),target,"email",user));
            db.update("update todo_delivery_guard set next_retry_time=? where todo_id=?",Timestamp.valueOf(target),todo);
            assertTrue(guard.claim(todo(),rule(),target,"email",user));
        });
    }

    private ReminderRecoveryDTO request(String action) {
        ReminderRecoveryDTO r=new ReminderRecoveryDTO();
        r.setHouseholdId(home); r.setTodoId(todo); r.setRuleId(rule); r.setRecipientId(owner); r.setChannel("email");
        r.setTargetTime(target); r.setExpectedUpdateTime(db.queryForObject("select update_time from todo_delivery_guard where todo_id=?",Timestamp.class,todo).toLocalDateTime());
        r.setAction(action); r.setConfirmed(true); return r;
    }
    private Todo todo() { Todo t=new Todo(); t.setId(todo); return t; }
    private TodoNoticeRule rule() { TodoNoticeRule r=new TodoNoticeRule(); r.setId(rule); return r; }
    private String status() { return db.queryForObject("select status from todo_delivery_guard where todo_id=?",String.class,todo); }
    private String id() { return UUID.randomUUID().toString(); }
}
