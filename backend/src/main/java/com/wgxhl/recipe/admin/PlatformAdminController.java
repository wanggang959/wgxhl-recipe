package com.wgxhl.recipe.admin;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.AuthRequestAttributes;
import com.wgxhl.recipe.todo.task.TodoNotifyTask;
import com.wgxhl.recipe.user.entity.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Global queries are available only behind the platform-admin interceptor. */
@RestController
@RequestMapping("/platform-admin")
public class PlatformAdminController {
    private static final Logger log = LoggerFactory.getLogger(PlatformAdminController.class);
    private final JdbcTemplate db;
    private final TodoNotifyTask task;
    private final String adminId;

    public PlatformAdminController(JdbcTemplate db, TodoNotifyTask task,
            @Value("${app.platform-admin-user-id:}") String adminId) {
        this.db = db;
        this.task = task;
        this.adminId = adminId;
    }

    @GetMapping("/access")
    public ApiResponse<Boolean> access() { return ApiResponse.success(true); }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("users", count("select count(*) from app_user"));
        data.put("households", count("select count(*) from household"));
        data.put("recipes", count("select count(*) from recipe"));
        data.put("todos", count("select count(*) from todo where status = 'TODO'"));
        data.put("sent24h", count("select count(*) from todo_send_log where send_status = 'SUCCESS' and send_time >= now() - interval 1 day"));
        data.put("failed24h", count("select count(*) from todo_send_log where send_status = 'FAIL' and send_time >= now() - interval 1 day"));
        data.put("scanStartedAt", task.getLastStartedAt());
        data.put("scanFinishedAt", task.getLastFinishedAt());
        data.put("scanRunning", task.isRunning());
        return ApiResponse.success(data);
    }

    @GetMapping("/users")
    public ApiResponse<Map<String, Object>> users(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "") String keyword) {
        int safePage = Math.max(1, Math.min(page, 5000));
        String query = keyword == null ? "" : keyword.trim();
        if (query.length() > 100) return ApiResponse.fail("搜索内容过长");
        String like = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        String where = " where (u.username like ? escape '!' or u.nickname like ? escape '!' or u.email like ? escape '!')";
        Long total = db.queryForObject("select count(*) from app_user u" + where, Long.class, like, like, like);
        List<Map<String, Object>> rows = db.queryForList("select u.id, u.username, u.nickname, u.email, u.status, u.create_time, "
                + "(select group_concat(h.household_name order by h.household_name separator ', ') from household_member hm "
                + "join household h on h.id = hm.household_id where hm.user_id = u.id and hm.status = 'normal') households "
                + "from app_user u" + where + " order by u.create_time desc, u.id limit 20 offset ?", like, like, like, (safePage - 1) * 20);
        Map<String, Object> result = page(rows, total, safePage);
        return ApiResponse.success(result);
    }

    @PostMapping("/users/status")
    public ApiResponse<Void> status(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String id = body.get("id"), status = body.get("status");
        if (!StringUtils.hasText(id) || !("normal".equals(status) || "disabled".equals(status))) return ApiResponse.fail("参数无效");
        if (id.equals(adminId)) return ApiResponse.fail("不能停用系统管理员");
        int changed = db.update("update app_user set status = ?, auth_version = coalesce(auth_version, 0) + 1, update_time = now() "
                + "where id = ? and user_role <> 'super_admin' and status <> ?", status, id, status);
        if (changed == 0) return ApiResponse.fail("用户不存在、状态未改变或不能修改该账号");
        AppUser actor = (AppUser) request.getAttribute(AuthRequestAttributes.CURRENT_USER);
        log.info("Platform account status changed, actor={}, user={}, status={}", actor.getId(), id, status);
        return ApiResponse.success(null);
    }

    @GetMapping("/notices")
    public ApiResponse<Map<String, Object>> notices(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "") String channel,
            @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "") String from,
            @RequestParam(defaultValue = "") String to) {
        int safePage = Math.max(1, Math.min(page, 5000));
        if (!Arrays.asList("", "SUCCESS", "FAIL", "SKIP").contains(status)
                || !Arrays.asList("", "site", "email", "push").contains(channel)) return ApiResponse.fail("筛选条件无效");
        String search = keyword.trim();
        if (search.length() > 100) return ApiResponse.fail("搜索内容过长");
        String like = "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        LocalDate start, end;
        try {
            start = from.isEmpty() ? null : LocalDate.parse(from);
            end = to.isEmpty() ? null : LocalDate.parse(to);
        } catch (DateTimeParseException ex) { return ApiResponse.fail("日期格式无效"); }
        if ((start != null && (start.getYear() < 1000 || start.getYear() > 9998))
                || (end != null && (end.getYear() < 1000 || end.getYear() > 9998))) return ApiResponse.fail("日期超出范围");
        if (start != null && end != null && end.isBefore(start)) return ApiResponse.fail("结束日期不能早于开始日期");
        String where = " where (? = '' or l.send_status = ?) and (? = '' or l.notify_type = ?)"
                + " and (? = '' or u.username like ? escape '!' or u.nickname like ? escape '!')"
                + (start == null ? "" : " and l.send_time >= ?") + (end == null ? "" : " and l.send_time < ?");
        List<Object> params = new ArrayList<>(Arrays.asList(status, status, channel, channel, search, like, like));
        if (start != null) params.add(start.atStartOfDay());
        if (end != null) params.add(end.plusDays(1).atStartOfDay());
        String join = " from todo_send_log l left join app_user u on u.id = l.recipient_id";
        Long total = db.queryForObject("select count(*)" + join + where, Long.class, params.toArray());
        params.add((safePage - 1) * 20);
        List<Map<String, Object>> rows = db.queryForList("select l.id, l.todo_id, l.recipient_id, l.notify_type, l.send_status, "
                + "l.send_time, l.target_time, l.error_msg, u.nickname recipient_name "
                + join + where + " order by l.send_time desc, l.id limit 20 offset ?", params.toArray());
        return ApiResponse.success(page(rows, total, safePage));
    }

    @GetMapping("/notices/uncertain")
    public ApiResponse<Map<String, Object>> uncertain(@RequestParam(defaultValue = "1") int page) {
        int safePage = Math.max(1, Math.min(page, 5000));
        String where = " where (g.status='UNKNOWN' or (g.status='IN_FLIGHT' and g.update_time < now() - interval 5 minute))";
        Long total = db.queryForObject("select count(*) from todo_delivery_guard g" + where, Long.class);
        List<Map<String, Object>> rows = db.queryForList("select g.todo_id, g.recipient_key, g.notify_type, g.status, g.target_time, g.update_time, u.nickname recipient_name "
                + "from todo_delivery_guard g left join app_user u on u.id=g.recipient_key" + where
                + " order by g.update_time desc, g.todo_id limit 20 offset ?", (safePage - 1) * 20);
        Map<String, Object> result = page(rows, total, safePage);
        result.put("gaps", db.queryForList("select c.household_id, h.household_name, c.last_gap_start, c.last_gap_end from todo_scan_checkpoint c left join household h on h.id=c.household_id where c.last_gap_start is not null order by c.last_gap_end desc limit 20"));
        return ApiResponse.success(result);
    }

    private long count(String sql) { return Objects.requireNonNull(db.queryForObject(sql, Long.class)); }
    private Map<String, Object> page(List<Map<String, Object>> rows, Long total, int page) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", rows); result.put("total", total); result.put("page", page);
        return result;
    }
}
