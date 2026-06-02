package com.wgxhl.recipe.ingredient.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.ingredient.dto.IngredientPageDTO;
import com.wgxhl.recipe.ingredient.entity.Ingredient;
import com.wgxhl.recipe.ingredient.mapper.IngredientMapper;
import com.wgxhl.recipe.ingredient.service.IngredientService;
import com.wgxhl.recipe.relation.entity.RecipeIngredientRel;
import com.wgxhl.recipe.relation.mapper.RecipeIngredientRelMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

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
        Page<Ingredient> result = lambdaQuery()
                .like(StringUtils.hasText(dto.getIngredientName()), Ingredient::getIngredientName, dto.getIngredientName())
                .orderByDesc(Ingredient::getCreateTime)
                .page(page);
        return ApiResponse.success(result);
    }

    @Override
    public ApiResponse<Ingredient> getById(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("食材id不能为空");
        }
        Ingredient ingredient = super.getById(id);
        if (ingredient == null) {
            return ApiResponse.fail("食材不存在");
        }
        return ApiResponse.success(ingredient);
    }

    @Override
    public ApiResponse<Ingredient> create(Ingredient entity) {
        if (!StringUtils.hasText(entity.getIngredientName())) {
            return ApiResponse.fail("食材名称不能为空");
        }
        if (lambdaQuery().eq(Ingredient::getIngredientName, entity.getIngredientName()).exists()) {
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
        if (super.getById(entity.getId()) == null) {
            return ApiResponse.fail("食材不存在");
        }
        if (StringUtils.hasText(entity.getIngredientName())
                && lambdaQuery()
                .ne(Ingredient::getId, entity.getId())
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
        if (super.getById(id) == null) {
            return ApiResponse.fail("食材不存在");
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
}
