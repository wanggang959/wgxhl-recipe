package com.wgxhl.recipe.want.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.AppZoneDates;
import com.wgxhl.recipe.notification.service.NotificationService;
import com.wgxhl.recipe.push.service.UserPushSubscriptionService;
import com.wgxhl.recipe.recipe.entity.Recipe;
import com.wgxhl.recipe.recipe.mapper.RecipeMapper;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import com.wgxhl.recipe.want.dto.WantNotifyDTO;
import com.wgxhl.recipe.want.dto.WantedRecipePageDTO;
import com.wgxhl.recipe.want.entity.UserWantedRecipe;
import com.wgxhl.recipe.want.mapper.UserWantedRecipeMapper;
import com.wgxhl.recipe.want.service.UserWantedRecipeService;
import com.wgxhl.recipe.want.vo.WantNotifyPreviewVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserWantedRecipeServiceImpl extends ServiceImpl<UserWantedRecipeMapper, UserWantedRecipe>
        implements UserWantedRecipeService {

    private static final Logger log = LoggerFactory.getLogger(UserWantedRecipeServiceImpl.class);
    private static final String NOTICE_SITE = "site";
    private static final String NOTICE_EMAIL = "email";
    private static final String NOTICE_PUSH = "push";

    private final RecipeMapper recipeMapper;
    private final AppUserMapper appUserMapper;
    private final NotificationService notificationService;
    private final UserPushSubscriptionService userPushSubscriptionService;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public UserWantedRecipeServiceImpl(RecipeMapper recipeMapper,
                                       AppUserMapper appUserMapper,
                                       NotificationService notificationService,
                                       UserPushSubscriptionService userPushSubscriptionService,
                                       ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.recipeMapper = recipeMapper;
        this.appUserMapper = appUserMapper;
        this.notificationService = notificationService;
        this.userPushSubscriptionService = userPushSubscriptionService;
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    @Override
    public boolean save(UserWantedRecipe entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return super.save(entity);
    }

    @Override
    public boolean updateById(UserWantedRecipe entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }

    @Override
    public ApiResponse<Page<UserWantedRecipe>> page(WantedRecipePageDTO dto) {
        Page<UserWantedRecipe> page = new Page<>(dto.getCurrent(), dto.getSize());
        Page<UserWantedRecipe> result = lambdaQuery()
                .eq(StringUtils.hasText(dto.getUserId()), UserWantedRecipe::getUserId, dto.getUserId())
                .like(StringUtils.hasText(dto.getRecipeName()), UserWantedRecipe::getRecipeName, dto.getRecipeName())
                .eq(dto.getPlannedDate() != null, UserWantedRecipe::getPlannedDate, dto.getPlannedDate())
                .ge(dto.getPlannedDateStart() != null, UserWantedRecipe::getPlannedDate, dto.getPlannedDateStart())
                .orderByAsc(UserWantedRecipe::getPlannedDate)
                .orderByDesc(UserWantedRecipe::getUpdateTime)
                .page(page);
        return ApiResponse.success(result);
    }

    @Override
    public ApiResponse<List<LocalDate>> dateList(String userId) {
        if (!StringUtils.hasText(userId)) {
            return ApiResponse.success(java.util.Collections.emptyList());
        }
        LocalDate today = AppZoneDates.today();
        List<LocalDate> dates = lambdaQuery()
                .eq(UserWantedRecipe::getUserId, userId)
                .ge(UserWantedRecipe::getPlannedDate, today)
                .select(UserWantedRecipe::getPlannedDate)
                .orderByAsc(UserWantedRecipe::getPlannedDate)
                .list()
                .stream()
                .map(UserWantedRecipe::getPlannedDate)
                .filter(date -> date != null)
                .distinct()
                .collect(Collectors.toList());
        return ApiResponse.success(dates);
    }

    @Override
    public ApiResponse<UserWantedRecipe> create(UserWantedRecipe entity) {
        if (!StringUtils.hasText(entity.getUserId())) {
            return ApiResponse.fail("用户id不能为空");
        }
        if (!StringUtils.hasText(entity.getRecipeId())) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        if (entity.getPlannedDate() == null) {
            return ApiResponse.fail("想吃日期不能为空");
        }
        Recipe recipe = recipeMapper.selectById(entity.getRecipeId());
        if (recipe == null) {
            return ApiResponse.fail("菜谱不存在");
        }
        UserWantedRecipe existing = lambdaQuery()
                .eq(UserWantedRecipe::getUserId, entity.getUserId())
                .eq(UserWantedRecipe::getRecipeId, entity.getRecipeId())
                .one();
        if (existing != null) {
            existing.setRecipeName(recipe.getRecipeName());
            existing.setCoverImage(recipe.getCoverImage());
            existing.setPlannedDate(entity.getPlannedDate());
            updateById(existing);
            return ApiResponse.success("已更新想吃日期", existing);
        }
        entity.setRecipeName(recipe.getRecipeName());
        entity.setCoverImage(recipe.getCoverImage());
        save(entity);
        return ApiResponse.success("已添加到想吃", entity);
    }

    @Override
    public ApiResponse<Void> updatePlannedDate(UserWantedRecipe entity) {
        if (!StringUtils.hasText(entity.getId())) {
            return ApiResponse.fail("想吃记录id不能为空");
        }
        if (entity.getPlannedDate() == null) {
            return ApiResponse.fail("想吃日期不能为空");
        }
        UserWantedRecipe existing = super.getById(entity.getId());
        if (existing == null) {
            return ApiResponse.fail("想吃记录不存在");
        }
        existing.setPlannedDate(entity.getPlannedDate());
        updateById(existing);
        return ApiResponse.success("已修改想吃日期", null);
    }

    @Override
    public ApiResponse<Void> delete(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("想吃记录id不能为空");
        }
        if (super.getById(id) == null) {
            return ApiResponse.fail("想吃记录不存在");
        }
        removeById(id);
        return ApiResponse.success("已从想吃移除", null);
    }

    @Override
    public ApiResponse<Void> deleteByRecipeId(String userId, String recipeId) {
        if (!StringUtils.hasText(userId)) {
            return ApiResponse.fail("用户id不能为空");
        }
        if (!StringUtils.hasText(recipeId)) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        boolean removed = remove(new LambdaQueryWrapper<UserWantedRecipe>()
                .eq(UserWantedRecipe::getUserId, userId)
                .eq(UserWantedRecipe::getRecipeId, recipeId));
        if (!removed) {
            return ApiResponse.fail("想吃记录不存在");
        }
        return ApiResponse.success("已从想吃移除", null);
    }

    @Override
    public ApiResponse<Boolean> check(String userId, String recipeId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(recipeId)) {
            return ApiResponse.success(false);
        }
        LocalDate today = AppZoneDates.today();
        boolean exists = lambdaQuery()
                .eq(UserWantedRecipe::getUserId, userId)
                .eq(UserWantedRecipe::getRecipeId, recipeId)
                .ge(UserWantedRecipe::getPlannedDate, today)
                .exists();
        return ApiResponse.success(exists);
    }

    @Override
    public int purgeExpiredBeforeToday() {
        LocalDate today = AppZoneDates.today();
        long count = lambdaQuery()
                .lt(UserWantedRecipe::getPlannedDate, today)
                .count();
        if (count <= 0) {
            return 0;
        }
        remove(new LambdaQueryWrapper<UserWantedRecipe>()
                .lt(UserWantedRecipe::getPlannedDate, today));
        int removed = (int) count;
        log.info("Purged {} expired wanted recipe record(s) before {}", removed, today);
        return removed;
    }

    @Override
    public ApiResponse<WantNotifyPreviewVO> notifyPreview(AppUser actor) {
        if (actor == null || !StringUtils.hasText(actor.getId())) {
            return ApiResponse.fail(401, "请先登录");
        }
        NotifyTarget target = resolveNotifyTarget(actor.getId(), actor);
        if (target == null) {
            return ApiResponse.fail("还没有可通知的想吃菜单");
        }
        WantNotifyPreviewVO vo = new WantNotifyPreviewVO();
        vo.setPlannedDate(target.plannedDate);
        vo.setPreviewBody(target.body);
        return ApiResponse.success(vo);
    }

    @Override
    public ApiResponse<Integer> notifyPrepare(AppUser actor, WantNotifyDTO dto) {
        if (actor == null || !StringUtils.hasText(actor.getId())) {
            return ApiResponse.fail(401, "请先登录");
        }
        if (dto == null || dto.getTargetUserIds() == null || dto.getTargetUserIds().isEmpty()) {
            log.info("Wanted recipe notify skipped, actor={}, reason=no_target_user", actor.getId());
            return ApiResponse.fail("请选择要通知的家人");
        }

        NotifyTarget target = resolveNotifyTarget(actor.getId(), actor);
        if (target == null) {
            log.info("Wanted recipe notify skipped, actor={}, targetUserIds={}, reason=no_wanted_recipe",
                    actor.getId(), dto.getTargetUserIds());
            return ApiResponse.fail("还没有可通知的想吃菜单");
        }

        log.info("Wanted recipe notify sending, actor={}, targetUserIds={}, plannedDate={}",
                actor.getId(), dto.getTargetUserIds(), target.plannedDate);
        int delivered = sendPrepareNotifications(dto.getTargetUserIds(), target);
        if (delivered <= 0) {
            log.info("Wanted recipe notify failed, actor={}, targetUserIds={}, plannedDate={}, delivered=0",
                    actor.getId(), dto.getTargetUserIds(), target.plannedDate);
            return ApiResponse.fail("对方尚未开启手机通知，或未选择有效用户");
        }
        log.info("Wanted recipe notify sent, actor={}, targetUserIds={}, plannedDate={}, delivered={}",
                actor.getId(), dto.getTargetUserIds(), target.plannedDate, delivered);
        return ApiResponse.success("已发送通知", delivered);
    }

    private int sendPrepareNotifications(List<String> targetUserIds, NotifyTarget target) {
        List<AppUser> targets = resolveTargetUsers(targetUserIds);
        if (targets.isEmpty()) {
            log.info("Wanted recipe notify skipped, reason=no_valid_target_user");
            return 0;
        }

        String title = "备菜提醒";
        int sent = 0;
        for (AppUser user : targets) {
            if (allowNotify(user, NOTICE_SITE)) {
                notificationService.createSiteNotice(user.getId(), title, target.body, "WANT", null);
                log.info("Wanted recipe site notice sent, user={}", userLabel(user));
                sent++;
            } else {
                log.info("Wanted recipe site notice skipped, user={}, preference={}, reason=preference",
                        userLabel(user), user.getNotificationPreference());
            }

            if (allowNotify(user, NOTICE_EMAIL)) {
                try {
                    sendEmail(user, "【王师傅家提醒】" + title, target.body);
                    log.info("Wanted recipe email sent, user={}, to={}", userLabel(user), maskEmail(user.getEmail()));
                    sent++;
                } catch (Exception ex) {
                    log.warn("Wanted recipe email failed, user={}, to={}, reason={}",
                            userLabel(user), maskEmail(user.getEmail()), ex.getMessage(), ex);
                }
            } else {
                log.info("Wanted recipe email skipped, user={}, preference={}, reason=preference",
                        userLabel(user), user.getNotificationPreference());
            }
        }

        List<String> pushUserIds = targets.stream()
                .filter(user -> allowNotify(user, NOTICE_PUSH))
                .map(AppUser::getId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        List<String> skippedPushUsers = targets.stream()
                .filter(user -> !allowNotify(user, NOTICE_PUSH))
                .map(this::userLabel)
                .collect(Collectors.toList());
        if (!skippedPushUsers.isEmpty()) {
            log.info("Wanted recipe push preference skipped, users={}", skippedPushUsers);
        }
        if (!pushUserIds.isEmpty()) {
            int delivered = userPushSubscriptionService.sendToUserIds(
                    pushUserIds,
                    title,
                    target.body,
                    "/#/todo",
                    "prepare-" + target.plannedDate
            );
            log.info("Wanted recipe push sent, targetUserIds={}, delivered={}", pushUserIds, delivered);
            sent += delivered;
        } else {
            log.info("Wanted recipe push skipped, reason=no_allowed_user");
        }
        return sent;
    }

    private List<AppUser> resolveTargetUsers(List<String> targetUserIds) {
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return targetUserIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .map(appUserMapper::selectById)
                .filter(user -> user != null && "normal".equals(user.getStatus()))
                .collect(Collectors.toList());
    }

    private void sendEmail(AppUser user, String title, String content) {
        if (mailSender == null || !StringUtils.hasText(mailUsername)) {
            throw new IllegalStateException("邮箱服务未配置");
        }
        if (user == null || !StringUtils.hasText(user.getEmail())) {
            throw new IllegalStateException("用户邮箱未配置");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(user.getEmail());
        message.setSubject(title);
        message.setText(content);
        mailSender.send(message);
    }

    private boolean allowNotify(AppUser user, String type) {
        if (user == null || !StringUtils.hasText(user.getNotificationPreference())) {
            return true;
        }
        Set<String> prefs = Arrays.stream(user.getNotificationPreference().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        return prefs.contains(type);
    }

    private String userLabel(AppUser user) {
        if (user == null) {
            return "none";
        }
        String name = StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
        return StringUtils.hasText(name) ? user.getId() + "(" + name + ")" : user.getId();
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

    @Override
    public void deleteByRecipeId(String recipeId) {
        if (!StringUtils.hasText(recipeId)) {
            return;
        }
        remove(new LambdaQueryWrapper<UserWantedRecipe>().eq(UserWantedRecipe::getRecipeId, recipeId));
    }

    @Override
    public void deleteByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        remove(new LambdaQueryWrapper<UserWantedRecipe>().eq(UserWantedRecipe::getUserId, userId));
    }

    private String displayName(AppUser actor) {
        if (actor == null) {
            return "家人";
        }
        if (StringUtils.hasText(actor.getNickname())) {
            return actor.getNickname();
        }
        if (StringUtils.hasText(actor.getUsername())) {
            return actor.getUsername();
        }
        return "家人";
    }

    private NotifyTarget resolveNotifyTarget(String userId, AppUser actor) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        LocalDate today = AppZoneDates.today();
        List<UserWantedRecipe> futureList = lambdaQuery()
                .eq(UserWantedRecipe::getUserId, userId)
                .ge(UserWantedRecipe::getPlannedDate, today)
                .orderByAsc(UserWantedRecipe::getPlannedDate)
                .orderByAsc(UserWantedRecipe::getRecipeName)
                .list();
        if (futureList.isEmpty()) {
            return null;
        }
        LocalDate plannedDate = futureList.get(0).getPlannedDate();
        List<UserWantedRecipe> wantedList = futureList.stream()
                .filter(item -> plannedDate.equals(item.getPlannedDate()))
                .collect(Collectors.toList());
        String body = buildNotifyBody(actor, plannedDate, wantedList);
        return new NotifyTarget(plannedDate, body);
    }

    private String buildNotifyBody(AppUser actor, LocalDate plannedDate, List<UserWantedRecipe> wantedList) {
        String recipeNames = joinRecipeNames(wantedList);
        return displayName(actor) + dayLabel(plannedDate) + "想吃" + recipeNames + "，点击前往备菜";
    }

    private String dayLabel(LocalDate plannedDate) {
        LocalDate today = AppZoneDates.today();
        if (plannedDate.equals(today)) {
            return "今天";
        }
        if (plannedDate.equals(today.plusDays(1))) {
            return "明天";
        }
        if (plannedDate.equals(today.plusDays(2))) {
            return "后天";
        }
        return plannedDate.getMonthValue() + "月" + plannedDate.getDayOfMonth() + "日";
    }

    private static final class NotifyTarget {
        private final LocalDate plannedDate;
        private final String body;

        private NotifyTarget(LocalDate plannedDate, String body) {
            this.plannedDate = plannedDate;
            this.body = body;
        }
    }

    private String joinRecipeNames(List<UserWantedRecipe> items) {
        List<String> names = items.stream()
                .map(UserWantedRecipe::getRecipeName)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (names.isEmpty()) {
            return "几道菜";
        }
        if (names.size() == 1) {
            return names.get(0);
        }
        if (names.size() == 2) {
            return names.get(0) + "和" + names.get(1);
        }
        String last = names.get(names.size() - 1);
        return String.join("、", names.subList(0, names.size() - 1)) + "和" + last;
    }
}
