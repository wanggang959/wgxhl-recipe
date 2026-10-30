package com.wgxhl.recipe.ingredient.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.ingredient.dto.IngredientPageDTO;
import com.wgxhl.recipe.ingredient.entity.Ingredient;
import com.wgxhl.recipe.ingredient.mapper.IngredientMapper;
import com.wgxhl.recipe.ingredient.service.IngredientService;
import com.wgxhl.recipe.relation.entity.RecipeIngredientRel;
import com.wgxhl.recipe.relation.mapper.RecipeIngredientRelMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class IngredientServiceImpl extends ServiceImpl<IngredientMapper, Ingredient>
        implements IngredientService {

    private final RecipeIngredientRelMapper recipeIngredientRelMapper;

    public IngredientServiceImpl(RecipeIngredientRelMapper recipeIngredientRelMapper) {
        this.recipeIngredientRelMapper = recipeIngredientRelMapper;
    }

    @Override
    public boolean save(Ingredient entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return super.save(entity);
    }

    @Override
    public boolean updateById(Ingredient entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }

    @Override
    public ApiResponse<Page<Ingredient>> page(IngredientPageDTO dto) {
        Page<Ingredient> page = new Page<>(dto.getCurrent(), dto.getSize());
        String userId = UserContext.requireUserId();
        Page<Ingredient> result = lambdaQuery()
                .and(wrapper -> wrapper
                        .eq(Ingredient::getScope, "platform")
                        .or(personal -> personal
                                .eq(Ingredient::getScope, "personal")
                                .eq(Ingredient::getOwnerUserId, userId)))
                .like(StringUtils.hasText(dto.getIngredientName()), Ingredient::getIngredientName, dto.getIngredientName())
                .and(StringUtils.hasText(dto.getKeyword()), wrapper -> wrapper
                        .like(Ingredient::getIngredientName, dto.getKeyword())
                        .or().like(Ingredient::getIngredientDesc, dto.getKeyword()))
                .orderByAsc(Ingredient::getScope)
                .orderByDesc(Ingredient::getCreateTime)
                .orderByDesc(Ingredient::getId)
                .page(page);
        return ApiResponse.success(result);
    }

    @Override
    public ApiResponse<Ingredient> getById(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("食材id不能为空");
        }
        Ingredient ingredient = super.getById(id);
        if (ingredient == null || !canView(ingredient)) {
            return ApiResponse.fail("食材不存在");
        }
        return ApiResponse.success(ingredient);
    }

    @Override
    public ApiResponse<Ingredient> create(Ingredient entity) {
        if (!StringUtils.hasText(entity.getIngredientName())) {
            return ApiResponse.fail("食材名称不能为空");
        }
        applyScope(entity);
        if (lambdaQuery()
                .eq(Ingredient::getOwnerUserId, entity.getOwnerUserId())
                .eq(Ingredient::getIngredientName, entity.getIngredientName())
                .exists()) {
            return ApiResponse.fail("食材名称已存在");
        }
        save(entity);
        return ApiResponse.success("保存成功", entity);
    }

    @Override
    public ApiResponse<Void> update(Ingredient entity) {
        if (!StringUtils.hasText(entity.getId())) {
            return ApiResponse.fail("食材id不能为空");
        }
        Ingredient existing = super.getById(entity.getId());
        if (existing == null) {
            return ApiResponse.fail("食材不存在");
        }
        if (!canManage(existing)) {
            return ApiResponse.fail(403, "只能编辑自己的食材");
        }
        entity.setScope(existing.getScope());
        entity.setOwnerUserId(existing.getOwnerUserId());
        if (StringUtils.hasText(entity.getIngredientName())
                && lambdaQuery()
                .ne(Ingredient::getId, entity.getId())
                .eq(Ingredient::getOwnerUserId, existing.getOwnerUserId())
                .eq(Ingredient::getIngredientName, entity.getIngredientName())
                .exists()) {
            return ApiResponse.fail("食材名称已存在");
        }
        updateById(entity);
        return ApiResponse.success("更新成功", null);
    }

    @Override
    public ApiResponse<Void> delete(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("食材id不能为空");
        }
        Ingredient existing = super.getById(id);
        if (existing == null) {
            return ApiResponse.fail("食材不存在");
        }
        if (!canManage(existing)) {
            return ApiResponse.fail(403, "只能删除自己的食材");
        }
        Long relCount = recipeIngredientRelMapper.selectCount(
                new LambdaQueryWrapper<RecipeIngredientRel>()
                        .eq(RecipeIngredientRel::getIngredientId, id));
        if (relCount != null && relCount > 0) {
            return ApiResponse.fail("该食材已被菜谱引用，无法删除");
        }
        removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    private void applyScope(Ingredient entity) {
        if (UserContext.isSuperAdmin()) {
            entity.setScope("platform");
            entity.setOwnerUserId("platform");
        } else {
            entity.setScope("personal");
            entity.setOwnerUserId(UserContext.requireUserId());
        }
    }

    private boolean canView(Ingredient ingredient) {
        return "platform".equals(ingredient.getScope())
                || UserContext.requireUserId().equals(ingredient.getOwnerUserId());
    }

    private boolean canManage(Ingredient ingredient) {
        if ("platform".equals(ingredient.getScope())) {
            return UserContext.isSuperAdmin();
        }
        return UserContext.requireUserId().equals(ingredient.getOwnerUserId());
    }
}
