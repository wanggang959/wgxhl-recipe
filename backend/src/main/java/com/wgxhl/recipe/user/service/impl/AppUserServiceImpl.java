package com.wgxhl.recipe.user.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.date.ChineseDate;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.JwtAuthUtil;
import com.wgxhl.recipe.config.TenantContext;
import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.favorite.service.UserFavoriteService;
import com.wgxhl.recipe.household.entity.Household;
import com.wgxhl.recipe.household.entity.HouseholdMember;
import com.wgxhl.recipe.household.service.HouseholdMemberService;
import com.wgxhl.recipe.household.service.HouseholdService;
import com.wgxhl.recipe.push.service.UserPushSubscriptionService;
import com.wgxhl.recipe.record.service.RecipeViewRecordService;
import com.wgxhl.recipe.todo.entity.Todo;
import com.wgxhl.recipe.todo.entity.TodoOwner;
import com.wgxhl.recipe.todo.mapper.TodoOwnerMapper;
import com.wgxhl.recipe.todo.service.TodoService;
import com.wgxhl.recipe.user.dto.UserLoginDTO;
import com.wgxhl.recipe.user.dto.UserPageDTO;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.vo.UserPreviewVO;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import com.wgxhl.recipe.user.service.AppUserService;
import com.wgxhl.recipe.user.service.LoginAttemptService;
import com.wgxhl.recipe.user.util.DefaultAvatars;
import com.wgxhl.recipe.user.util.EmailAddress;
import com.wgxhl.recipe.user.util.PasswordUtil;
import com.wgxhl.recipe.want.service.UserWantedRecipeService;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AppUserServiceImpl extends ServiceImpl<AppUserMapper, AppUser>
        implements AppUserService {

    private static final String ADMIN_ID = "admin-wangshifu";
    private static final String GUEST_ID = "guest";
    private static final String GUEST_USERNAME = "guest";
    private static final String ADMIN_USERNAME = "王师傅";
    private static final String ADMIN_PASSWORD = "123456";
    private static final String ROLE_SUPER_ADMIN = "super_admin";
    private static final String ROLE_USER = "user";
    private static final String MEMBER_OWNER = UserContext.MEMBER_OWNER;
    private static final String MEMBER_ADMIN = UserContext.MEMBER_ADMIN;
    private static final String MEMBER_MEMBER = UserContext.MEMBER_MEMBER;
    private static final String STATUS_NORMAL = "normal";
    private static final String STATUS_DISABLED = "disabled";

    private final UserFavoriteService userFavoriteService;
    private final RecipeViewRecordService recipeViewRecordService;
    private final UserWantedRecipeService userWantedRecipeService;
    private final UserPushSubscriptionService userPushSubscriptionService;
    private final TodoService todoService;
    private final TodoOwnerMapper todoOwnerMapper;
    private final JwtAuthUtil jwtAuthUtil;
    private final HouseholdMemberService householdMemberService;
    private final HouseholdService householdService;
    private final LoginAttemptService loginAttemptService;

    public AppUserServiceImpl(UserFavoriteService userFavoriteService,
                              RecipeViewRecordService recipeViewRecordService,
                              UserWantedRecipeService userWantedRecipeService,
                              UserPushSubscriptionService userPushSubscriptionService,
                              TodoService todoService,
                              TodoOwnerMapper todoOwnerMapper,
                              JwtAuthUtil jwtAuthUtil,
                              HouseholdMemberService householdMemberService,
                              HouseholdService householdService,
                              LoginAttemptService loginAttemptService) {
        this.userFavoriteService = userFavoriteService;
        this.recipeViewRecordService = recipeViewRecordService;
        this.userWantedRecipeService = userWantedRecipeService;
        this.userPushSubscriptionService = userPushSubscriptionService;
        this.todoService = todoService;
        this.todoOwnerMapper = todoOwnerMapper;
        this.jwtAuthUtil = jwtAuthUtil;
        this.householdMemberService = householdMemberService;
        this.householdService = householdService;
        this.loginAttemptService = loginAttemptService;
    }

    @PostConstruct
    public void ensureBuiltinAccounts() {
        ensureAdminAccount();
        ensureGuestAccount();
        ensureBuiltinMemberships();
    }

    private void ensureBuiltinMemberships() {
        AppUser admin = lambdaQuery()
                .eq(AppUser::getUsername, ADMIN_USERNAME)
                .one();
        if (admin == null) {
            return;
        }
        Household household = householdService.lambdaQuery()
                .eq(Household::getOwnerUserId, admin.getId())
                .eq(Household::getStatus, STATUS_NORMAL)
                .orderByAsc(Household::getCreateTime)
                .last("limit 1")
                .one();
        if (household == null) {
            return;
        }
        ensureMembership(household.getId(), admin.getId(), "owner");
        ensureMembership(household.getId(), GUEST_ID, "guest");
    }

    private void ensureMembership(String householdId, String userId, String memberRole) {
        HouseholdMember existing = householdMemberService.find(householdId, userId);
        if (existing == null) {
            HouseholdMember member = new HouseholdMember();
            member.setHouseholdId(householdId);
            member.setUserId(userId);
            member.setMemberRole(memberRole);
            householdMemberService.save(member);
            return;
        }
        if (!memberRole.equals(existing.getMemberRole())) {
            HouseholdMember patch = new HouseholdMember();
            patch.setId(existing.getId());
            patch.setMemberRole(memberRole);
            householdMemberService.updateById(patch);
        }
    }

    private void ensureAdminAccount() {
        AppUser admin = super.getById(ADMIN_ID);
        if (admin == null) {
            admin = lambdaQuery()
                    .eq(AppUser::getUsername, ADMIN_USERNAME)
                    .one();
        }
        if (admin == null) {
            admin = new AppUser();
            admin.setId(ADMIN_ID);
            admin.setUsername(ADMIN_USERNAME);
            admin.setNickname(ADMIN_USERNAME);
            admin.setPassword(PasswordUtil.hash(ADMIN_PASSWORD));
            admin.setUserRole(ROLE_SUPER_ADMIN);
            admin.setStatus(STATUS_NORMAL);
            admin.setAvatar(DefaultAvatars.ADMIN_AVATAR);
            admin.setNotificationPreference("site,email");
            save(admin);
            return;
        }
        boolean changed = false;
        if (!ADMIN_USERNAME.equals(admin.getUsername())) {
            admin.setUsername(ADMIN_USERNAME);
            changed = true;
        }
        // 只在历史明文密码时升级为哈希，保留用户自己改过的密码，不回退成默认密码
        if (StringUtils.hasText(admin.getPassword()) && !PasswordUtil.isHashed(admin.getPassword())) {
            admin.setPassword(PasswordUtil.hash(admin.getPassword()));
            changed = true;
        }
        if (!ROLE_SUPER_ADMIN.equals(admin.getUserRole())) {
            admin.setUserRole(ROLE_SUPER_ADMIN);
            changed = true;
        }
        if (!STATUS_NORMAL.equals(admin.getStatus())) {
            admin.setStatus(STATUS_NORMAL);
            changed = true;
        }
        if (!StringUtils.hasText(admin.getNickname())) {
            admin.setNickname(ADMIN_USERNAME);
            changed = true;
        }
        if (!StringUtils.hasText(admin.getAvatar())) {
            admin.setAvatar(DefaultAvatars.ADMIN_AVATAR);
            changed = true;
        }
        if (!StringUtils.hasText(admin.getNotificationPreference())) {
            admin.setNotificationPreference("site,email");
            changed = true;
        }
        if (changed) {
            updateById(admin);
        }
    }

    private void ensureGuestAccount() {
        AppUser guest = super.getById(GUEST_ID);
        if (guest == null) {
            guest = lambdaQuery()
                    .eq(AppUser::getUsername, GUEST_USERNAME)
                    .one();
        }
        if (guest == null) {
            guest = new AppUser();
            guest.setId(GUEST_ID);
            guest.setUsername(GUEST_USERNAME);
            guest.setNickname("游客");
            guest.setPassword(null);
            guest.setUserRole(ROLE_USER);
            guest.setStatus(STATUS_NORMAL);
            guest.setAvatar(DefaultAvatars.GUEST_AVATAR);
            guest.setNotificationPreference("site");
            save(guest);
            return;
        }
        boolean changed = false;
        if (!GUEST_USERNAME.equals(guest.getUsername())) {
            guest.setUsername(GUEST_USERNAME);
            changed = true;
        }
        if (!ROLE_USER.equals(guest.getUserRole())) {
            guest.setUserRole(ROLE_USER);
            changed = true;
        }
        if (!StringUtils.hasText(guest.getNickname())) {
            guest.setNickname("游客");
            changed = true;
        }
        if (!StringUtils.hasText(guest.getAvatar())) {
            guest.setAvatar(DefaultAvatars.GUEST_AVATAR);
            changed = true;
        }
        if (!StringUtils.hasText(guest.getNotificationPreference())) {
            guest.setNotificationPreference("site");
            changed = true;
        }
        if (changed) {
            updateById(guest);
        }
    }

    @Override
    public boolean save(AppUser entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return super.save(entity);
    }

    @Override
    public boolean updateById(AppUser entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }

    @Override
    public ApiResponse<Page<AppUser>> page(UserPageDTO dto) {
        Page<AppUser> page = new Page<>(dto.getCurrent(), dto.getSize());
        String householdId = TenantContext.require();
        List<String> memberIds = householdMemberService.listUserIds(householdId);
        if (memberIds.isEmpty()) {
            return ApiResponse.success(page);
        }
        Page<AppUser> result = lambdaQuery()
                .in(AppUser::getId, memberIds)
                .like(StringUtils.hasText(dto.getUsername()), AppUser::getUsername, dto.getUsername())
                .like(StringUtils.hasText(dto.getNickname()), AppUser::getNickname, dto.getNickname())
                .eq(StringUtils.hasText(dto.getUserRole()), AppUser::getUserRole, dto.getUserRole())
                .eq(StringUtils.hasText(dto.getStatus()), AppUser::getStatus, dto.getStatus())
                .orderByDesc(AppUser::getCreateTime)
                .orderByDesc(AppUser::getId)
                .page(page);
        if (UserContext.isGuest()) {
            result.setRecords(result.getRecords().stream().map(user -> {
                AppUser profile = new AppUser();
                profile.setId(user.getId());
                profile.setNickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
                profile.setAvatar(DefaultAvatars.normalizeOrPick(user.getAvatar(), user.getId()));
                return profile;
            }).collect(Collectors.toList()));
            return ApiResponse.success(result);
        }
        Map<String, String> roleByUser = householdMemberService.listByHousehold(householdId).stream()
                .filter(member -> StringUtils.hasText(member.getUserId()))
                .collect(Collectors.toMap(HouseholdMember::getUserId,
                        member -> StringUtils.hasText(member.getMemberRole()) ? member.getMemberRole() : MEMBER_MEMBER,
                        (first, second) -> first));
        result.getRecords().forEach(user -> {
            user.setRecentTodoCount(countRecentTodo(user.getId()));
            user.setMemberRole(roleByUser.get(user.getId()));
            maskPassword(user);
        });
        result.getRecords().sort(Comparator.comparingInt(this::memberRoleRank));
        return ApiResponse.success(result);
    }

    private int memberRoleRank(AppUser user) {
        String role = user.getMemberRole();
        if (MEMBER_OWNER.equals(role)) {
            return 0;
        }
        if (MEMBER_ADMIN.equals(role)) {
            return 1;
        }
        if (MEMBER_MEMBER.equals(role)) {
            return 2;
        }
        return 3;
    }

    @Override
    public ApiResponse<AppUser> getById(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("用户id不能为空");
        }
        if (UserContext.isGuest()) return ApiResponse.fail(403, "游客不能查看账号资料");
        if (!UserContext.requireUserId().equals(id)) {
            if (!UserContext.isHouseholdManager()) return ApiResponse.fail(403, "无权查看此账号资料");
            HouseholdMember member = householdMemberService.find(TenantContext.require(), id);
            if (member == null || !STATUS_NORMAL.equals(member.getStatus())) {
                return ApiResponse.fail(403, "无权查看此账号资料");
            }
        }
        AppUser user = super.getById(id);
        if (user == null) {
            return ApiResponse.fail("用户不存在");
        }
        maskPassword(user);
        user.setAuthVersion(null);
        user.setAccountOrigin(null);
        user.setCreatedByUserId(null);
        user.setCreatedHouseholdId(null);
        user.setActiveHouseholdId(null);
        return ApiResponse.success(user);
    }

    @Override
    public ApiResponse<AppUser> create(AppUser entity) {
        if (!UserContext.isHouseholdManager()) {
            return ApiResponse.fail(403, "只有家庭管理员可以添加家人");
        }
        if (!StringUtils.hasText(entity.getUsername())) {
            return ApiResponse.fail("用户名不能为空");
        }
        if (!StringUtils.hasText(entity.getPassword())) {
            return ApiResponse.fail("密码不能为空");
        }
        if (entity.getPassword().length() < 8 || entity.getPassword().length() > 64) {
            return ApiResponse.fail("密码长度需为 8-64 位");
        }
        entity.setAccountOrigin("HOUSEHOLD");
        entity.setCreatedByUserId(UserContext.requireUserId());
        entity.setCreatedHouseholdId(TenantContext.require());
        entity.setEmailVerified(false);
        entity.setAuthVersion(0L);
        if (lambdaQuery().eq(AppUser::getUsername, entity.getUsername()).exists()) {
            return ApiResponse.fail("用户名已存在");
        }
        if (entity.getEmail() != null) {
            entity.setEmail(entity.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
            if (!entity.getEmail().isEmpty() && !EmailAddress.isValid(entity.getEmail())) {
                return ApiResponse.fail("请输入有效的邮箱地址");
            }
            if (!entity.getEmail().isEmpty() && baseMapper.countEmailUsedByAnotherUser(entity.getEmail(), null) > 0) {
                return ApiResponse.fail("该邮箱已被其他账号使用");
            }
        }
        if (StringUtils.hasText(entity.getAvatar()) && !DefaultAvatars.isAllowed(entity.getAvatar())) {
            return ApiResponse.fail("头像地址无效，请从默认头像中选择");
        }
        String memberRole = normalizeMemberRole(entity.getMemberRole());
        if (memberRole == null) {
            return ApiResponse.fail("家庭角色无效");
        }
        if (MEMBER_ADMIN.equals(memberRole) && !UserContext.isHouseholdOwner()) {
            return ApiResponse.fail(403, "只有家庭所有者可以设置管理员");
        }
        entity.setUserRole(ROLE_USER);
        normalizeUser(entity);
        if (StringUtils.hasText(entity.getPassword())) {
            entity.setPassword(PasswordUtil.hash(entity.getPassword()));
        }
        try {
            save(entity);
        } catch (DuplicateKeyException ex) {
            return ApiResponse.fail("用户名或邮箱已被使用");
        }
        HouseholdMember member = new HouseholdMember();
        member.setHouseholdId(TenantContext.require());
        member.setUserId(entity.getId());
        member.setMemberRole(memberRole);
        householdMemberService.save(member);
        todoService.syncBirthdayTodo(entity);
        entity.setMemberRole(memberRole);
        maskPassword(entity);
        return ApiResponse.success("保存成功", entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> update(AppUser entity) {
        if (!StringUtils.hasText(entity.getId())) {
            return ApiResponse.fail("用户id不能为空");
        }
        AppUser existing = super.getById(entity.getId());
        if (existing == null) {
            return ApiResponse.fail("用户不存在");
        }
        HouseholdMember membership = householdMemberService.find(TenantContext.require(), entity.getId());
        if (membership == null) {
            return ApiResponse.fail("该成员不属于当前家庭");
        }
        boolean self = UserContext.requireUserId().equals(entity.getId());
        boolean manager = UserContext.isHouseholdManager();
        if (!self && !manager) {
            return ApiResponse.fail(403, "只有家庭管理员可以修改其他家人的资料");
        }
        if (StringUtils.hasText(entity.getPassword())) {
            return ApiResponse.fail(403, "请通过修改密码或安全重置入口操作");
        }
        entity.setPassword(null);
        if (entity.getEmail() != null && !normalizeAccountEmail(entity.getEmail()).equals(normalizeAccountEmail(existing.getEmail()))) {
            return ApiResponse.fail(403, "更换邮箱需要本人验证，请使用邮箱安全入口");
        }
        entity.setEmail(null);
        entity.setEmailVerified(null);
        entity.setAuthVersion(null);
        entity.setAccountOrigin(null);
        entity.setCreatedByUserId(null);
        entity.setCreatedHouseholdId(null);
        if (!UserContext.isSuperAdmin()) {
            entity.setUserRole(existing.getUserRole());
            entity.setStatus(existing.getStatus());
        }
        if (isBuiltinAdmin(existing)) {
            if (!UserContext.isSuperAdmin()) {
                return ApiResponse.fail(403, "内置管理员「王师傅」的资料只有本人可以修改");
            }
            if (!ADMIN_USERNAME.equals(entity.getUsername())) {
                return ApiResponse.fail("内置管理员「王师傅」的用户名不能修改，请只改昵称");
            }
            entity.setUsername(ADMIN_USERNAME);
            entity.setUserRole(ROLE_SUPER_ADMIN);
            entity.setStatus(STATUS_NORMAL);
        } else if (isGuestUser(existing)) {
            if (!UserContext.isSuperAdmin()) {
                return ApiResponse.fail(403, "游客账号由系统维护，不能修改");
            }
            entity.setUsername(GUEST_USERNAME);
            entity.setUserRole(ROLE_USER);
            if (!STATUS_NORMAL.equals(entity.getStatus()) && !STATUS_DISABLED.equals(entity.getStatus())) {
                entity.setStatus(existing.getStatus());
            }
        } else {
            if (ADMIN_USERNAME.equals(entity.getUsername())) {
                return ApiResponse.fail("用户名「王师傅」为系统保留");
            }
            if (ROLE_SUPER_ADMIN.equals(entity.getUserRole())) {
                return ApiResponse.fail("只有王师傅可以是超级管理员");
            }
            applyRole(entity);
            if (!STATUS_NORMAL.equals(entity.getStatus()) && !STATUS_DISABLED.equals(entity.getStatus())) {
                entity.setStatus(existing.getStatus());
            }
        }
        if (StringUtils.hasText(entity.getUsername())
                && lambdaQuery()
                .ne(AppUser::getId, entity.getId())
                .eq(AppUser::getUsername, entity.getUsername())
                .exists()) {
            return ApiResponse.fail("用户名已存在");
        }
        if (StringUtils.hasText(entity.getAvatar()) && !DefaultAvatars.isAllowed(entity.getAvatar())) {
            return ApiResponse.fail("头像地址无效，请从默认头像中选择");
        }
        if (!StringUtils.hasText(entity.getAvatar())) {
            entity.setAvatar(existing.getAvatar());
        }
        if (entity.getPublicName() != null) {
            String publicName = entity.getPublicName().trim();
            if (publicName.length() > 20) {
                return ApiResponse.fail("公开昵称不能超过 20 个字符");
            }
            entity.setPublicName(publicName);
        }
        ApiResponse<Void> roleResult = applyMemberRoleChange(entity, membership, manager);
        if (roleResult != null) {
            return roleResult;
        }
        try {
            updateById(entity);
        } catch (DuplicateKeyException ex) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.fail("用户名或邮箱已被使用");
        }
        todoService.syncBirthdayTodo(super.getById(entity.getId()));
        return ApiResponse.success("更新成功", null);
    }

    /**
     * 家庭角色只由家庭所有者调整；非管理员提交的 memberRole 一律忽略（个人资料表单会回显该字段）。
     */
    private ApiResponse<Void> applyMemberRoleChange(AppUser entity, HouseholdMember membership, boolean manager) {
        String requested = normalizeMemberRole(entity.getMemberRole());
        entity.setMemberRole(null);
        if (requested == null || !manager || requested.equals(membership.getMemberRole())) {
            return null;
        }
        if (!UserContext.isHouseholdOwner()) {
            return ApiResponse.fail(403, "只有家庭所有者可以调整家庭角色");
        }
        if (MEMBER_OWNER.equals(membership.getMemberRole())) {
            return ApiResponse.fail("家庭所有者的角色请通过转让家庭调整");
        }
        if (UserContext.requireUserId().equals(membership.getUserId())) {
            return ApiResponse.fail("不能调整自己的家庭角色");
        }
        HouseholdMember patch = new HouseholdMember();
        patch.setId(membership.getId());
        patch.setMemberRole(requested);
        householdMemberService.updateById(patch);
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> delete(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("用户id不能为空");
        }
        AppUser user = super.getById(id);
        if (user == null) {
            return ApiResponse.fail("用户不存在");
        }
        if (isBuiltinAdmin(user)) {
            return ApiResponse.fail("内置管理员不能删除");
        }
        if (isGuestUser(user)) {
            return ApiResponse.fail("游客账号不能删除");
        }
        HouseholdMember membership = householdMemberService.find(TenantContext.require(), id);
        if (membership == null) {
            return ApiResponse.fail("该成员不属于当前家庭");
        }
        if (!UserContext.isHouseholdManager()) {
            return ApiResponse.fail(403, "只有家庭管理员可以移出家人");
        }
        if (UserContext.requireUserId().equals(id)) {
            return ApiResponse.fail("不能移出自己，请使用退出家庭");
        }
        if (MEMBER_OWNER.equals(membership.getMemberRole())) {
            return ApiResponse.fail("家庭所有者不能移出，请先转让家庭");
        }
        if (ROLE_SUPER_ADMIN.equals(user.getUserRole())) {
            return ApiResponse.fail("超级管理员不能被移出家庭");
        }
        userFavoriteService.deleteByUserId(id);
        recipeViewRecordService.deleteByUserId(id);
        userWantedRecipeService.deleteByUserId(id);
        userPushSubscriptionService.deleteByUserId(id);
        todoService.lambdaUpdate().eq(Todo::getOwnerId, id).remove();
        householdMemberService.removeById(membership.getId());
        boolean hasOtherHousehold = householdMemberService.lambdaQuery()
                .eq(HouseholdMember::getUserId, id)
                .exists();
        if (!hasOtherHousehold) {
            removeById(id);
        }
        return ApiResponse.success("删除成功", null);
    }

    @Override
    public ApiResponse<AppUser> login(UserLoginDTO dto, String clientIp) {
        if (dto == null || !StringUtils.hasText(dto.getUsername()) || !StringUtils.hasText(dto.getPassword())) {
            return ApiResponse.fail("用户名、邮箱或密码错误");
        }
        String identifier = dto.getUsername().trim();
        String attemptKey = "login:" + identifier;
        String lockedMessage = loginAttemptService.lockedMessage(attemptKey, clientIp);
        if (lockedMessage != null) {
            return ApiResponse.fail(429, lockedMessage);
        }
        List<AppUser> matches = baseMapper.findLoginByUsername(identifier);
        if (matches.isEmpty() && identifier.contains("@")) {
            matches = baseMapper.findLoginByEmail(identifier);
        }
        AppUser user = matches.size() == 1 ? matches.get(0) : null;
        if (user != null) {
            attemptKey = "user:" + user.getId();
            lockedMessage = loginAttemptService.lockedMessage(attemptKey, clientIp);
            if (lockedMessage != null) return ApiResponse.fail(429, lockedMessage);
        }
        if (user == null || !PasswordUtil.matches(dto.getPassword(), user.getPassword())) {
            loginAttemptService.onFailure(attemptKey, clientIp);
            return ApiResponse.fail("用户名、邮箱或密码错误");
        }
        loginAttemptService.onSuccess(attemptKey, clientIp);
        if (!STATUS_NORMAL.equals(user.getStatus())) {
            return ApiResponse.fail(disabledLoginMessage(user));
        }
        if (!PasswordUtil.isHashed(user.getPassword())) {
            AppUser passwordPatch = new AppUser();
            passwordPatch.setId(user.getId());
            passwordPatch.setPassword(PasswordUtil.hash(dto.getPassword()));
            updateById(passwordPatch);
        }
        ApiResponse<AppUser> householdResult = applyLoginHousehold(user);
        if (householdResult != null) {
            return householdResult;
        }
        user.setToken(jwtAuthUtil.createToken(user, user.getHouseholdId()));
        maskPassword(user);
        return ApiResponse.success(user);
    }

    @Override
    public ApiResponse<AppUser> guestLogin() {
        AppUser guest = super.getById(GUEST_ID);
        if (guest == null) {
            guest = lambdaQuery()
                    .eq(AppUser::getUsername, GUEST_USERNAME)
                    .one();
        }
        if (guest == null) {
            return ApiResponse.fail("游客账号未初始化");
        }
        if (!STATUS_NORMAL.equals(guest.getStatus())) {
            return ApiResponse.fail("当前系统已禁止游客登录，请联系管理员处理");
        }
        ApiResponse<AppUser> householdResult = applyLoginHousehold(guest);
        if (householdResult != null) {
            return householdResult;
        }
        guest.setToken(jwtAuthUtil.createToken(guest, guest.getHouseholdId()));
        maskPassword(guest);
        return ApiResponse.success(guest);
    }

    private ApiResponse<AppUser> applyLoginHousehold(AppUser user) {
        HouseholdMember membership = null;
        if (StringUtils.hasText(user.getActiveHouseholdId())) {
            membership = householdMemberService.find(user.getActiveHouseholdId(), user.getId());
            if (membership != null && !STATUS_NORMAL.equals(membership.getStatus())) {
                membership = null;
            }
        }
        if (membership == null) {
            membership = householdMemberService.findFirstByUser(user.getId());
        }
        if (membership == null) {
            return ApiResponse.fail("该账号尚未加入任何家庭，请联系管理员");
        }
        Household household = householdService.getById(membership.getHouseholdId());
        if (household == null || !STATUS_NORMAL.equals(household.getStatus())) {
            return ApiResponse.fail("所在家庭已停用，请联系管理员");
        }
        rememberHousehold(user.getId(), household.getId());
        user.setActiveHouseholdId(household.getId());
        user.setHouseholdId(household.getId());
        user.setHouseholdName(household.getHouseholdName());
        user.setMemberRole(membership.getMemberRole());
        return null;
    }

    @Override
    public void rememberHousehold(String userId, String householdId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(householdId)) {
            return;
        }
        AppUser patch = new AppUser();
        patch.setId(userId);
        patch.setActiveHouseholdId(householdId);
        updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> changePassword(String oldPassword, String newPassword) {
        AppUser user = baseMapper.lockById(UserContext.requireUserId());
        if (user == null) {
            return ApiResponse.fail("用户不存在");
        }
        if (isGuestUser(user)) {
            return ApiResponse.fail(403, "游客账号不能修改密码");
        }
        if (!StringUtils.hasText(oldPassword)) {
            return ApiResponse.fail("请输入原密码");
        }
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 8 || newPassword.length() > 64) {
            return ApiResponse.fail("新密码需为 8-64 位");
        }
        String next = newPassword;
        String attemptKey = "security:" + user.getId();
        String locked = loginAttemptService.lockedMessage(attemptKey, "");
        if (locked != null) return ApiResponse.fail(429, locked);
        if (!PasswordUtil.matches(oldPassword, user.getPassword())) {
            loginAttemptService.onFailure(attemptKey, "");
            return ApiResponse.fail("原密码不正确");
        }
        loginAttemptService.onSuccess(attemptKey, "");
        if (oldPassword.equals(next)) {
            return ApiResponse.fail("新密码不能与原密码相同");
        }
        baseMapper.replacePassword(user.getId(), PasswordUtil.hash(next));
        return ApiResponse.success("密码已更新", null);
    }

    private String normalizeAccountEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public ApiResponse<UserPreviewVO> previewByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return ApiResponse.success(null);
        }
        AppUser user = lambdaQuery()
                .eq(AppUser::getUsername, username.trim())
                .one();
        if (user == null) {
            return ApiResponse.success(null);
        }
        UserPreviewVO preview = new UserPreviewVO();
        preview.setId(user.getId());
        preview.setUsername(user.getUsername());
        preview.setNickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        preview.setAvatar(DefaultAvatars.normalizeOrPick(user.getAvatar(), user.getId()));
        return ApiResponse.success(preview);
    }

    @Override
    public ApiResponse<List<UserPreviewVO>> listMembers() {
        List<String> memberIds = householdMemberService.listUserIds(TenantContext.require());
        if (memberIds.isEmpty()) {
            return ApiResponse.success(new ArrayList<>());
        }
        List<UserPreviewVO> members = lambdaQuery()
                .in(AppUser::getId, memberIds)
                .eq(AppUser::getStatus, STATUS_NORMAL)
                .ne(AppUser::getId, GUEST_ID)
                .orderByAsc(AppUser::getNickname)
                .list()
                .stream()
                .map(this::toPreview)
                .collect(Collectors.toList());
        return ApiResponse.success(members);
    }

    private UserPreviewVO toPreview(AppUser user) {
        UserPreviewVO preview = new UserPreviewVO();
        preview.setId(user.getId());
        preview.setUsername(user.getUsername());
        preview.setNickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        preview.setAvatar(DefaultAvatars.normalizeOrPick(user.getAvatar(), user.getId()));
        if (UserContext.isGuest()) return preview;
        preview.setEmail(user.getEmail());
        preview.setBirthday(user.getBirthday());
        preview.setBirthdayCalendar(user.getBirthdayCalendar());
        preview.setLunarBirthdayYear(user.getLunarBirthdayYear());
        preview.setLunarBirthdayMonth(user.getLunarBirthdayMonth());
        preview.setLunarBirthdayDay(user.getLunarBirthdayDay());
        preview.setLunarBirthdayLeap(user.getLunarBirthdayLeap());
        preview.setNotificationPreference(user.getNotificationPreference());
        return preview;
    }

    @Override
    public ApiResponse<Void> setStatus(String id, String status) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("用户id不能为空");
        }
        if (!STATUS_NORMAL.equals(status) && !STATUS_DISABLED.equals(status)) {
            return ApiResponse.fail("无效状态");
        }
        AppUser user = super.getById(id);
        if (user == null) {
            return ApiResponse.fail("用户不存在");
        }
        if (isBuiltinAdmin(user)) {
            return ApiResponse.fail("内置管理员不能禁用");
        }
        if (householdMemberService.find(TenantContext.require(), id) == null) {
            return ApiResponse.fail("该用户不属于当前家庭");
        }
        if (status.equals(user.getStatus())) {
            return ApiResponse.success(STATUS_DISABLED.equals(status) ? "该用户已是禁用状态" : "该用户已是正常状态", null);
        }
        AppUser patch = new AppUser();
        patch.setId(id);
        patch.setStatus(status);
        patch.setUpdateTime(LocalDateTime.now());
        updateById(patch);
        String message = STATUS_DISABLED.equals(status) ? "已禁用该用户" : "已恢复该用户";
        return ApiResponse.success(message, null);
    }

    private void normalizeUser(AppUser user) {
        if (user == null) {
            return;
        }
        user.setUsername(user.getUsername().trim());
        if (!StringUtils.hasText(user.getNickname())) {
            user.setNickname(user.getUsername());
        }
        normalizeBirthday(user);
        if (ADMIN_USERNAME.equals(user.getUsername())) {
            user.setUserRole(ROLE_SUPER_ADMIN);
            user.setStatus(STATUS_NORMAL);
            if (!StringUtils.hasText(user.getNotificationPreference())) {
                user.setNotificationPreference("site,email");
            }
            return;
        }
        if (GUEST_USERNAME.equals(user.getUsername()) || GUEST_ID.equals(user.getId())) {
            user.setUserRole(ROLE_USER);
            if (!StringUtils.hasText(user.getStatus())) {
                user.setStatus(STATUS_NORMAL);
            }
            if (!StringUtils.hasText(user.getNotificationPreference())) {
                user.setNotificationPreference("site");
            }
            return;
        }
        applyRole(user);
        if (!StringUtils.hasText(user.getStatus())) {
            user.setStatus(STATUS_NORMAL);
        }
        user.setAvatar(DefaultAvatars.normalizeOrPick(user.getAvatar(), user.getUsername()));
        if (!StringUtils.hasText(user.getNotificationPreference())) {
            user.setNotificationPreference(StringUtils.hasText(user.getEmail()) ? "site,email" : "site");
        }
    }

    private void normalizeBirthday(AppUser user) {
        if (!StringUtils.hasText(user.getBirthdayCalendar())) {
            user.setBirthdayCalendar("SOLAR");
        }
        if (!"LUNAR".equals(user.getBirthdayCalendar())) {
            user.setBirthdayCalendar("SOLAR");
            user.setLunarBirthdayMonth(null);
            user.setLunarBirthdayDay(null);
            user.setLunarBirthdayLeap(false);
            return;
        }
        if (user.getLunarBirthdayLeap() == null) {
            user.setLunarBirthdayLeap(false);
        }
        user.setBirthday(nextLunarBirthday(user, LocalDate.now()));
    }

    public static LocalDate nextLunarBirthday(AppUser user, LocalDate baseDate) {
        if (user == null || user.getLunarBirthdayMonth() == null || user.getLunarBirthdayDay() == null) {
            return null;
        }
        LocalDate next = lunarToSolar(baseDate.getYear(), user.getLunarBirthdayMonth(),
                user.getLunarBirthdayDay(), Boolean.TRUE.equals(user.getLunarBirthdayLeap()));
        if (next == null || next.isBefore(baseDate)) {
            next = lunarToSolar(baseDate.getYear() + 1, user.getLunarBirthdayMonth(),
                    user.getLunarBirthdayDay(), Boolean.TRUE.equals(user.getLunarBirthdayLeap()));
        }
        return next;
    }

    public static LocalDate lunarToSolar(int lunarYear, int month, int day, boolean leap) {
        try {
            ChineseDate chineseDate = new ChineseDate(lunarYear, month, day, leap);
            return chineseDate.getGregorianDate().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
        } catch (Exception ex) {
            return null;
        }
    }

    private long countRecentTodo(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0L;
        }
        List<String> todoIds = todoOwnerMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TodoOwner>()
                        .eq(TodoOwner::getOwnerId, userId))
                .stream()
                .map(TodoOwner::getTodoId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dueSoonEnd = now.plusDays(7);
        return todoService.lambdaQuery()
                .eq(Todo::getStatus, "TODO")
                .ge(Todo::getDueTime, now)
                .le(Todo::getDueTime, dueSoonEnd)
                .and(wrapper -> {
                    wrapper.eq(Todo::getOwnerId, userId);
                    if (!todoIds.isEmpty()) {
                        wrapper.or().in(Todo::getId, todoIds);
                    }
                })
                .count();
    }

    /**
     * 家庭角色只允许 admin / member；owner 只能通过转让产生，guest 为系统内置。
     *
     * @return 合法角色，非法时返回 null
     */
    private String normalizeMemberRole(String memberRole) {
        if (!StringUtils.hasText(memberRole)) {
            return MEMBER_MEMBER;
        }
        if (MEMBER_ADMIN.equals(memberRole) || MEMBER_MEMBER.equals(memberRole)) {
            return memberRole;
        }
        return null;
    }

    /**
     * 全局角色只保留 super_admin（内置王师傅）与 user，家庭内权限一律由 member_role 决定。
     */
    private void applyRole(AppUser user) {
        user.setUserRole(ROLE_USER);
    }

    private boolean isBuiltinAdmin(AppUser user) {
        if (user == null) {
            return false;
        }
        if (ADMIN_ID.equals(user.getId())) {
            return true;
        }
        return ADMIN_USERNAME.equals(user.getUsername());
    }

    private boolean isGuestUser(AppUser user) {
        return user != null && (GUEST_ID.equals(user.getId()) || GUEST_USERNAME.equals(user.getUsername()));
    }

    private String disabledLoginMessage(AppUser user) {
        if (isGuestUser(user)) {
            return "当前系统已禁止游客登录，请联系管理员处理";
        }
        return "您的帐号已被禁用，请联系王师傅处理";
    }

    private void maskPassword(AppUser user) {
        if (user != null) {
            user.setPassword(null);
        }
    }
}
