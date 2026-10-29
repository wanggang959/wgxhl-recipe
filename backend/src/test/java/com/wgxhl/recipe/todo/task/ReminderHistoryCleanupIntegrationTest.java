package com.wgxhl.recipe.todo.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named="test.integration.enabled", matches="true")
@Transactional
class ReminderHistoryCleanupIntegrationTest {
    private static final String HOME = "00000000-0000-0000-0000-000000000001";
    @Autowired private JdbcTemplate db;
    @Autowired private ReminderHistoryCleanupTask cleanup;
    private final LocalDateTime now = LocalDateTime.now().withNano(0);

    @Test
    void keepsUnreadRecentlyReadAndRecentNotificationsAcrossFamilies() {
        String old = notice(true, 91, 91);
        String unread = notice(false, 200, 200);
        String recent = notice(true, 1, 1);
        String recentlyRead = notice(true, 200, 0);
        String boundary = notice(true, 90, 90);
        String otherFamily = notice(true, 91, 91);
        db.update("update notification set household_id='other-test-home' where id=?", otherFamily);
        cleanup.cleanup(now);
        assertCount("notification", old, 0);
        assertCount("notification", otherFamily, 0);
        for (String id : new String[]{unread, recent, recentlyRead, boundary}) assertCount("notification", id, 1);
    }

    @Test
    void removesOnlyOldDeletedTasksAndProvablyObsoleteCycles() {
        String todo = todo(now.minusDays(100));
        rule(todo, 7 * 1440);
        rule(todo, 1440);
        String obsolete = sent(todo, 400, 400, "SUCCESS");
        String obsoleteFail = sent(todo, 400, 400, "FAIL");
        String obsoleteLegacy = sent(todo, 400, null, "SUCCESS");
        String current = sent(todo, 107, 107, "SUCCESS");
        String currentFail = sent(todo, 106, 107, "FAIL");
        String currentSkip = sent(todo, 106, 107, "SKIP");
        String legacy = sent(todo, 105, null, "SUCCESS");
        String lateSuccess = sent(todo, 105, 400, "SUCCESS");
        String futureTarget = sent(todo, 400, 1, "FAIL");
        String deleted = sent(UUID.randomUUID().toString(), 400, 400, "FAIL");
        String recentDeleted = sent(UUID.randomUUID().toString(), 1, 1, "SUCCESS");
        String noDate = sent(todo(null), 400, 400, "SUCCESS");
        String noRules = sent(todo(now.plusDays(1)), 400, 400, "SUCCESS");
        String recentlyUpdated = sent(todo, 400, 400, "SUCCESS");
        db.update("update todo_send_log set update_time=? where id=?", Timestamp.valueOf(now), recentlyUpdated);
        String boundary = sent(UUID.randomUUID().toString(), 90, 90, "SUCCESS");
        cleanup.cleanup(now);
        for (String id : new String[]{obsolete, obsoleteFail, obsoleteLegacy, deleted}) assertCount("todo_send_log", id, 0);
        for (String id : new String[]{current, currentFail, currentSkip, legacy, lateSuccess, futureTarget, recentDeleted, noDate, noRules, recentlyUpdated, boundary})
            assertCount("todo_send_log", id, 1);
        cleanup.cleanup(now);
        assertCount("todo_send_log", current, 1);
        assertCount("todo", todo, 1);
        assertEquals(2, db.queryForObject("select count(*) from todo_notice_rule where todo_id=?", Integer.class, todo));
    }

    private String notice(boolean read, int createdDays, int updatedDays) {
        String id = UUID.randomUUID().toString();
        db.update("insert into notification (id, household_id, user_id, title, is_read, create_time, update_time) values (?,?,?,'retention-test',?,?,?)",
                id, HOME, "admin-wangshifu", read, Timestamp.valueOf(now.minusDays(createdDays)), Timestamp.valueOf(now.minusDays(updatedDays)));
        return id;
    }

    private String todo(LocalDateTime due) {
        String id = UUID.randomUUID().toString();
        db.update("insert into todo (id, household_id, title, category, status, due_time) values (?,?,'retention-test','OTHER','TODO',?)",
                id, HOME, due == null ? null : Timestamp.valueOf(due));
        return id;
    }

    private void rule(String todo, int minutes) {
        db.update("insert into todo_notice_rule (id, household_id, todo_id, before_type, before_minutes) values (?,?,?,'BEFORE',?)",
                UUID.randomUUID().toString(), HOME, todo, minutes);
    }

    private String sent(String todo, int days, Integer targetDays, String status) {
        String id = UUID.randomUUID().toString();
        Timestamp time = Timestamp.valueOf(now.minusDays(days));
        db.update("insert into todo_send_log (id,household_id,todo_id,rule_id,notify_type,send_status,send_time,target_time,create_time,update_time) values (?,?,?,?,'email',?,?,?,?,?)",
                id, HOME, todo, UUID.randomUUID().toString(), status, time,
                targetDays == null ? null : Timestamp.valueOf(now.minusDays(targetDays)), time, time);
        return id;
    }

    private void assertCount(String table, String id, int expected) {
        assertEquals(expected, db.queryForObject("select count(*) from " + table + " where id=?", Integer.class, id), id);
    }
}
