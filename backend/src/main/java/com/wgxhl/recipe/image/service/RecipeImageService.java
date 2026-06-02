package com.wgxhl.recipe.image.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.image.dto.RecipeImageBatchDTO;
import com.wgxhl.recipe.image.entity.RecipeImage;

import java.util.List;

public interface RecipeImageService extends IService<RecipeImage> {

    ApiResponse<List<RecipeImage>> listByRecipeId(String recipeId);

    ApiResponse<RecipeImage> create(RecipeImage entity);

    ApiResponse<Void> update(RecipeImage entity);

    ApiResponse<Void> delete(String id);

    ApiResponse<Void> saveBatchByRecipeId(RecipeImageBatchDTO dto);

    void deleteByRecipeId(String recipeId);

    void saveBatchByRecipeId(String recipeId, List<RecipeImage> imageList);
}
