package com.wgxhl.recipe.category.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.category.dto.RecipeCategoryPageDTO;
import com.wgxhl.recipe.category.entity.RecipeCategory;
import com.wgxhl.recipe.category.mapper.RecipeCategoryMapper;
import com.wgxhl.recipe.category.service.RecipeCategoryService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.recipe.entity.Recipe;
import com.wgxhl.recipe.recipe.mapper.RecipeMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class RecipeCategoryServiceImpl extends ServiceImpl<RecipeCategoryMapper, RecipeCategory>
        implements RecipeCategoryService {

    private final RecipeMapper recipeMapper;

    public RecipeCategoryServiceImpl(RecipeMapper recipeMapper) {
        this.recipeMapper = recipeMapper;
    }

    @Override
    public boolean save(RecipeCategory entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return super.save(entity);
    }

    @Override
    public boolean updateById(RecipeCategory entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }

    @Override
    public ApiResponse<Page<RecipeCategory>> page(RecipeCategoryPageDTO dto) {
        Page<RecipeCategory> page = new Page<>(dto.getCurrent(), dto.getSize());
        String userId = UserContext.requireUserId();
        Page<RecipeCategory> result = lambdaQuery()
                .and(wrapper -> wrapper
                        .eq(RecipeCategory::getScope, "platform")
                        .or(personal -> personal
                                .eq(RecipeCategory::getScope, "personal")
                                .eq(RecipeCategory::getOwnerUserId, userId)))
                .like(StringUtils.hasText(dto.getCategoryName()), RecipeCategory::getCategoryName, dto.getCategoryName())
                .eq(StringUtils.hasText(dto.getCategoryCode()), RecipeCategory::getCategoryCode, dto.getCategoryCode())
                .orderByAsc(RecipeCategory::getSortNo)
                .orderByDesc(RecipeCategory::getCreateTime)
                .page(page);
        return ApiResponse.success(result);
    }

    @Override
    public ApiResponse<RecipeCategory> getById(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("分类id不能为空");
        }
        RecipeCategory category = super.getById(id);
        if (category == null || !canView(category)) {
            return ApiResponse.fail("分类不存在");
        }
        return ApiResponse.success(category);
    }

    @Override
    public ApiResponse<RecipeCategory> create(RecipeCategory entity) {
        if (!StringUtils.hasText(entity.getCategoryCode())) {
            return ApiResponse.fail("分类编码不能为空");
        }
        if (!StringUtils.hasText(entity.getCategoryName())) {
            return ApiResponse.fail("分类名称不能为空");
        }
        applyScope(entity);
        if (lambdaQuery()
                .eq(RecipeCategory::getOwnerUserId, entity.getOwnerUserId())
                .eq(RecipeCategory::getCategoryCode, entity.getCategoryCode())
                .exists()) {
            return ApiResponse.fail("分类编码已存在");
        }
        if (lambdaQuery()
                .eq(RecipeCategory::getOwnerUserId, entity.getOwnerUserId())
                .eq(RecipeCategory::getCategoryName, entity.getCategoryName())
                .exists()) {
            return ApiResponse.fail("分类名称已存在");
        }
        save(entity);
        return ApiResponse.success("保存成功", entity);
    }

    @Override
    public ApiResponse<Void> update(RecipeCategory entity) {
        if (!StringUtils.hasText(entity.getId())) {
            return ApiResponse.fail("分类id不能为空");
        }
        RecipeCategory existing = super.getById(entity.getId());
        if (existing == null) {
            return ApiResponse.fail("分类不存在");
        }
        if (!canManage(existing)) {
            return ApiResponse.fail(403, "只能编辑自己的分类");
        }
        entity.setScope(existing.getScope());
        entity.setOwnerUserId(existing.getOwnerUserId());
        if (StringUtils.hasText(entity.getCategoryCode())
                && lambdaQuery()
                .ne(RecipeCategory::getId, entity.getId())
                .eq(RecipeCategory::getOwnerUserId, existing.getOwnerUserId())
                .eq(RecipeCategory::getCategoryCode, entity.getCategoryCode())
                .exists()) {
            return ApiResponse.fail("分类编码已存在");
        }
        if (StringUtils.hasText(entity.getCategoryName())
                && lambdaQuery()
                .ne(RecipeCategory::getId, entity.getId())
                .eq(RecipeCategory::getOwnerUserId, existing.getOwnerUserId())
                .eq(RecipeCategory::getCategoryName, entity.getCategoryName())
                .exists()) {
            return ApiResponse.fail("分类名称已存在");
        }
        updateById(entity);
        return ApiResponse.success("更新成功", null);
    }

    @Override
    public ApiResponse<Void> delete(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("分类id不能为空");
        }
        RecipeCategory existing = super.getById(id);
        if (existing == null) {
            return ApiResponse.fail("分类不存在");
        }
        if (!canManage(existing)) {
            return ApiResponse.fail(403, "只能删除自己的分类");
        }
        Long recipeCount = recipeMapper.selectCount(
                new LambdaQueryWrapper<Recipe>().eq(Recipe::getCategoryId, id));
        if (recipeCount != null && recipeCount > 0) {
            return ApiResponse.fail("该分类下存在菜谱，无法删除");
        }
        removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    private void applyScope(RecipeCategory entity) {
        if (UserContext.isSuperAdmin()) {
            entity.setScope("platform");
            entity.setOwnerUserId("platform");
        } else {
            entity.setScope("personal");
            entity.setOwnerUserId(UserContext.requireUserId());
        }
    }

    private boolean canView(RecipeCategory category) {
        return "platform".equals(category.getScope())
                || UserContext.requireUserId().equals(category.getOwnerUserId());
    }

    private boolean canManage(RecipeCategory category) {
        if ("platform".equals(category.getScope())) {
            return UserContext.isSuperAdmin();
        }
        return UserContext.requireUserId().equals(category.getOwnerUserId());
    }
}
