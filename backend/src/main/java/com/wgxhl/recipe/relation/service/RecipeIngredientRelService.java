package com.wgxhl.recipe.relation.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.relation.dto.RecipeIngredientRelBatchDTO;
import com.wgxhl.recipe.relation.entity.RecipeIngredientRel;

import java.util.List;

public interface RecipeIngredientRelService extends IService<RecipeIngredientRel> {

    ApiResponse<List<RecipeIngredientRel>> listByRecipeId(String recipeId);

    ApiResponse<RecipeIngredientRel> create(RecipeIngredientRel entity);

    ApiResponse<Void> update(RecipeIngredientRel entity);

    ApiResponse<Void> delete(String id);

    ApiResponse<Void> saveBatchByRecipeId(RecipeIngredientRelBatchDTO dto);

    void deleteByRecipeId(String recipeId);

    void saveBatchByRecipeId(String recipeId, List<RecipeIngredientRel> relList);
}
