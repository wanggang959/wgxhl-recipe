package com.wgxhl.recipe.household.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.JwtAuthUtil;
import com.wgxhl.recipe.favorite.entity.UserFavorite;
import com.wgxhl.recipe.favorite.mapper.UserFavoriteMapper;
import com.wgxhl.recipe.household.entity.Household;
import com.wgxhl.recipe.household.entity.HouseholdInvite;
import com.wgxhl.recipe.household.entity.HouseholdMember;
import com.wgxhl.recipe.household.service.HouseholdInviteService;
import com.wgxhl.recipe.household.service.HouseholdLeaveService;
import com.wgxhl.recipe.household.service.HouseholdMemberService;
import com.wgxhl.recipe.household.service.HouseholdService;
import com.wgxhl.recipe.notification.entity.Notification;
import com.wgxhl.recipe.notification.mapper.NotificationMapper;
import com.wgxhl.recipe.push.entity.UserPushSubscription;
import com.wgxhl.recipe.push.mapper.UserPushSubscriptionMapper;
import com.wgxhl.recipe.record.entity.RecipeViewRecord;
import com.wgxhl.recipe.record.mapper.RecipeViewRecordMapper;
import com.wgxhl.recipe.todo.entity.Todo;
import com.wgxhl.recipe.todo.entity.TodoOwner;
import com.wgxhl.recipe.todo.mapper.TodoMapper;
import com.wgxhl.recipe.todo.mapper.TodoNoticeRuleMapper;
import com.wgxhl.recipe.todo.mapper.TodoOwnerMapper;
import com.wgxhl.recipe.todo.mapper.TodoSendLogMapper;
import com.wgxhl.recipe.expense.entity.SpecialExpenseItem;
import com.wgxhl.recipe.expense.entity.SpecialExpenseProject;
import com.wgxhl.recipe.expense.mapper.SpecialExpenseItemMapper;
import com.wgxhl.recipe.expense.mapper.SpecialExpenseProjectMapper;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.service.AppUserService;
import com.wgxhl.recipe.want.entity.UserWantedRecipe;
import com.wgxhl.recipe.want.mapper.UserWantedRecipeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class HouseholdLeaveServiceImpl implements HouseholdLeaveService {

    private static final String RELATED_BIRTHDAY = "MEMBER_BIRTHDAY";
    private static final String RELATED_EXPENSE_ITEM = "SPECIAL_EXPENSE_ITEM";

    private final HouseholdService householdService;
    private final HouseholdMemberService householdMemberService;
    private final HouseholdInviteService householdInviteService;
    private final TodoMapper todoMapper;
    private final TodoOwnerMapper todoOwnerMapper;
    private final TodoNoticeRuleMapper todoNoticeRuleMapper;
    private final TodoSendLogMapper todoSendLogMapper;
    private final NotificationMapper notificationMapper;
    private final UserFavoriteMapper userFavoriteMapper;
    private final UserWantedRecipeMapper userWantedRecipeMapper;
    private final RecipeViewRecordMapper recipeViewRecordMapper;
    private final UserPushSubscriptionMapper userPushSubscriptionMapper;
    private final SpecialExpenseItemMapper specialExpenseItemMapper;
    private final SpecialExpenseProjectMapper specialExpenseProjectMapper;
    private final JwtAuthUtil jwtAuthUtil;
    private final AppUserService appUserService;

    public HouseholdLeaveServiceImpl(HouseholdService householdService,
                                     HouseholdMemberService householdMemberService,
                                     HouseholdInviteService householdInviteService,
                                     TodoMapper todoMapper,
                                     TodoOwnerMapper todoOwnerMapper,
                                     TodoNoticeRuleMapper todoNoticeRuleMapper,
                                     TodoSendLogMapper todoSendLogMapper,
                                     NotificationMapper notificationMapper,
                                     UserFavoriteMapper userFavoriteMapper,
                                     UserWantedRecipeMapper userWantedRecipeMapper,
                                     RecipeViewRecordMapper recipeViewRecordMapper,
                                     UserPushSubscriptionMapper userPushSubscriptionMapper,
                                     SpecialExpenseItemMapper specialExpenseItemMapper,
                                     SpecialExpenseProjectMapper specialExpenseProjectMapper,
                                     JwtAuthUtil jwtAuthUtil,
                                     AppUserService appUserService) {
        this.householdService = householdService;
        this.householdMemberService = householdMemberService;
        this.householdInviteService = householdInviteService;
        this.todoMapper = todoMapper;
        this.todoOwnerMapper = todoOwnerMapper;
        this.todoNoticeRuleMapper = todoNoticeRuleMapper;
        this.todoSendLogMapper = todoSendLogMapper;
        this.notificationMapper = notificationMapper;
        this.userFavoriteMapper = userFavoriteMapper;
        this.userWantedRecipeMapper = userWantedRecipeMapper;
        this.recipeViewRecordMapper = recipeViewRecordMapper;
        this.userPushSubscriptionMapper = userPushSubscriptionMapper;
        this.specialExpenseItemMapper = specialExpenseItemMapper;
        this.specialExpenseProjectMapper = specialExpenseProjectMapper;
        this.jwtAuthUtil = jwtAuthUtil;
        this.appUserService = appUserService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<AppUser> leave(AppUser user, Household household, HouseholdMember membership) {
        if (user == null || household == null || membership == null) {
            return ApiResponse.fail(401, "请先登录");
        }
        if (isGuest(user)) {
            return ApiResponse.fail(403, "游客不能退出家庭");
        }
        if ("owner".equals(membership.getMemberRole())) {
            return ApiResponse.fail("家庭所有者不能直接退出，请先转让所有权或解散家庭");
        }
        cleanupMemberData(household.getId(), user.getId());
        householdMemberService.removeById(membership.getId());
        return switchToNextHousehold(user, household.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<AppUser> transfer(AppUser user, Household household, HouseholdMember membership,
                                         String targetUserId) {
        if (user == null || household == null || membership == null) {
            return ApiResponse.fail(401, "请先登录");
        }
        if (!"owner".equals(membership.getMemberRole())) {
            return ApiResponse.fail(403, "只有家庭所有者可以转让所有权");
        }
        if (!StringUtils.hasText(targetUserId) || targetUserId.equals(user.getId())) {
            return ApiResponse.fail("请选择其他家庭成员");
        }
        HouseholdMember target = householdMemberService.find(household.getId(), targetUserId);
        if (target == null || !"normal".equals(target.getStatus())) {
            return ApiResponse.fail("目标成员不属于当前家庭");
        }
        Household patch = new Household();
        patch.setId(household.getId());
        patch.setOwnerUserId(targetUserId);
        householdService.updateById(patch);

        target.setMemberRole("owner");
        householdMemberService.updateById(target);
        membership.setMemberRole("admin");
        householdMemberService.updateById(membership);

        return ApiResponse.success("所有权已转让", applyHousehold(user, household, membership));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<AppUser> dissolve(AppUser user, Household household, HouseholdMember membership) {
        if (user == null || household == null || membership == null) {
            return ApiResponse.fail(401, "请先登录");
        }
        if (!"owner".equals(membership.getMemberRole())) {
            return ApiResponse.fail(403, "只有家庭所有者可以解散家庭");
        }
        List<HouseholdMember> members = householdMemberService.listByHousehold(household.getId());
        if (members.size() > 1) {
            return ApiResponse.fail("家庭还有其他成员，请先让他们退出或转让所有权");
        }
        deleteHouseholdData(household.getId());
        householdMemberService.removeById(membership.getId());
        householdService.removeById(household.getId());
        ApiResponse<AppUser> response = switchToNextHousehold(user, household.getId());
        response.setMessage("家庭已解散");
        return response;
    }

    private void cleanupMemberData(String householdId, String userId) {
        Set<String> todoIds = new HashSet<>();
        todoOwnerMapper.selectList(new LambdaQueryWrapper<TodoOwner>()
                        .eq(TodoOwner::getOwnerId, userId))
                .forEach(owner -> todoIds.add(owner.getTodoId()));
        todoMapper.selectList(new LambdaQueryWrapper<Todo>()
                        .eq(Todo::getOwnerId, userId))
                .forEach(todo -> todoIds.add(todo.getId()));

        for (String todoId : todoIds) {
            if (!StringUtils.hasText(todoId)) {
                continue;
            }
            Todo todo = todoMapper.selectById(todoId);
            if (todo == null) {
                continue;
            }
            boolean birthdayTodo = RELATED_BIRTHDAY.equals(todo.getRelatedType())
                    && userId.equals(todo.getRelatedId());
            List<TodoOwner> owners = todoOwnerMapper.selectList(new LambdaQueryWrapper<TodoOwner>()
                    .eq(TodoOwner::getTodoId, todoId));
            long otherOwners = owners.stream()
                    .filter(owner -> !userId.equals(owner.getOwnerId()))
                    .count();
            if (birthdayTodo || otherOwners == 0) {
                deleteTodoCompletely(todo);
                continue;
            }
            todoOwnerMapper.delete(new LambdaQueryWrapper<TodoOwner>()
                    .eq(TodoOwner::getTodoId, todoId)
                    .eq(TodoOwner::getOwnerId, userId));
            if (userId.equals(todo.getOwnerId())) {
                String nextOwnerId = owners.stream()
                        .map(TodoOwner::getOwnerId)
                        .filter(id -> StringUtils.hasText(id) && !userId.equals(id))
                        .findFirst()
                        .orElse(null);
                Todo patch = new Todo();
                patch.setId(todoId);
                patch.setOwnerId(nextOwnerId);
                todoMapper.updateById(patch);
            }
        }
        todoOwnerMapper.delete(new LambdaQueryWrapper<TodoOwner>().eq(TodoOwner::getOwnerId, userId));

        notificationMapper.delete(new LambdaQueryWrapper<Notification>().eq(Notification::getUserId, userId));
        userFavoriteMapper.delete(new LambdaQueryWrapper<UserFavorite>().eq(UserFavorite::getUserId, userId));
        userWantedRecipeMapper.delete(new LambdaQueryWrapper<UserWantedRecipe>()
                .eq(UserWantedRecipe::getUserId, userId));
        recipeViewRecordMapper.delete(new LambdaQueryWrapper<RecipeViewRecord>()
                .eq(RecipeViewRecord::getUserId, userId));
        userPushSubscriptionMapper.delete(new LambdaQueryWrapper<UserPushSubscription>()
                .eq(UserPushSubscription::getUserId, userId));

        specialExpenseItemMapper.update(null, new LambdaUpdateWrapper<SpecialExpenseItem>()
                .eq(SpecialExpenseItem::getOwnerId, userId)
                .set(SpecialExpenseItem::getOwnerId, null));
        specialExpenseItemMapper.update(null, new LambdaUpdateWrapper<SpecialExpenseItem>()
                .eq(SpecialExpenseItem::getPayerId, userId)
                .set(SpecialExpenseItem::getPayerId, null));
    }

    private void deleteTodoCompletely(Todo todo) {
        if (todo == null || !StringUtils.hasText(todo.getId())) {
            return;
        }
        if (RELATED_EXPENSE_ITEM.equals(todo.getRelatedType())) {
            specialExpenseItemMapper.update(null, new LambdaUpdateWrapper<SpecialExpenseItem>()
                    .eq(SpecialExpenseItem::getTodoId, todo.getId())
                    .set(SpecialExpenseItem::getTodoId, null));
        }
        todoNoticeRuleMapper.delete(new LambdaQueryWrapper<com.wgxhl.recipe.todo.entity.TodoNoticeRule>()
                .eq(com.wgxhl.recipe.todo.entity.TodoNoticeRule::getTodoId, todo.getId()));
        todoSendLogMapper.delete(new LambdaQueryWrapper<com.wgxhl.recipe.todo.entity.TodoSendLog>()
                .eq(com.wgxhl.recipe.todo.entity.TodoSendLog::getTodoId, todo.getId()));
        todoOwnerMapper.delete(new LambdaQueryWrapper<TodoOwner>().eq(TodoOwner::getTodoId, todo.getId()));
        notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getRelatedId, todo.getId())
                .eq(Notification::getNotificationType, "TODO"));
        todoMapper.deleteById(todo.getId());
    }

    private void deleteHouseholdData(String householdId) {
        List<Todo> todos = todoMapper.selectList(new LambdaQueryWrapper<>());
        for (Todo todo : todos) {
            if (RELATED_EXPENSE_ITEM.equals(todo.getRelatedType())) {
                specialExpenseItemMapper.update(null, new LambdaUpdateWrapper<SpecialExpenseItem>()
                        .eq(SpecialExpenseItem::getTodoId, todo.getId())
                        .set(SpecialExpenseItem::getTodoId, null));
            }
        }
        todoNoticeRuleMapper.delete(new LambdaQueryWrapper<>());
        todoSendLogMapper.delete(new LambdaQueryWrapper<>());
        todoOwnerMapper.delete(new LambdaQueryWrapper<>());
        todoMapper.delete(new LambdaQueryWrapper<>());

        notificationMapper.delete(new LambdaQueryWrapper<>());
        userFavoriteMapper.delete(new LambdaQueryWrapper<>());
        userWantedRecipeMapper.delete(new LambdaQueryWrapper<>());
        recipeViewRecordMapper.delete(new LambdaQueryWrapper<>());
        userPushSubscriptionMapper.delete(new LambdaQueryWrapper<>());
        specialExpenseItemMapper.delete(new LambdaQueryWrapper<>());
        specialExpenseProjectMapper.delete(new LambdaQueryWrapper<>());

        householdInviteService.remove(new LambdaQueryWrapper<HouseholdInvite>()
                .eq(HouseholdInvite::getHouseholdId, householdId));
    }

    private ApiResponse<AppUser> switchToNextHousehold(AppUser user, String leftHouseholdId) {
        HouseholdMember next = householdMemberService.findFirstByUser(user.getId());
        if (next != null) {
            Household household = householdService.getById(next.getHouseholdId());
            if (household != null && "normal".equals(household.getStatus())) {
                return ApiResponse.success("已退出家庭", applyHousehold(user, household, next));
            }
        }
        return createPersonalHousehold(user, "已退出家庭");
    }

    private ApiResponse<AppUser> createPersonalHousehold(AppUser user, String message) {
        String baseName = StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
        String name = baseName + "的家";
        if (householdService.lambdaQuery().eq(Household::getHouseholdName, name).exists()) {
            name = name + "-" + user.getId().substring(0, Math.min(4, user.getId().length()));
        }
        Household household = new Household();
        household.setHouseholdName(name);
        household.setOwnerUserId(user.getId());
        householdService.save(household);

        HouseholdMember membership = new HouseholdMember();
        membership.setHouseholdId(household.getId());
        membership.setUserId(user.getId());
        membership.setMemberRole("owner");
        householdMemberService.save(membership);

        return ApiResponse.success(message, applyHousehold(user, household, membership));
    }

    private AppUser applyHousehold(AppUser user, Household household, HouseholdMember membership) {
        appUserService.rememberHousehold(user.getId(), household.getId());
        user.setActiveHouseholdId(household.getId());
        user.setHouseholdId(household.getId());
        user.setHouseholdName(household.getHouseholdName());
        user.setMemberRole(membership.getMemberRole());
        user.setToken(jwtAuthUtil.createToken(user, household.getId()));
        user.setPassword(null);
        return user;
    }

    private boolean isGuest(AppUser user) {
        return user != null && ("guest".equals(user.getId()) || "guest".equals(user.getUsername()));
    }
}
