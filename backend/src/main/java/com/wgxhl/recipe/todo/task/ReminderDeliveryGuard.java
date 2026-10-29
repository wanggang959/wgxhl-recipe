package com.wgxhl.recipe.todo.task;

import com.wgxhl.recipe.config.TenantContext;
import com.wgxhl.recipe.todo.entity.Todo;
import com.wgxhl.recipe.todo.entity.TodoNoticeRule;
import com.wgxhl.recipe.user.entity.AppUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Component
public class ReminderDeliveryGuard {
    private final JdbcTemplate db;
    public ReminderDeliveryGuard(JdbcTemplate db) { this.db = db; }

    public boolean claim(Todo todo, TodoNoticeRule rule, LocalDateTime target, String channel, AppUser owner) {
        String household = TenantContext.require();
        String recipient = owner == null || owner.getId() == null ? "" : owner.getId();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        try {
            db.update("insert into todo_delivery_guard (household_id,todo_id,rule_id,target_time,notify_type,recipient_key,status,attempts,update_time) values (?,?,?,?,?,?,'IN_FLIGHT',1,?)",
                    household, todo.getId(), rule.getId(), Timestamp.valueOf(target), channel, recipient, now);
            return true;
        } catch (DuplicateKeyException duplicate) {
            // The unique key is the durable cross-instance claim.
        }
        return db.update("update todo_delivery_guard set status='IN_FLIGHT', attempts=attempts+1, update_time=? where household_id=? and todo_id=? and rule_id=? and target_time=? and notify_type=? and recipient_key=? and status='FAILED' and attempts<3 and update_time<=?",
                now, household, todo.getId(), rule.getId(), Timestamp.valueOf(target), channel, recipient,
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1))) == 1;
    }

    public void finish(Todo todo, TodoNoticeRule rule, LocalDateTime target, String channel, AppUser owner, String status) {
        String recipient = owner == null || owner.getId() == null ? "" : owner.getId();
        db.update("update todo_delivery_guard set status=?, update_time=? where household_id=? and todo_id=? and rule_id=? and target_time=? and notify_type=? and recipient_key=? and status='IN_FLIGHT'",
                status, Timestamp.valueOf(LocalDateTime.now()), TenantContext.require(), todo.getId(), rule.getId(),
                Timestamp.valueOf(target), channel, recipient);
    }

    public boolean hasRetryable(Todo todo, TodoNoticeRule rule, LocalDateTime target) {
        Integer count = db.queryForObject("select count(*) from todo_delivery_guard where household_id=? and todo_id=? and rule_id=? and target_time=? and status='FAILED' and attempts<3",
                Integer.class, TenantContext.require(), todo.getId(), rule.getId(), Timestamp.valueOf(target));
        return count != null && count > 0;
    }
}
