package com.wgxhl.recipe.user.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.JwtAuthUtil;
import com.wgxhl.recipe.favorite.service.UserFavoriteService;
import com.wgxhl.recipe.record.service.RecipeViewRecordService;
import com.wgxhl.recipe.user.dto.UserLoginDTO;
import com.wgxhl.recipe.user.dto.UserPageDTO;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.vo.UserPreviewVO;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import com.wgxhl.recipe.user.service.AppUserService;
import com.wgxhl.recipe.user.util.DefaultAvatars;
import com.wgxhl.recipe.want.service.UserWantedRecipeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class AppUserServiceImpl extends ServiceImpl<AppUserMapper, AppUser>
        implements AppUserService {

    private static final String ADMIN_ID = "admin-wangshifu";
    private static final String GUEST_ID = "guest";
    private static final String GUEST_USERNAME = "guest";
    private static final String ADMIN_USERNAME = "王师傅";
    private static final String ADMIN_PASSWORD = "123456";
    private static final String ROLE_SUPER_ADMIN = "super_admin";
    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_USER = "user";
    private static final String STATUS_NORMAL = "normal";
    private static final String STATUS_DISABLED = "disabled";

    private final UserFavoriteService userFavoriteService;
    private final RecipeViewRecordService recipeViewRecordService;
    private final UserWantedRecipeService userWantedRecipeService;
    private final JwtAuthUtil jwtAuthUtil;

    public AppUserServiceImpl(UserFavoriteService userFavoriteService,
                              RecipeViewRecordService recipeViewRecordService,
                              UserWantedRecipeService userWantedRecipeService,
                              JwtAuthUtil jwtAuthUtil) {
        this.userFavoriteService = userFavoriteService;
        this.recipeViewRecordService = recipeViewRecordService;
        this.userWantedRecipeService = userWantedRecipeService;
        this.jwtAuthUtil = jwtAuthUtil;
    }

    @PostConstruct
    public void ensureBuiltinAccounts() {
        ensureAdminAccount();
        ensureGuestAccount();
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
            admin.setPassword(ADMIN_PASSWORD);
            admin.setUserRole(ROLE_SUPER_ADMIN);
            admin.setStatus(STATUS_NORMAL);
            admin.setAvatar(DefaultAvatars.ADMIN_AVATAR);
            save(admin);
            return;
        }
        boolean changed = false;
        if (!ADMIN_USERNAME.equals(admin.getUsername())) {
            admin.setUsername(ADMIN_USERNAME);
            changed = true;
        }
        if (!Objects.equals(admin.getPassword(), ADMIN_PASSWORD)) {
            admin.setPassword(ADMIN_PASSWORD);
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
        Page<AppUser> result = lambdaQuery()
                .like(StringUtils.hasText(dto.getUsername()), AppUser::getUsername, dto.getUsername())
                .like(StringUtils.hasText(dto.getNickname()), AppUser::getNickname, dto.getNickname())
                .eq(StringUtils.hasText(dto.getUserRole()), AppUser::getUserRole, dto.getUserRole())
                .eq(StringUtils.hasText(dto.getStatus()), AppUser::getStatus, dto.getStatus())
                .orderByDesc(AppUser::getCreateTime)
                .page(page);
        result.getRecords().forEach(this::maskPassword);
        return ApiResponse.success(result);
    }

    @Override
    public ApiResponse<AppUser> getById(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("用户id不能为空");
        }
        AppUser user = super.getById(id);
        if (user == null) {
            return ApiResponse.fail("用户不存在");
        }
        maskPassword(user);
        return ApiResponse.success(user);
    }

    @Override
    public ApiResponse<AppUser> create(AppUser entity) {
        if (!StringUtils.hasText(entity.getUsername())) {
            return ApiResponse.fail("用户名不能为空");
        }
        if (!StringUtils.hasText(entity.getPassword())) {
            return ApiResponse.fail("密码不能为空");
        }
        if (lambdaQuery().eq(AppUser::getUsername, entity.getUsername()).exists()) {
            return ApiResponse.fail("用户名已存在");
        }
        if (StringUtils.hasText(entity.getAvatar()) && !DefaultAvatars.isAllowed(entity.getAvatar())) {
            return ApiResponse.fail("头像地址无效，请从默认头像中选择");
        }
        normalizeUser(entity);
        save(entity);
        maskPassword(entity);
        return ApiResponse.success("保存成功", entity);
    }

    @Override
    public ApiResponse<Void> update(AppUser entity) {
        if (!StringUtils.hasText(entity.getId())) {
            return ApiResponse.fail("用户id不能为空");
        }
        AppUser existing = super.getById(entity.getId());
        if (existing == null) {
            return ApiResponse.fail("用户不存在");
        }
        if (isBuiltinAdmin(existing)) {
            if (!ADMIN_USERNAME.equals(entity.getUsername())) {
                return ApiResponse.fail("内置管理员「王师傅」的用户名不能修改，请只改昵称");
            }
            entity.setUsername(ADMIN_USERNAME);
            entity.setUserRole(ROLE_SUPER_ADMIN);
            entity.setStatus(STATUS_NORMAL);
        } else if (isGuestUser(existing)) {
            entity.setUsername(GUEST_USERNAME);
            entity.setUserRole(ROLE_USER);
            if (!StringUtils.hasText(entity.getStatus())) {
                entity.setStatus(existing.getStatus());
            } else if (!STATUS_NORMAL.equals(entity.getStatus()) && !STATUS_DISABLED.equals(entity.getStatus())) {
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
            if (!StringUtils.hasText(entity.getStatus())) {
                entity.setStatus(existing.getStatus());
            } else if (!STATUS_NORMAL.equals(entity.getStatus()) && !STATUS_DISABLED.equals(entity.getStatus())) {
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
        updateById(entity);
        return ApiResponse.success("更新成功", null);
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
        userFavoriteService.deleteByUserId(id);
        recipeViewRecordService.deleteByUserId(id);
        userWantedRecipeService.deleteByUserId(id);
        removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    @Override
    public ApiResponse<AppUser> login(UserLoginDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getUsername()) || !StringUtils.hasText(dto.getPassword())) {
            return ApiResponse.fail("用户名或密码错误");
        }
        AppUser user = lambdaQuery()
                .eq(AppUser::getUsername, dto.getUsername())
                .one();
        if (user == null || !Objects.equals(dto.getPassword(), user.getPassword())) {
            return ApiResponse.fail("用户名或密码错误");
        }
        if (!STATUS_NORMAL.equals(user.getStatus())) {
            return ApiResponse.fail(disabledLoginMessage(user));
        }
        user.setToken(jwtAuthUtil.createToken(user));
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
        guest.setToken(jwtAuthUtil.createToken(guest));
        maskPassword(guest);
        return ApiResponse.success(guest);
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
        if (ADMIN_USERNAME.equals(user.getUsername())) {
            user.setPassword(ADMIN_PASSWORD);
            user.setUserRole(ROLE_SUPER_ADMIN);
            user.setStatus(STATUS_NORMAL);
            return;
        }
        if (GUEST_USERNAME.equals(user.getUsername()) || GUEST_ID.equals(user.getId())) {
            user.setUserRole(ROLE_USER);
            if (!StringUtils.hasText(user.getStatus())) {
                user.setStatus(STATUS_NORMAL);
            }
            return;
        }
        applyRole(user);
        if (!StringUtils.hasText(user.getStatus())) {
            user.setStatus(STATUS_NORMAL);
        }
        user.setAvatar(DefaultAvatars.normalizeOrPick(user.getAvatar(), user.getUsername()));
    }

    private void applyRole(AppUser user) {
        if (ROLE_SUPER_ADMIN.equals(user.getUserRole())) {
            user.setUserRole(ROLE_USER);
            return;
        }
        if (ROLE_ADMIN.equals(user.getUserRole())) {
            user.setUserRole(ROLE_ADMIN);
        } else {
            user.setUserRole(ROLE_USER);
        }
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
