package com.wgxhl.recipe.todo.task;

import com.wgxhl.recipe.config.TenantContext;
import com.wgxhl.recipe.todo.entity.Todo;
import com.wgxhl.recipe.todo.entity.TodoNoticeRule;
import com.wgxhl.recipe.user.entity.AppUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.wgxhl.recipe.todo.dto.ReminderRecoveryDTO;
import cn.hutool.core.util.IdUtil;
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
        return db.update("update todo_delivery_guard set status='IN_FLIGHT', attempts=attempts+1, update_time=?,next_retry_time=null where household_id=? and todo_id=? and rule_id=? and target_time=? and notify_type=? and recipient_key=? and status='FAILED' and attempts<3 and update_time<=? and (next_retry_time is null or next_retry_time<=?)",
                now, household, todo.getId(), rule.getId(), Timestamp.valueOf(target), channel, recipient,
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), now) == 1;
    }

    public void finish(Todo todo, TodoNoticeRule rule, LocalDateTime target, String channel, AppUser owner, String status) {
        String recipient = owner == null || owner.getId() == null ? "" : owner.getId();
        db.update("update todo_delivery_guard set status=?, update_time=?,next_retry_time=null where household_id=? and todo_id=? and rule_id=? and target_time=? and notify_type=? and recipient_key=? and status='IN_FLIGHT'",
                status, Timestamp.valueOf(LocalDateTime.now()), TenantContext.require(), todo.getId(), rule.getId(),
                Timestamp.valueOf(target), channel, recipient);
    }

    public void retryLater(Todo todo, TodoNoticeRule rule, LocalDateTime target, String channel, AppUser owner, int minutes) {
        LocalDateTime now = LocalDateTime.now();
        db.update("update todo_delivery_guard set status='FAILED',update_time=?,next_retry_time=? where household_id=? and todo_id=? and rule_id=? and target_time=? and notify_type=? and recipient_key=? and status='IN_FLIGHT'",
                Timestamp.valueOf(now), Timestamp.valueOf(now.plusMinutes(Math.max(1, minutes))), TenantContext.require(),
                todo.getId(), rule.getId(), Timestamp.valueOf(target), channel, owner == null || owner.getId() == null ? "" : owner.getId());
    }

    public boolean hasRetryable(Todo todo, TodoNoticeRule rule, LocalDateTime target) {
        Integer count = db.queryForObject("select count(*) from todo_delivery_guard where household_id=? and todo_id=? and rule_id=? and target_time=? and status='FAILED' and attempts<3",
                Integer.class, TenantContext.require(), todo.getId(), rule.getId(), Timestamp.valueOf(target));
        return count != null && count > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordCoalesced(Todo todo, TodoNoticeRule rule, LocalDateTime target, TodoNoticeRule selected) {
        String home = TenantContext.require();
        Integer accepted = db.queryForObject("select count(*) from todo_delivery_guard where household_id=? and todo_id=? and rule_id=? and target_time=? and status='SUCCESS'",
                Integer.class, home, todo.getId(), rule.getId(), Timestamp.valueOf(target));
        if (accepted != null && accepted > 0) return;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        try {
            db.update("insert into todo_delivery_guard (household_id,todo_id,rule_id,target_time,notify_type,recipient_key,status,attempts,update_time) values (?,?,?,?,'coalesced','','SKIP',0,?)",
                    home, todo.getId(), rule.getId(), Timestamp.valueOf(target), now);
        } catch (DuplicateKeyException duplicate) {
            return;
        }
        LocalDateTime latest = todo.getDueTime().minusMinutes(Math.max(0, selected.getBeforeMinutes() == null ? 0 : selected.getBeforeMinutes()));
        db.update("insert into todo_send_log (id,household_id,todo_id,rule_id,target_time,notify_type,send_time,send_status,error_msg,create_time,update_time) values (?,?,?,?,?,'coalesced',?,'SKIP',?,?,?)",
                IdUtil.simpleUUID(), home, todo.getId(), rule.getId(), Timestamp.valueOf(target), now,
                "补扫合并：本轮仅采用 " + latest.toString().replace('T', ' ') + " 的规则，较早规则未再次发送", now, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public String beginRecovery(ReminderRecoveryDTO r, String actor) {
        boolean close = "CLOSE".equals(r.getAction());
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        int changed = db.update("update todo_delivery_guard set status=?,attempts=attempts+?,update_time=?,next_retry_time=null "
                + "where household_id=? and todo_id=? and rule_id=? and target_time=? and notify_type=? and recipient_key=? "
                + "and update_time=? and (status in ('UNKNOWN','REJECTED') or (status='FAILED' and attempts>=3) "
                + "or (status='IN_FLIGHT' and update_time<?))", close ? "SKIP" : "IN_FLIGHT", close ? 0 : 1, now,
                TenantContext.require(), r.getTodoId(), r.getRuleId(), Timestamp.valueOf(r.getTargetTime()), r.getChannel(),
                r.getRecipientId(), Timestamp.valueOf(r.getExpectedUpdateTime()), Timestamp.valueOf(LocalDateTime.now().minusMinutes(5)));
        if (changed != 1) return null;
        String id = IdUtil.simpleUUID();
        db.update("insert into todo_send_log (id,household_id,todo_id,rule_id,target_time,recipient_id,notify_type,send_time,send_status,error_msg,create_time,update_time) values (?,?,?,?,?,?,?,?,?,?,?,?)",
                id, TenantContext.require(), r.getTodoId(), r.getRuleId(), Timestamp.valueOf(r.getTargetTime()), r.getRecipientId(),
                r.getChannel(), now, close ? "SKIP" : "PENDING", "管理员 " + actor + (close ? " 确认关闭，不再补发" : " 确认补发，已知可能重复"), now, now);
        return id;
    }

    @Transactional(rollbackFor = Exception.class)
    public void finishRecovery(ReminderRecoveryDTO r, String logId, String status, String message) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        db.update("update todo_delivery_guard set status=?,update_time=? where household_id=? and todo_id=? and rule_id=? and target_time=? and notify_type=? and recipient_key=? and status='IN_FLIGHT'",
                status, now, TenantContext.require(), r.getTodoId(), r.getRuleId(), Timestamp.valueOf(r.getTargetTime()), r.getChannel(), r.getRecipientId());
        db.update("update todo_send_log set send_status=?,error_msg=concat(error_msg,?),update_time=? where id=? and household_id=? and send_status='PENDING'",
                "SUCCESS".equals(status) ? "SUCCESS" : "FAIL", "；" + message, now, logId, TenantContext.require());
    }
}
