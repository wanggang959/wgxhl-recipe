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
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import com.wgxhl.recipe.user.service.AppUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class AppUserServiceImpl extends ServiceImpl<AppUserMapper, AppUser>
        implements AppUserService {

    private static final String ADMIN_USERNAME = "王师傅";
    private static final String ADMIN_PASSWORD = "123456";
    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_USER = "user";
    private static final String STATUS_NORMAL = "normal";

    private final UserFavoriteService userFavoriteService;
    private final RecipeViewRecordService recipeViewRecordService;
    private final JwtAuthUtil jwtAuthUtil;

    public AppUserServiceImpl(UserFavoriteService userFavoriteService,
                              RecipeViewRecordService recipeViewRecordService,
                              JwtAuthUtil jwtAuthUtil) {
        this.userFavoriteService = userFavoriteService;
        this.recipeViewRecordService = recipeViewRecordService;
        this.jwtAuthUtil = jwtAuthUtil;
    }

    @PostConstruct
    public void ensureAdminAccount() {
        AppUser admin = lambdaQuery()
                .eq(AppUser::getUsername, ADMIN_USERNAME)
                .one();
        if (admin == null) {
            admin = new AppUser();
            admin.setUsername(ADMIN_USERNAME);
            admin.setNickname(ADMIN_USERNAME);
            admin.setPassword(ADMIN_PASSWORD);
            admin.setUserRole(ROLE_ADMIN);
            admin.setStatus(STATUS_NORMAL);
            save(admin);
            return;
        }
        boolean changed = false;
        if (!Objects.equals(admin.getPassword(), ADMIN_PASSWORD)) {
            admin.setPassword(ADMIN_PASSWORD);
            changed = true;
        }
        if (!ROLE_ADMIN.equals(admin.getUserRole())) {
            admin.setUserRole(ROLE_ADMIN);
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
        if (changed) {
            updateById(admin);
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
        if (super.getById(entity.getId()) == null) {
            return ApiResponse.fail("用户不存在");
        }
        if (ADMIN_USERNAME.equals(entity.getUsername())) {
            entity.setUserRole(ROLE_ADMIN);
            entity.setStatus(STATUS_NORMAL);
        } else if (ROLE_ADMIN.equals(entity.getUserRole())) {
            return ApiResponse.fail("只有内置账号王师傅可以设置为管理员");
        }
        if (StringUtils.hasText(entity.getUsername())
                && lambdaQuery()
                .ne(AppUser::getId, entity.getId())
                .eq(AppUser::getUsername, entity.getUsername())
                .exists()) {
            return ApiResponse.fail("用户名已存在");
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
        if (ADMIN_USERNAME.equals(user.getUsername())) {
            return ApiResponse.fail("内置管理员不能删除");
        }
        userFavoriteService.deleteByUserId(id);
        recipeViewRecordService.deleteByUserId(id);
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
        if (!"normal".equals(user.getStatus())) {
            return ApiResponse.fail("用户已被禁用");
        }
        user.setToken(jwtAuthUtil.createToken(user));
        maskPassword(user);
        return ApiResponse.success(user);
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
            user.setUserRole(ROLE_ADMIN);
            user.setStatus(STATUS_NORMAL);
            return;
        }
        user.setUserRole(ROLE_USER);
        if (!StringUtils.hasText(user.getStatus())) {
            user.setStatus(STATUS_NORMAL);
        }
    }

    private void maskPassword(AppUser user) {
        if (user != null) {
            user.setPassword(null);
        }
    }
}
