package com.wgxhl.recipe.step.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.step.dto.RecipeStepBatchDTO;
import com.wgxhl.recipe.step.entity.RecipeStep;

import java.util.List;

public interface RecipeStepService extends IService<RecipeStep> {

    ApiResponse<List<RecipeStep>> listByRecipeId(String recipeId);

    ApiResponse<RecipeStep> create(RecipeStep entity);

    ApiResponse<Void> update(RecipeStep entity);

    ApiResponse<Void> delete(String id);

    ApiResponse<Void> saveBatchByRecipeId(RecipeStepBatchDTO dto);

    void deleteByRecipeId(String recipeId);

    void saveBatchByRecipeId(String recipeId, List<RecipeStep> stepList);
}
