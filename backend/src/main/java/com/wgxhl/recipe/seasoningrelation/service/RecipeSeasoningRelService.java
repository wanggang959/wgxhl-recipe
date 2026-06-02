package com.wgxhl.recipe.seasoningrelation.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.seasoningrelation.dto.RecipeSeasoningRelBatchDTO;
import com.wgxhl.recipe.seasoningrelation.entity.RecipeSeasoningRel;

import java.util.List;

public interface RecipeSeasoningRelService extends IService<RecipeSeasoningRel> {

    ApiResponse<List<RecipeSeasoningRel>> listByRecipeId(String recipeId);

    ApiResponse<RecipeSeasoningRel> create(RecipeSeasoningRel entity);

    ApiResponse<Void> update(RecipeSeasoningRel entity);

    ApiResponse<Void> delete(String id);

    ApiResponse<Void> saveBatchByRecipeId(RecipeSeasoningRelBatchDTO dto);

    void deleteByRecipeId(String recipeId);

    void saveBatchByRecipeId(String recipeId, List<RecipeSeasoningRel> relList);
}
