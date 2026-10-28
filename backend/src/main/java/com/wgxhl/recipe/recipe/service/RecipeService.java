package com.wgxhl.recipe.recipe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.recipe.dto.RecipePageDTO;
import com.wgxhl.recipe.recipe.dto.RecipeSaveDTO;
import com.wgxhl.recipe.recipe.entity.Recipe;
import com.wgxhl.recipe.recipe.vo.RecipeDetailVO;
import com.wgxhl.recipe.recipe.vo.RecipeLikeStateVO;
import com.wgxhl.recipe.user.entity.AppUser;
import java.util.List;

public interface RecipeService extends IService<Recipe> {

    ApiResponse<Page<Recipe>> page(RecipePageDTO dto);

    ApiResponse<List<Recipe>> batch(List<String> ids);

    ApiResponse<RecipeDetailVO> getDetailById(String id);

    boolean canViewRecipeDetail(Recipe recipe);

    ApiResponse<RecipeDetailVO> create(RecipeSaveDTO dto, AppUser creator);

    ApiResponse<Void> update(RecipeSaveDTO dto);

    ApiResponse<Void> delete(String id);

    ApiResponse<Void> setStatus(String id, String status);

    ApiResponse<RecipeLikeStateVO> toggleLike(String id);
}
