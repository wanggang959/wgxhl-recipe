package com.wgxhl.recipe.want.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.push.service.UserPushSubscriptionService;
import com.wgxhl.recipe.recipe.entity.Recipe;
import com.wgxhl.recipe.recipe.mapper.RecipeMapper;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.want.dto.WantNotifyDTO;
import com.wgxhl.recipe.want.dto.WantedRecipePageDTO;
import com.wgxhl.recipe.want.entity.UserWantedRecipe;
import com.wgxhl.recipe.want.mapper.UserWantedRecipeMapper;
import com.wgxhl.recipe.want.service.UserWantedRecipeService;
import com.wgxhl.recipe.want.vo.WantNotifyPreviewVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserWantedRecipeServiceImpl extends ServiceImpl<UserWantedRecipeMapper, UserWantedRecipe>
        implements UserWantedRecipeService {

    private static final Logger log = LoggerFactory.getLogger(UserWantedRecipeServiceImpl.class);

    private final RecipeMapper recipeMapper;
    private final UserPushSubscriptionService userPushSubscriptionService;

    public UserWantedRecipeServiceImpl(RecipeMapper recipeMapper,
                                       UserPushSubscriptionService userPushSubscriptionService) {
        this.recipeMapper = recipeMapper;
        this.userPushSubscriptionService = userPushSubscriptionService;
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
        LocalDate today = LocalDate.now();
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
        LocalDate today = LocalDate.now();
        boolean exists = lambdaQuery()
                .eq(UserWantedRecipe::getUserId, userId)
                .eq(UserWantedRecipe::getRecipeId, recipeId)
                .ge(UserWantedRecipe::getPlannedDate, today)
                .exists();
        return ApiResponse.success(exists);
    }

    @Override
    public int purgeExpiredBeforeToday() {
        LocalDate today = LocalDate.now();
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
            return ApiResponse.fail("请选择要通知的家人");
        }

        NotifyTarget target = resolveNotifyTarget(actor.getId(), actor);
        if (target == null) {
            return ApiResponse.fail("还没有可通知的想吃菜单");
        }

        int delivered = userPushSubscriptionService.sendToUserIds(
                dto.getTargetUserIds(),
                "备菜提醒",
                target.body,
                "/#/todo",
                "prepare-" + target.plannedDate
        );
        if (delivered <= 0) {
            return ApiResponse.fail("对方尚未开启手机通知，或未选择有效用户");
        }
        return ApiResponse.success("已发送通知", delivered);
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
        LocalDate today = LocalDate.now();
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
        LocalDate today = LocalDate.now();
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
