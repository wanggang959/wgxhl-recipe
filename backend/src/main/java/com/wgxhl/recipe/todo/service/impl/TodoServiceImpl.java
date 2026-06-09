package com.wgxhl.recipe.todo.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.date.ChineseDate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.notification.service.NotificationService;
import com.wgxhl.recipe.push.service.UserPushSubscriptionService;
import com.wgxhl.recipe.todo.dto.TodoPageDTO;
import com.wgxhl.recipe.todo.dto.TodoSummaryDTO;
import com.wgxhl.recipe.todo.entity.Todo;
import com.wgxhl.recipe.todo.entity.TodoNoticeRule;
import com.wgxhl.recipe.todo.entity.TodoOwner;
import com.wgxhl.recipe.todo.entity.TodoSendLog;
import com.wgxhl.recipe.todo.mapper.TodoMapper;
import com.wgxhl.recipe.todo.mapper.TodoNoticeRuleMapper;
import com.wgxhl.recipe.todo.mapper.TodoOwnerMapper;
import com.wgxhl.recipe.todo.mapper.TodoSendLogMapper;
import com.wgxhl.recipe.todo.service.TodoService;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import com.wgxhl.recipe.user.service.impl.AppUserServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TodoServiceImpl extends ServiceImpl<TodoMapper, Todo> implements TodoService {

    private static final Logger log = LoggerFactory.getLogger(TodoServiceImpl.class);

    private static final String STATUS_TODO = "TODO";
    private static final String STATUS_DONE = "DONE";
    private static final String REPEAT_NONE = "NONE";
    private static final String RELATED_BIRTHDAY = "MEMBER_BIRTHDAY";
    private static final String NOTICE_SITE = "site";
    private static final String NOTICE_EMAIL = "email";
    private static final String NOTICE_PUSH = "push";
    private static final LocalTime BIRTHDAY_ON_TIME = LocalTime.of(11, 0);
    private static final long NOTIFY_SCAN_WINDOW_MINUTES = 6L;

    private final TodoNoticeRuleMapper todoNoticeRuleMapper;
    private final TodoOwnerMapper todoOwnerMapper;
    private final TodoSendLogMapper todoSendLogMapper;
    private final AppUserMapper appUserMapper;
    private final NotificationService notificationService;
    private final UserPushSubscriptionService userPushSubscriptionService;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public TodoServiceImpl(TodoNoticeRuleMapper todoNoticeRuleMapper,
                           TodoOwnerMapper todoOwnerMapper,
                           TodoSendLogMapper todoSendLogMapper,
                           AppUserMapper appUserMapper,
                           NotificationService notificationService,
                           UserPushSubscriptionService userPushSubscriptionService,
                           ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.todoNoticeRuleMapper = todoNoticeRuleMapper;
        this.todoOwnerMapper = todoOwnerMapper;
        this.todoSendLogMapper = todoSendLogMapper;
        this.appUserMapper = appUserMapper;
        this.notificationService = notificationService;
        this.userPushSubscriptionService = userPushSubscriptionService;
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    @Override
    public boolean save(Todo entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        normalize(entity);
        normalizeLunarDueTime(entity);
        normalizeSolarBirthdayDueTime(entity);
        return super.save(entity);
    }

    @Override
    public boolean updateById(Todo entity) {
        entity.setUpdateTime(LocalDateTime.now());
        normalize(entity);
        normalizeLunarDueTime(entity);
        normalizeSolarBirthdayDueTime(entity);
        return super.updateById(entity);
    }

    @Override
    public ApiResponse<Page<Todo>> page(TodoPageDTO dto) {
        Page<Todo> page = new Page<>(dto.getCurrent(), dto.getSize());
        Page<Todo> result = lambdaQuery()
                .like(StringUtils.hasText(dto.getTitle()), Todo::getTitle, dto.getTitle())
                .eq(StringUtils.hasText(dto.getCategory()), Todo::getCategory, dto.getCategory())
                .in(StringUtils.hasText(dto.getOwnerId()), Todo::getId, todoIdsByOwner(dto.getOwnerId()))
                .eq(StringUtils.hasText(dto.getStatus()), Todo::getStatus, dto.getStatus())
                .ge(dto.getDueTimeStart() != null, Todo::getDueTime, dto.getDueTimeStart())
                .le(dto.getDueTimeEnd() != null, Todo::getDueTime, dto.getDueTimeEnd())
                .orderByAsc(Todo::getStatus)
                .orderByAsc(Todo::getDueTime)
                .orderByDesc(Todo::getCreateTime)
                .page(page);
        enrich(result.getRecords());
        return ApiResponse.success(result);
    }

    @Override
    public ApiResponse<List<Todo>> upcoming(int limit) {
        int size = limit <= 0 ? 3 : Math.min(limit, 20);
        List<Todo> list = lambdaQuery()
                .eq(Todo::getStatus, STATUS_TODO)
                .ge(Todo::getDueTime, LocalDateTime.now().minusHours(2))
                .orderByAsc(Todo::getDueTime)
                .last("limit " + size)
                .list();
        enrich(list);
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<TodoSummaryDTO> summary() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay().minusNanos(1);
        TodoSummaryDTO dto = new TodoSummaryDTO();
        dto.setTodayCount(lambdaQuery()
                .eq(Todo::getStatus, STATUS_TODO)
                .ge(Todo::getDueTime, start)
                .le(Todo::getDueTime, end)
                .count());
        dto.setDoneCount(lambdaQuery().eq(Todo::getStatus, STATUS_DONE).count());
        dto.setDueSoonCount(lambdaQuery()
                .eq(Todo::getStatus, STATUS_TODO)
                .ge(Todo::getDueTime, LocalDateTime.now())
                .le(Todo::getDueTime, LocalDateTime.now().plusDays(7))
                .count());
        dto.setTotalCount(count());
        return ApiResponse.success(dto);
    }

    @Override
    public ApiResponse<Todo> detail(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("待办id不能为空");
        }
        Todo todo = super.getById(id);
        if (todo == null) {
            return ApiResponse.fail("待办不存在");
        }
        enrich(Collections.singletonList(todo));
        return ApiResponse.success(todo);
    }

    private String completeDisabledReason(Todo todo) {
        if (todo == null) {
            return "待办不存在";
        }
        if (STATUS_DONE.equals(todo.getStatus())) {
            return "待办已完成";
        }
        if (todo.getDueTime() == null) {
            return "请先填写到期时间";
        }
        LocalDate today = LocalDate.now();
        if ("BIRTHDAY".equals(todo.getCategory())) {
            return todo.getDueTime().toLocalDate().equals(today) ? null : "生日当天才能完成";
        }
        LocalDateTime firstNoticeTime = firstNoticeTime(todo);
        if (LocalDateTime.now().isBefore(firstNoticeTime)) {
            return "第一次提醒后才能完成";
        }
        return null;
    }

    private LocalDateTime firstNoticeTime(Todo todo) {
        int maxBeforeMinutes = selectRules(todo.getId()).stream()
                .map(TodoNoticeRule::getBeforeMinutes)
                .filter(minute -> minute != null && minute > 0)
                .max(Integer::compareTo)
                .orElse(0);
        return todo.getDueTime().minusMinutes(maxBeforeMinutes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Todo> create(Todo entity, AppUser actor) {
        if (!StringUtils.hasText(entity.getTitle())) {
            return ApiResponse.fail("标题不能为空");
        }
        if (!StringUtils.hasText(entity.getCategory())) {
            entity.setCategory("OTHER");
        }
        if (CollectionUtils.isEmpty(entity.getOwnerIds())) {
            if (StringUtils.hasText(entity.getOwnerId())) {
                entity.setOwnerIds(Collections.singletonList(entity.getOwnerId()));
            } else if (actor != null) {
                entity.setOwnerIds(Collections.singletonList(actor.getId()));
                entity.setOwnerId(actor.getId());
            }
        } else {
            entity.setOwnerId(firstOwnerId(entity.getOwnerIds()));
        }
        save(entity);
        replaceOwners(entity.getId(), entity.getOwnerIds());
        replaceRules(entity.getId(), resolveNoticeMinutes(entity));
        enrich(Collections.singletonList(entity));
        return ApiResponse.success("已创建待办", entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> update(Todo entity) {
        if (!StringUtils.hasText(entity.getId())) {
            return ApiResponse.fail("待办id不能为空");
        }
        Todo existing = super.getById(entity.getId());
        if (existing == null) {
            return ApiResponse.fail("待办不存在");
        }
        if (!CollectionUtils.isEmpty(entity.getOwnerIds())) {
            entity.setOwnerId(firstOwnerId(entity.getOwnerIds()));
        }
        updateById(entity);
        replaceOwners(entity.getId(), entity.getOwnerIds());
        replaceRules(entity.getId(), resolveNoticeMinutes(entity));
        return ApiResponse.success("已更新待办", null);
    }

    @Override
    public ApiResponse<Void> complete(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("待办id不能为空");
        }
        Todo todo = super.getById(id);
        if (todo == null) {
            return ApiResponse.fail("待办不存在");
        }
        String disabledReason = completeDisabledReason(todo);
        if (StringUtils.hasText(disabledReason)) {
            return ApiResponse.fail(disabledReason);
        }
        if ("BIRTHDAY".equals(todo.getCategory())) {
            LocalDate next = resolveNextBirthdayDueDate(todo);
            if (next == null) {
                return ApiResponse.fail("无法计算下一次生日日期");
            }
            LocalTime time = todo.getDueTime().toLocalTime();
            if (time.equals(LocalTime.MIDNIGHT)) {
                time = BIRTHDAY_ON_TIME;
            }
            todo.setDueTime(LocalDateTime.of(next, time));
            todo.setStatus(STATUS_TODO);
            updateById(todo);
            return ApiResponse.success("已进入下一年生日提醒", null);
        }
        todo.setStatus(STATUS_DONE);
        updateById(todo);
        return ApiResponse.success("已完成", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> delete(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("待办id不能为空");
        }
        removeById(id);
        todoNoticeRuleMapper.delete(new LambdaQueryWrapper<TodoNoticeRule>().eq(TodoNoticeRule::getTodoId, id));
        todoOwnerMapper.delete(new LambdaQueryWrapper<TodoOwner>().eq(TodoOwner::getTodoId, id));
        return ApiResponse.success("已删除", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncBirthdayTodo(AppUser user) {
        if (user == null || !StringUtils.hasText(user.getId())) {
            return;
        }
        LocalDate sourceBirthday = "LUNAR".equals(user.getBirthdayCalendar())
                ? AppUserServiceImpl.nextLunarBirthday(user, LocalDate.now())
                : user.getBirthday();
        if (sourceBirthday == null) {
            return;
        }
        LocalDate birthday = nextBirthday(sourceBirthday);
        Todo todo = lambdaQuery()
                .eq(Todo::getRelatedType, RELATED_BIRTHDAY)
                .eq(Todo::getRelatedId, user.getId())
                .one();
        if (todo == null) {
            todo = new Todo();
            todo.setRelatedType(RELATED_BIRTHDAY);
            todo.setRelatedId(user.getId());
        }
        String name = StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
        todo.setTitle(name + "生日");
        todo.setCategory("BIRTHDAY");
        List<String> birthdayOwnerIds = birthdayReminderOwnerIds(user.getId());
        todo.setOwnerId(birthdayOwnerIds.isEmpty() ? null : birthdayOwnerIds.get(0));
        todo.setOwnerIds(birthdayOwnerIds);
        todo.setDueTime(LocalDateTime.of(birthday, BIRTHDAY_ON_TIME));
        todo.setDueCalendar("LUNAR".equals(user.getBirthdayCalendar()) ? "LUNAR" : "SOLAR");
        todo.setLunarDueMonth(user.getLunarBirthdayMonth());
        todo.setLunarDueDay(user.getLunarBirthdayDay());
        todo.setLunarDueLeap(user.getLunarBirthdayLeap());
        todo.setBirthdayYear("LUNAR".equals(user.getBirthdayCalendar())
                ? user.getLunarBirthdayYear()
                : (user.getBirthday() == null ? null : user.getBirthday().getYear()));
        todo.setRepeatType("YEARLY");
        todo.setNotifySite(true);
        todo.setNotifyEmail(true);
        todo.setNotifyPush(false);
        todo.setStatus(STATUS_TODO);
        if (StringUtils.hasText(todo.getId())) {
            updateById(todo);
            replaceOwners(todo.getId(), todo.getOwnerIds());
            replaceRules(todo.getId(), defaultMinutes("BIRTHDAY"));
        } else {
            save(todo);
            replaceOwners(todo.getId(), todo.getOwnerIds());
            replaceRules(todo.getId(), defaultMinutes("BIRTHDAY"));
        }
    }

    private List<String> birthdayReminderOwnerIds(String birthdayUserId) {
        return appUserMapper.selectList(new LambdaQueryWrapper<AppUser>()
                        .eq(AppUser::getStatus, "normal")
                        .ne(AppUser::getId, "guest")
                        .ne(StringUtils.hasText(birthdayUserId), AppUser::getId, birthdayUserId))
                .stream()
                .map(AppUser::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    @Override
    public int scanAndNotify() {
        syncAllBirthdayTodos();
        syncSolarYearlyBirthdayTodos();
        syncLunarYearlyTodos();
        LocalDateTime now = LocalDateTime.now();
        List<Todo> todos = lambdaQuery()
                .eq(Todo::getStatus, STATUS_TODO)
                .isNotNull(Todo::getDueTime)
                .ge(Todo::getDueTime, now.minusDays(31))
                .le(Todo::getDueTime, now.plusDays(31))
                .list();
        int count = 0;
        for (Todo todo : todos) {
            List<TodoNoticeRule> rules = selectRules(todo.getId());
            for (TodoNoticeRule rule : rules) {
                LocalDateTime targetTime = todo.getDueTime().minusMinutes(rule.getBeforeMinutes() == null ? 0 : rule.getBeforeMinutes());
                if (!isInNotifyWindow(targetTime, now) || hasSent(todo.getId(), rule.getId(), targetTime)) {
                    continue;
                }
                count += sendByRule(todo, rule, targetTime);
            }
        }
        return count;
    }

    private boolean isInNotifyWindow(LocalDateTime targetTime, LocalDateTime now) {
        return !now.isBefore(targetTime) && !targetTime.isBefore(now.minusMinutes(NOTIFY_SCAN_WINDOW_MINUTES));
    }

    private void syncAllBirthdayTodos() {
        List<AppUser> users = appUserMapper.selectList(null);
        for (AppUser user : users) {
            if (user == null || "guest".equals(user.getId()) || !"normal".equals(user.getStatus())) {
                continue;
            }
            if ("LUNAR".equals(user.getBirthdayCalendar())
                    || user.getBirthday() != null) {
                syncBirthdayTodo(user);
            }
        }
    }

    private int sendByRule(Todo todo, TodoNoticeRule rule, LocalDateTime targetTime) {
        int sent = 0;
        String title = "【王师傅家提醒】" + todo.getTitle();
        String content = buildNoticeContent(todo, rule);
        List<AppUser> owners = resolveOwners(todo);
        if (owners.isEmpty()) {
            owners = Collections.singletonList(null);
        }
        log.info("Todo notify start, todoId={}, title={}, ruleId={}, targetTime={}, owners={}, notifySite={}, notifyEmail={}, notifyPush={}",
                todo.getId(), todo.getTitle(), rule.getId(), targetTime, owners.size(),
                todo.getNotifySite(), todo.getNotifyEmail(), todo.getNotifyPush());
        for (AppUser owner : owners) {
            if (!Boolean.TRUE.equals(todo.getNotifySite())) {
                log.info("Todo site notice skipped, todoId={}, owner={}, reason=disabled", todo.getId(), ownerLabel(owner));
            } else if (!allowNotify(owner, NOTICE_SITE)) {
                log.info("Todo site notice skipped, todoId={}, owner={}, preference={}, reason=preference",
                        todo.getId(), ownerLabel(owner), owner == null ? null : owner.getNotificationPreference());
            } else {
                String userId = owner == null ? null : owner.getId();
                notificationService.createSiteNotice(userId, todo.getTitle(), content, "TODO", todo.getId());
                logSend(todo, rule, NOTICE_SITE, "SUCCESS", null);
                log.info("Todo site notice sent, todoId={}, owner={}, userId={}", todo.getId(), ownerLabel(owner), userId);
                sent++;
            }

            if (!Boolean.TRUE.equals(todo.getNotifyEmail())) {
                log.info("Todo email skipped, todoId={}, owner={}, reason=disabled", todo.getId(), ownerLabel(owner));
            } else if (!allowNotify(owner, NOTICE_EMAIL)) {
                log.info("Todo email skipped, todoId={}, owner={}, preference={}, reason=preference",
                        todo.getId(), ownerLabel(owner), owner == null ? null : owner.getNotificationPreference());
            } else {
                try {
                    sendEmail(owner, title, content);
                    logSend(todo, rule, NOTICE_EMAIL, "SUCCESS", null);
                    log.info("Todo email sent, todoId={}, owner={}, to={}", todo.getId(), ownerLabel(owner), maskEmail(owner == null ? null : owner.getEmail()));
                    sent++;
                } catch (Exception ex) {
                    log.warn("Todo email failed, todoId={}, owner={}, to={}, reason={}",
                            todo.getId(), ownerLabel(owner), maskEmail(owner == null ? null : owner.getEmail()), ex.getMessage(), ex);
                    logSend(todo, rule, NOTICE_EMAIL, "FAIL", ex.getMessage());
                }
            }
        }
        if (!Boolean.TRUE.equals(todo.getNotifyPush())) {
            log.info("Todo push skipped, todoId={}, reason=disabled", todo.getId());
        } else {
            List<String> pushUserIds = owners.stream()
                    .filter(owner -> allowNotify(owner, NOTICE_PUSH))
                    .map(owner -> owner == null ? null : owner.getId())
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());
            List<String> skippedPushOwners = owners.stream()
                    .filter(owner -> !allowNotify(owner, NOTICE_PUSH))
                    .map(this::ownerLabel)
                    .collect(Collectors.toList());
            if (!skippedPushOwners.isEmpty()) {
                log.info("Todo push preference skipped, todoId={}, owners={}", todo.getId(), skippedPushOwners);
            }
            if (pushUserIds.isEmpty()) {
                log.info("Todo push skipped, todoId={}, reason=no_allowed_owner", todo.getId());
            } else {
                int delivered = userPushSubscriptionService.sendToUserIds(
                        pushUserIds,
                        todo.getTitle(),
                        content,
                        "/#/todo/" + todo.getId(),
                        "todo-" + todo.getId() + "-" + rule.getId()
                );
                if (delivered > 0) {
                    logSend(todo, rule, NOTICE_PUSH, "SUCCESS", "delivered=" + delivered);
                    log.info("Todo push sent, todoId={}, targetUserIds={}, delivered={}", todo.getId(), pushUserIds, delivered);
                    sent += delivered;
                } else {
                    logSend(todo, rule, NOTICE_PUSH, "SKIP", "客户端未开启PWA通知或服务端未配置");
                    log.info("Todo push skipped, todoId={}, targetUserIds={}, delivered=0", todo.getId(), pushUserIds);
                }
            }
        }
        if (sent == 0
                && !Boolean.TRUE.equals(todo.getNotifyEmail())
                && !Boolean.TRUE.equals(todo.getNotifySite())
                && !Boolean.TRUE.equals(todo.getNotifyPush())) {
            logSend(todo, rule, "none", "SKIP", "未开启通知方式");
        }
        return sent;
    }

    private String ownerLabel(AppUser owner) {
        if (owner == null) {
            return "none";
        }
        String name = StringUtils.hasText(owner.getNickname()) ? owner.getNickname() : owner.getUsername();
        return StringUtils.hasText(name) ? owner.getId() + "(" + name + ")" : owner.getId();
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "none";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + (atIndex >= 0 ? email.substring(atIndex) : "");
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    private void sendEmail(AppUser owner, String title, String content) {
        if (mailSender == null || !StringUtils.hasText(mailUsername)) {
            throw new IllegalStateException("邮箱服务未配置");
        }
        if (owner == null || !StringUtils.hasText(owner.getEmail())) {
            throw new IllegalStateException("负责人邮箱未配置");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(owner.getEmail());
        message.setSubject(title);
        message.setText(content);
        mailSender.send(message);
    }

    private boolean allowNotify(AppUser owner, String type) {
        if (owner == null || !StringUtils.hasText(owner.getNotificationPreference())) {
            return true;
        }
        Set<String> prefs = Arrays.stream(owner.getNotificationPreference().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        return prefs.contains(type);
    }

    private String buildNoticeContent(Todo todo, TodoNoticeRule rule) {
        int minutes = rule.getBeforeMinutes() == null ? 0 : rule.getBeforeMinutes();
        String remain = minutes == 0 ? "现在到点啦。" : "还有" + humanDuration(minutes) + "。";
        if ("BIRTHDAY".equals(todo.getCategory())) {
            String birthdayText = birthdayDisplayText(todo);
            String desc = StringUtils.hasText(todo.getDescription()) ? "\n" + todo.getDescription() : "";
            return birthdayText + remain + desc;
        }
        String due = todo.getDueTime() == null ? "" : "\n到期时间：" + todo.getDueTime().toString().replace('T', ' ');
        String desc = StringUtils.hasText(todo.getDescription()) ? "\n" + todo.getDescription() : "";
        return todo.getTitle() + remain + due + desc;
    }

    private String humanDuration(int minutes) {
        if (minutes >= 1440) {
            return (minutes / 1440) + "天";
        }
        if (minutes >= 60) {
            return (minutes / 60) + "小时";
        }
        return minutes + "分钟";
    }

    private boolean hasSent(String todoId, String ruleId, LocalDateTime targetTime) {
        return todoSendLogMapper.selectCount(new LambdaQueryWrapper<TodoSendLog>()
                .eq(TodoSendLog::getTodoId, todoId)
                .eq(TodoSendLog::getRuleId, ruleId)
                .eq(TodoSendLog::getSendStatus, "SUCCESS")
                .ge(TodoSendLog::getSendTime, targetTime)) > 0;
    }

    private void logSend(Todo todo, TodoNoticeRule rule, String type, String status, String errorMsg) {
        TodoSendLog log = new TodoSendLog();
        log.setId(IdUtil.simpleUUID());
        log.setTodoId(todo.getId());
        log.setRuleId(rule.getId());
        log.setNotifyType(type);
        log.setSendTime(LocalDateTime.now());
        log.setSendStatus(status);
        log.setErrorMsg(errorMsg);
        log.setCreateTime(LocalDateTime.now());
        log.setUpdateTime(LocalDateTime.now());
        todoSendLogMapper.insert(log);
    }

    private void normalize(Todo todo) {
        if (!StringUtils.hasText(todo.getCategory())) {
            todo.setCategory("OTHER");
        }
        if (!StringUtils.hasText(todo.getRepeatType())) {
            todo.setRepeatType(REPEAT_NONE);
        }
        if (!StringUtils.hasText(todo.getStatus())) {
            todo.setStatus(STATUS_TODO);
        }
        if (todo.getNotifySite() == null) {
            todo.setNotifySite(true);
        }
        if (todo.getNotifyEmail() == null) {
            todo.setNotifyEmail(false);
        }
        if (todo.getNotifyPush() == null) {
            todo.setNotifyPush(false);
        }
        if (!StringUtils.hasText(todo.getDueCalendar())) {
            todo.setDueCalendar("SOLAR");
        }
        if (!"LUNAR".equals(todo.getDueCalendar())) {
            todo.setDueCalendar("SOLAR");
            todo.setLunarDueMonth(null);
            todo.setLunarDueDay(null);
            todo.setLunarDueLeap(false);
        } else if (todo.getLunarDueLeap() == null) {
            todo.setLunarDueLeap(false);
        }
    }

    private void normalizeLunarDueTime(Todo todo) {
        if (!"LUNAR".equals(todo.getDueCalendar()) || todo.getLunarDueMonth() == null || todo.getLunarDueDay() == null) {
            return;
        }
        LocalDate next = nextLunarDueDate(todo, LocalDate.now());
        if (next != null) {
            LocalTime time = todo.getDueTime() == null ? defaultDueTime(todo) : todo.getDueTime().toLocalTime();
            todo.setDueTime(LocalDateTime.of(next, time));
            todo.setRepeatType("YEARLY");
        }
    }

    private void normalizeSolarBirthdayDueTime(Todo todo) {
        if (!"BIRTHDAY".equals(todo.getCategory())
                || "LUNAR".equals(todo.getDueCalendar())
                || todo.getDueTime() == null) {
            return;
        }
        if (todo.getBirthdayYear() == null) {
            todo.setBirthdayYear(todo.getDueTime().getYear());
        }
        LocalDate next = nextBirthday(todo.getDueTime().toLocalDate());
        LocalTime time = todo.getDueTime().toLocalTime();
        if (time.equals(LocalTime.MIDNIGHT)) {
            time = BIRTHDAY_ON_TIME;
        }
        todo.setDueTime(LocalDateTime.of(next, time));
        todo.setRepeatType("YEARLY");
    }

    private LocalTime defaultDueTime(Todo todo) {
        return "BIRTHDAY".equals(todo.getCategory()) ? BIRTHDAY_ON_TIME : LocalTime.of(9, 0);
    }

    private LocalDate nextLunarDueDate(Todo todo, LocalDate baseDate) {
        LocalDate next = AppUserServiceImpl.lunarToSolar(baseDate.getYear(), todo.getLunarDueMonth(),
                todo.getLunarDueDay(), Boolean.TRUE.equals(todo.getLunarDueLeap()));
        if (next == null || next.isBefore(baseDate)) {
            next = AppUserServiceImpl.lunarToSolar(baseDate.getYear() + 1, todo.getLunarDueMonth(),
                    todo.getLunarDueDay(), Boolean.TRUE.equals(todo.getLunarDueLeap()));
        }
        return next;
    }

    private void syncLunarYearlyTodos() {
        List<Todo> todos = lambdaQuery()
                .eq(Todo::getDueCalendar, "LUNAR")
                .eq(Todo::getRepeatType, "YEARLY")
                .eq(Todo::getStatus, STATUS_TODO)
                .list();
        for (Todo todo : todos) {
            if (todo.getDueTime() == null || !todo.getDueTime().toLocalDate().isBefore(LocalDate.now())) {
                continue;
            }
            normalizeLunarDueTime(todo);
            updateById(todo);
        }
    }

    private void syncSolarYearlyBirthdayTodos() {
        List<Todo> todos = lambdaQuery()
                .eq(Todo::getCategory, "BIRTHDAY")
                .eq(Todo::getDueCalendar, "SOLAR")
                .eq(Todo::getRepeatType, "YEARLY")
                .eq(Todo::getStatus, STATUS_TODO)
                .isNotNull(Todo::getDueTime)
                .list();
        for (Todo todo : todos) {
            if (!todo.getDueTime().toLocalDate().isBefore(LocalDate.now())) {
                continue;
            }
            LocalDate next = nextBirthday(todo.getDueTime().toLocalDate());
            todo.setDueTime(LocalDateTime.of(next, todo.getDueTime().toLocalTime()));
            updateById(todo);
        }
    }

    private void replaceRules(String todoId, List<Integer> minutes) {
        todoNoticeRuleMapper.delete(new LambdaQueryWrapper<TodoNoticeRule>().eq(TodoNoticeRule::getTodoId, todoId));
        for (Integer minute : uniqueMinutes(minutes)) {
            TodoNoticeRule rule = new TodoNoticeRule();
            rule.setId(IdUtil.simpleUUID());
            rule.setTodoId(todoId);
            rule.setBeforeMinutes(Math.max(minute == null ? 0 : minute, 0));
            rule.setBeforeType(rule.getBeforeMinutes() == 0 ? "ON_TIME" : "BEFORE");
            rule.setCreateTime(LocalDateTime.now());
            rule.setUpdateTime(LocalDateTime.now());
            todoNoticeRuleMapper.insert(rule);
        }
    }

    private List<Integer> resolveNoticeMinutes(Todo todo) {
        if (!CollectionUtils.isEmpty(todo.getNoticeMinutes())) {
            return todo.getNoticeMinutes();
        }
        return defaultMinutes(todo.getCategory());
    }

    private List<Integer> defaultMinutes(String category) {
        if ("SERVER".equals(category) || "DOMAIN".equals(category) || "SSL".equals(category)) {
            return Arrays.asList(43200, 10080, 4320, 1440);
        }
        if ("BIRTHDAY".equals(category)) {
            return Arrays.asList(10080, 4320, 0);
        }
        return Arrays.asList(0);
    }

    private List<Integer> uniqueMinutes(List<Integer> minutes) {
        if (CollectionUtils.isEmpty(minutes)) {
            return Collections.singletonList(0);
        }
        Set<Integer> seen = new HashSet<>();
        List<Integer> result = new ArrayList<>();
        for (Integer minute : minutes) {
            int normalized = Math.max(minute == null ? 0 : minute, 0);
            if (seen.add(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private List<TodoNoticeRule> selectRules(String todoId) {
        return todoNoticeRuleMapper.selectList(new LambdaQueryWrapper<TodoNoticeRule>()
                .eq(TodoNoticeRule::getTodoId, todoId)
                .orderByDesc(TodoNoticeRule::getBeforeMinutes));
    }

    private void replaceOwners(String todoId, List<String> ownerIds) {
        todoOwnerMapper.delete(new LambdaQueryWrapper<TodoOwner>().eq(TodoOwner::getTodoId, todoId));
        for (String ownerId : uniqueOwnerIds(ownerIds)) {
            TodoOwner owner = new TodoOwner();
            owner.setId(IdUtil.simpleUUID());
            owner.setTodoId(todoId);
            owner.setOwnerId(ownerId);
            owner.setCreateTime(LocalDateTime.now());
            owner.setUpdateTime(LocalDateTime.now());
            todoOwnerMapper.insert(owner);
        }
    }

    private List<String> uniqueOwnerIds(List<String> ownerIds) {
        if (CollectionUtils.isEmpty(ownerIds)) {
            return Collections.emptyList();
        }
        Set<String> seen = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String ownerId : ownerIds) {
            if (StringUtils.hasText(ownerId) && seen.add(ownerId)) {
                result.add(ownerId);
            }
        }
        return result;
    }

    private String firstOwnerId(List<String> ownerIds) {
        List<String> owners = uniqueOwnerIds(ownerIds);
        return owners.isEmpty() ? null : owners.get(0);
    }

    private List<AppUser> resolveOwners(Todo todo) {
        List<String> ownerIds = loadOwnerIds(todo);
        if (ownerIds.isEmpty() && StringUtils.hasText(todo.getOwnerId())) {
            ownerIds = Collections.singletonList(todo.getOwnerId());
        }
        return ownerIds.stream()
                .map(appUserMapper::selectById)
                .filter(user -> user != null)
                .collect(Collectors.toList());
    }

    private List<String> loadOwnerIds(Todo todo) {
        if (todo == null || !StringUtils.hasText(todo.getId())) {
            return Collections.emptyList();
        }
        List<String> ownerIds = todoOwnerMapper.selectList(new LambdaQueryWrapper<TodoOwner>()
                        .eq(TodoOwner::getTodoId, todo.getId()))
                .stream()
                .map(TodoOwner::getOwnerId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        if (ownerIds.isEmpty() && StringUtils.hasText(todo.getOwnerId())) {
            ownerIds = Collections.singletonList(todo.getOwnerId());
        }
        return ownerIds;
    }

    private List<String> todoIdsByOwner(String ownerId) {
        if (!StringUtils.hasText(ownerId)) {
            return Collections.emptyList();
        }
        List<String> todoIds = todoOwnerMapper.selectList(new LambdaQueryWrapper<TodoOwner>()
                        .eq(TodoOwner::getOwnerId, ownerId))
                .stream()
                .map(TodoOwner::getTodoId)
                .collect(Collectors.toList());
        List<String> legacyIds = lambdaQuery()
                .eq(Todo::getOwnerId, ownerId)
                .select(Todo::getId)
                .list()
                .stream()
                .map(Todo::getId)
                .collect(Collectors.toList());
        Set<String> merged = new HashSet<>(todoIds);
        merged.addAll(legacyIds);
        if (merged.isEmpty()) {
            return Collections.singletonList("__none__");
        }
        return new ArrayList<>(merged);
    }

    private void enrich(List<Todo> todos) {
        if (CollectionUtils.isEmpty(todos)) {
            return;
        }
        for (Todo todo : todos) {
            List<AppUser> owners = resolveOwners(todo);
            if (!owners.isEmpty()) {
                todo.setOwnerIds(owners.stream().map(AppUser::getId).collect(Collectors.toList()));
                todo.setOwnerNames(owners.stream().map(owner -> StringUtils.hasText(owner.getNickname()) ? owner.getNickname() : owner.getUsername()).collect(Collectors.toList()));
                todo.setOwnerName(String.join("、", todo.getOwnerNames()));
                AppUser first = owners.get(0);
                todo.setOwnerAvatar(first.getAvatar());
                todo.setOwnerEmail(first.getEmail());
            }
            if (todo.getDueTime() != null) {
                todo.setDueLabel(formatDueLabel(todo.getDueTime()));
                todo.setRemainText(formatRemain(todo.getDueTime()));
            }
            if ("BIRTHDAY".equals(todo.getCategory())) {
                enrichBirthdayDisplay(todo);
            }
            todo.setNoticeMinutes(selectRules(todo.getId()).stream()
                    .map(TodoNoticeRule::getBeforeMinutes)
                    .collect(Collectors.toList()));
            todo.setNextNotifyLabel(resolveNextNotifyLabel(todo));
            String completeReason = completeDisabledReason(todo);
            todo.setCanComplete(!StringUtils.hasText(completeReason));
            todo.setCompleteDisabledReason(completeReason);
        }
    }

    private String resolveNextNotifyLabel(Todo todo) {
        if (!Boolean.TRUE.equals(todo.getNotifySite())
                && !Boolean.TRUE.equals(todo.getNotifyEmail())
                && !Boolean.TRUE.equals(todo.getNotifyPush())) {
            return null;
        }
        if (todo.getDueTime() == null) {
            return null;
        }
        List<TodoNoticeRule> rules = selectRules(todo.getId());
        if (rules.isEmpty()) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = null;
        for (TodoNoticeRule rule : rules) {
            int beforeMinutes = rule.getBeforeMinutes() == null ? 0 : rule.getBeforeMinutes();
            LocalDateTime targetTime = todo.getDueTime().minusMinutes(beforeMinutes);
            if (hasSent(todo.getId(), rule.getId(), targetTime)) {
                continue;
            }
            if (targetTime.isBefore(now.minusMinutes(NOTIFY_SCAN_WINDOW_MINUTES))) {
                continue;
            }
            if (next == null || targetTime.isBefore(next)) {
                next = targetTime;
            }
        }
        return next == null ? null : formatNextNotifyLabel(next);
    }

    private String formatNextNotifyLabel(LocalDateTime notifyTime) {
        LocalDate today = LocalDate.now();
        LocalTime time = notifyTime.toLocalTime().withSecond(0).withNano(0);
        if (notifyTime.toLocalDate().equals(today)) {
            return "今天" + time;
        }
        if (notifyTime.toLocalDate().equals(today.plusDays(1))) {
            return "明天" + time;
        }
        return notifyTime.toLocalDate() + " " + time;
    }

    private String formatDueLabel(LocalDateTime dueTime) {
        LocalDate today = LocalDate.now();
        if (dueTime.toLocalDate().equals(today)) {
            return "今天" + dueTime.toLocalTime().withSecond(0).withNano(0);
        }
        if (dueTime.toLocalDate().equals(today.plusDays(1))) {
            return "明天" + dueTime.toLocalTime().withSecond(0).withNano(0);
        }
        return dueTime.toString().replace('T', ' ');
    }

    private void enrichBirthdayDisplay(Todo todo) {
        todo.setBirthdayAge(calculateBirthdayAge(todo));
        todo.setBirthdayDisplayText(birthdayDisplayText(todo));
        todo.setDueLabel(todo.getBirthdayDisplayText());
    }

    private String birthdayDisplayText(Todo todo) {
        StringBuilder text = new StringBuilder();
        text.append(StringUtils.hasText(todo.getTitle()) ? todo.getTitle() : "生日");
        Integer age = calculateBirthdayAge(todo);
        if (age != null && age >= 0) {
            text.append("（").append(age).append("岁）");
        }
        if (todo.getDueTime() != null) {
            text.append(" · 公历").append(todo.getDueTime().toLocalDate());
        }
        return text.toString();
    }

    private Integer calculateBirthdayAge(Todo todo) {
        if (todo == null || todo.getBirthdayYear() == null || todo.getDueTime() == null) {
            return null;
        }
        int birthdayYear = todo.getBirthdayYear();
        int currentBirthdayYear;
        if ("LUNAR".equals(todo.getDueCalendar())) {
            try {
                currentBirthdayYear = new ChineseDate(todo.getDueTime().toLocalDate()).getChineseYear();
            } catch (Exception ex) {
                currentBirthdayYear = todo.getDueTime().getYear();
            }
        } else {
            currentBirthdayYear = todo.getDueTime().getYear();
        }
        return Math.max(currentBirthdayYear - birthdayYear, 0);
    }

    private String formatRemain(LocalDateTime dueTime) {
        Duration duration = Duration.between(LocalDateTime.now(), dueTime);
        long minutes = duration.toMinutes();
        if (minutes < 0) {
            return "已到期";
        }
        if (minutes < 60) {
            return "剩余" + Math.max(minutes, 0) + "分钟";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return "剩余" + hours + "小时";
        }
        return "剩余" + (hours / 24) + "天";
    }

    private LocalDate resolveNextBirthdayDueDate(Todo todo) {
        if (todo == null || todo.getDueTime() == null) {
            return null;
        }
        LocalDate afterCurrent = todo.getDueTime().toLocalDate().plusDays(1);
        if ("LUNAR".equals(todo.getDueCalendar())
                && todo.getLunarDueMonth() != null
                && todo.getLunarDueDay() != null) {
            return nextLunarDueDate(todo, afterCurrent);
        }
        return birthdayInYear(todo.getDueTime().toLocalDate(), todo.getDueTime().getYear() + 1);
    }

    private LocalDate nextBirthday(LocalDate birthday) {
        LocalDate today = LocalDate.now();
        LocalDate next = birthdayInYear(birthday, today.getYear());
        if (next.isBefore(today)) {
            next = birthdayInYear(birthday, today.getYear() + 1);
        }
        return next;
    }

    private LocalDate birthdayInYear(LocalDate birthday, int year) {
        try {
            return LocalDate.of(year, birthday.getMonth(), birthday.getDayOfMonth());
        } catch (DateTimeException ex) {
            return LocalDate.of(year, 2, 28);
        }
    }
}
