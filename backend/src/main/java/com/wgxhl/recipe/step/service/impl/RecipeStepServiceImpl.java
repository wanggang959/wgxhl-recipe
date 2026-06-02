package com.wgxhl.recipe.step.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.step.dto.RecipeStepBatchDTO;
import com.wgxhl.recipe.step.entity.RecipeStep;
import com.wgxhl.recipe.step.mapper.RecipeStepMapper;
import com.wgxhl.recipe.step.service.RecipeStepService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RecipeStepServiceImpl extends ServiceImpl<RecipeStepMapper, RecipeStep>
        implements RecipeStepService {

    @Override
    public boolean save(RecipeStep entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return super.save(entity);
    }

    @Override
    public boolean updateById(RecipeStep entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }

    @Override
    public ApiResponse<List<RecipeStep>> listByRecipeId(String recipeId) {
        if (!StringUtils.hasText(recipeId)) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        List<RecipeStep> list = lambdaQuery()
                .eq(RecipeStep::getRecipeId, recipeId)
                .orderByAsc(RecipeStep::getStepNo)
                .list();
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<RecipeStep> create(RecipeStep entity) {
        if (!StringUtils.hasText(entity.getRecipeId())) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        if (entity.getStepNo() == null) {
            return ApiResponse.fail("步骤序号不能为空");
        }
        if (lambdaQuery()
                .eq(RecipeStep::getRecipeId, entity.getRecipeId())
                .eq(RecipeStep::getStepNo, entity.getStepNo())
                .exists()) {
            return ApiResponse.fail("该菜谱下步骤序号已存在");
        }
        save(entity);
        return ApiResponse.success("保存成功", entity);
    }

    @Override
    public ApiResponse<Void> update(RecipeStep entity) {
        if (!StringUtils.hasText(entity.getId())) {
            return ApiResponse.fail("步骤id不能为空");
        }
        RecipeStep existing = super.getById(entity.getId());
        if (existing == null) {
            return ApiResponse.fail("步骤不存在");
        }
        if (entity.getStepNo() != null
                && lambdaQuery()
                .ne(RecipeStep::getId, entity.getId())
                .eq(RecipeStep::getRecipeId, existing.getRecipeId())
                .eq(RecipeStep::getStepNo, entity.getStepNo())
                .exists()) {
            return ApiResponse.fail("该菜谱下步骤序号已存在");
        }
        updateById(entity);
        return ApiResponse.success("更新成功", null);
    }

    @Override
    public ApiResponse<Void> delete(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("步骤id不能为空");
        }
        if (super.getById(id) == null) {
            return ApiResponse.fail("步骤不存在");
        }
        removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> saveBatchByRecipeId(RecipeStepBatchDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getRecipeId())) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        ApiResponse<Void> validateResult = validateStepList(dto.getStepList());
        if (validateResult != null) {
            return validateResult;
        }
        doSaveBatchByRecipeId(dto.getRecipeId(), dto.getStepList());
        return ApiResponse.success("保存成功", null);
    }

    @Override
    public void deleteByRecipeId(String recipeId) {
        if (!StringUtils.hasText(recipeId)) {
            return;
        }
        remove(new LambdaQueryWrapper<RecipeStep>().eq(RecipeStep::getRecipeId, recipeId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBatchByRecipeId(String recipeId, List<RecipeStep> stepList) {
        if (!StringUtils.hasText(recipeId)) {
            return;
        }
        doSaveBatchByRecipeId(recipeId, stepList);
    }

    private void doSaveBatchByRecipeId(String recipeId, List<RecipeStep> stepList) {
        remove(new LambdaQueryWrapper<RecipeStep>().eq(RecipeStep::getRecipeId, recipeId));
        if (stepList != null) {
            for (RecipeStep step : stepList) {
                step.setId(null);
                step.setRecipeId(recipeId);
                save(step);
            }
        }
    }

    private ApiResponse<Void> validateStepList(List<RecipeStep> stepList) {
        if (stepList == null) {
            return null;
        }
        Set<Integer> stepNos = new HashSet<>();
        for (RecipeStep step : stepList) {
            if (step.getStepNo() == null) {
                return ApiResponse.fail("步骤序号不能为空");
            }
            if (!stepNos.add(step.getStepNo())) {
                return ApiResponse.fail("步骤序号不能重复");
            }
        }
        return null;
    }
}
