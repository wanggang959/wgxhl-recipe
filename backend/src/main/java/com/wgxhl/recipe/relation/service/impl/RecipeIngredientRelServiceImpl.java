package com.wgxhl.recipe.relation.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.ingredient.entity.Ingredient;
import com.wgxhl.recipe.ingredient.service.IngredientService;
import com.wgxhl.recipe.relation.dto.RecipeIngredientRelBatchDTO;
import com.wgxhl.recipe.relation.entity.RecipeIngredientRel;
import com.wgxhl.recipe.relation.mapper.RecipeIngredientRelMapper;
import com.wgxhl.recipe.relation.service.RecipeIngredientRelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RecipeIngredientRelServiceImpl extends ServiceImpl<RecipeIngredientRelMapper, RecipeIngredientRel>
        implements RecipeIngredientRelService {

    private final IngredientService ingredientService;

    public RecipeIngredientRelServiceImpl(IngredientService ingredientService) {
        this.ingredientService = ingredientService;
    }

    @Override
    public boolean save(RecipeIngredientRel entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return super.save(entity);
    }

    @Override
    public boolean updateById(RecipeIngredientRel entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }

    @Override
    public ApiResponse<List<RecipeIngredientRel>> listByRecipeId(String recipeId) {
        if (!StringUtils.hasText(recipeId)) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        List<RecipeIngredientRel> list = lambdaQuery()
                .eq(RecipeIngredientRel::getRecipeId, recipeId)
                .orderByAsc(RecipeIngredientRel::getSortNo)
                .orderByAsc(RecipeIngredientRel::getCreateTime)
                .list();
        list.forEach(this::fillIngredientView);
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<RecipeIngredientRel> create(RecipeIngredientRel entity) {
        if (!StringUtils.hasText(entity.getRecipeId())) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        if (!StringUtils.hasText(entity.getIngredientId())) {
            return ApiResponse.fail("食材id不能为空");
        }
        if (lambdaQuery()
                .eq(RecipeIngredientRel::getRecipeId, entity.getRecipeId())
                .eq(RecipeIngredientRel::getIngredientId, entity.getIngredientId())
                .exists()) {
            return ApiResponse.fail("该菜谱下食材关系已存在");
        }
        ApiResponse<Void> fillResult = fillIngredientName(entity);
        if (fillResult != null) {
            return ApiResponse.fail(fillResult.getMessage());
        }
        save(entity);
        return ApiResponse.success("保存成功", entity);
    }

    @Override
    public ApiResponse<Void> update(RecipeIngredientRel entity) {
        if (!StringUtils.hasText(entity.getId())) {
            return ApiResponse.fail("关系id不能为空");
        }
        RecipeIngredientRel existing = super.getById(entity.getId());
        if (existing == null) {
            return ApiResponse.fail("关系不存在");
        }
        if (StringUtils.hasText(entity.getIngredientId())
                && !entity.getIngredientId().equals(existing.getIngredientId())) {
            ApiResponse<Void> fillResult = fillIngredientName(entity);
            if (fillResult != null) {
                return fillResult;
            }
            if (lambdaQuery()
                    .ne(RecipeIngredientRel::getId, entity.getId())
                    .eq(RecipeIngredientRel::getRecipeId, existing.getRecipeId())
                    .eq(RecipeIngredientRel::getIngredientId, entity.getIngredientId())
                    .exists()) {
                return ApiResponse.fail("该菜谱下食材关系已存在");
            }
        }
        updateById(entity);
        return ApiResponse.success("更新成功", null);
    }

    @Override
    public ApiResponse<Void> delete(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("关系id不能为空");
        }
        if (super.getById(id) == null) {
            return ApiResponse.fail("关系不存在");
        }
        removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> saveBatchByRecipeId(RecipeIngredientRelBatchDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getRecipeId())) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        ApiResponse<Void> validateResult = validateRelList(dto.getRelList());
        if (validateResult != null) {
            return validateResult;
        }
        doSaveBatchByRecipeId(dto.getRecipeId(), dto.getRelList());
        return ApiResponse.success("保存成功", null);
    }

    @Override
    public void deleteByRecipeId(String recipeId) {
        if (!StringUtils.hasText(recipeId)) {
            return;
        }
        remove(new LambdaQueryWrapper<RecipeIngredientRel>()
                .eq(RecipeIngredientRel::getRecipeId, recipeId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBatchByRecipeId(String recipeId, List<RecipeIngredientRel> relList) {
        if (!StringUtils.hasText(recipeId)) {
            return;
        }
        doSaveBatchByRecipeId(recipeId, relList);
    }

    private void doSaveBatchByRecipeId(String recipeId, List<RecipeIngredientRel> relList) {
        remove(new LambdaQueryWrapper<RecipeIngredientRel>()
                .eq(RecipeIngredientRel::getRecipeId, recipeId));
        if (relList != null) {
            for (RecipeIngredientRel rel : relList) {
                rel.setId(null);
                rel.setRecipeId(recipeId);
                ApiResponse<Void> fillResult = fillIngredientName(rel);
                if (fillResult == null) {
                    save(rel);
                }
            }
        }
    }

    private ApiResponse<Void> validateRelList(List<RecipeIngredientRel> relList) {
        if (relList == null) {
            return null;
        }
        Set<String> ingredientIds = new HashSet<>();
        for (RecipeIngredientRel rel : relList) {
            if (!StringUtils.hasText(rel.getIngredientId())) {
                return ApiResponse.fail("食材id不能为空");
            }
            if (!ingredientIds.add(rel.getIngredientId())) {
                return ApiResponse.fail("食材不能重复");
            }
            ApiResponse<Void> fillResult = fillIngredientName(rel);
            if (fillResult != null) {
                return fillResult;
            }
        }
        return null;
    }

    private ApiResponse<Void> fillIngredientName(RecipeIngredientRel entity) {
        Ingredient ingredient = ingredientService.lambdaQuery()
                .eq(Ingredient::getId, entity.getIngredientId())
                .one();
        if (ingredient == null) {
            return ApiResponse.fail("食材不存在");
        }
        entity.setIngredientName(ingredient.getIngredientName());
        entity.setIngredientImage(ingredient.getIngredientImage());
        return null;
    }

    private void fillIngredientView(RecipeIngredientRel entity) {
        if (entity == null || !StringUtils.hasText(entity.getIngredientId())) {
            return;
        }
        Ingredient ingredient = ingredientService.lambdaQuery()
                .eq(Ingredient::getId, entity.getIngredientId())
                .one();
        if (ingredient == null) {
            return;
        }
        entity.setIngredientName(ingredient.getIngredientName());
        entity.setIngredientImage(ingredient.getIngredientImage());
    }
}
