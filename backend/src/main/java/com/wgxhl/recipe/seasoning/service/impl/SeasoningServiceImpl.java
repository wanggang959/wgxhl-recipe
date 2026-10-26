package com.wgxhl.recipe.seasoning.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.seasoning.dto.SeasoningPageDTO;
import com.wgxhl.recipe.seasoning.entity.Seasoning;
import com.wgxhl.recipe.seasoning.mapper.SeasoningMapper;
import com.wgxhl.recipe.seasoning.service.SeasoningService;
import com.wgxhl.recipe.seasoningrelation.entity.RecipeSeasoningRel;
import com.wgxhl.recipe.seasoningrelation.mapper.RecipeSeasoningRelMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class SeasoningServiceImpl extends ServiceImpl<SeasoningMapper, Seasoning>
        implements SeasoningService {

    private final RecipeSeasoningRelMapper recipeSeasoningRelMapper;

    public SeasoningServiceImpl(RecipeSeasoningRelMapper recipeSeasoningRelMapper) {
        this.recipeSeasoningRelMapper = recipeSeasoningRelMapper;
    }

    @Override
    public boolean save(Seasoning entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return super.save(entity);
    }

    @Override
    public boolean updateById(Seasoning entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }

    @Override
    public ApiResponse<Page<Seasoning>> page(SeasoningPageDTO dto) {
        Page<Seasoning> page = new Page<>(dto.getCurrent(), dto.getSize());
        String userId = UserContext.requireUserId();
        Page<Seasoning> result = lambdaQuery()
                .and(wrapper -> wrapper
                        .eq(Seasoning::getScope, "platform")
                        .or(personal -> personal
                                .eq(Seasoning::getScope, "personal")
                                .eq(Seasoning::getOwnerUserId, userId)))
                .like(StringUtils.hasText(dto.getSeasoningName()), Seasoning::getSeasoningName, dto.getSeasoningName())
                .orderByDesc(Seasoning::getCreateTime)
                .page(page);
        return ApiResponse.success(result);
    }

    @Override
    public ApiResponse<Seasoning> getById(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("调料id不能为空");
        }
        Seasoning seasoning = super.getById(id);
        if (seasoning == null || !canView(seasoning)) {
            return ApiResponse.fail("调料不存在");
        }
        return ApiResponse.success(seasoning);
    }

    @Override
    public ApiResponse<Seasoning> create(Seasoning entity) {
        if (!StringUtils.hasText(entity.getSeasoningName())) {
            return ApiResponse.fail("调料名称不能为空");
        }
        applyScope(entity);
        if (lambdaQuery()
                .eq(Seasoning::getOwnerUserId, entity.getOwnerUserId())
                .eq(Seasoning::getSeasoningName, entity.getSeasoningName())
                .exists()) {
            return ApiResponse.fail("调料名称已存在");
        }
        save(entity);
        return ApiResponse.success("保存成功", entity);
    }

    @Override
    public ApiResponse<Void> update(Seasoning entity) {
        if (!StringUtils.hasText(entity.getId())) {
            return ApiResponse.fail("调料id不能为空");
        }
        Seasoning existing = super.getById(entity.getId());
        if (existing == null) {
            return ApiResponse.fail("调料不存在");
        }
        if (!canManage(existing)) {
            return ApiResponse.fail(403, "只能编辑自己的调料");
        }
        entity.setScope(existing.getScope());
        entity.setOwnerUserId(existing.getOwnerUserId());
        if (StringUtils.hasText(entity.getSeasoningName())
                && lambdaQuery()
                .ne(Seasoning::getId, entity.getId())
                .eq(Seasoning::getOwnerUserId, existing.getOwnerUserId())
                .eq(Seasoning::getSeasoningName, entity.getSeasoningName())
                .exists()) {
            return ApiResponse.fail("调料名称已存在");
        }
        updateById(entity);
        return ApiResponse.success("更新成功", null);
    }

    @Override
    public ApiResponse<Void> delete(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("调料id不能为空");
        }
        Seasoning existing = super.getById(id);
        if (existing == null) {
            return ApiResponse.fail("调料不存在");
        }
        if (!canManage(existing)) {
            return ApiResponse.fail(403, "只能删除自己的调料");
        }
        Long relCount = recipeSeasoningRelMapper.selectCount(
                new LambdaQueryWrapper<RecipeSeasoningRel>()
                        .eq(RecipeSeasoningRel::getSeasoningId, id));
        if (relCount != null && relCount > 0) {
            return ApiResponse.fail("该调料已被菜谱引用，无法删除");
        }
        removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    private void applyScope(Seasoning entity) {
        if (UserContext.isSuperAdmin()) {
            entity.setScope("platform");
            entity.setOwnerUserId("platform");
        } else {
            entity.setScope("personal");
            entity.setOwnerUserId(UserContext.requireUserId());
        }
    }

    private boolean canView(Seasoning seasoning) {
        return "platform".equals(seasoning.getScope())
                || UserContext.requireUserId().equals(seasoning.getOwnerUserId());
    }

    private boolean canManage(Seasoning seasoning) {
        if ("platform".equals(seasoning.getScope())) {
            return UserContext.isSuperAdmin();
        }
        return UserContext.requireUserId().equals(seasoning.getOwnerUserId());
    }
}
