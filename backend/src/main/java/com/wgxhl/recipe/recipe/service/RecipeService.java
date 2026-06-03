package com.wgxhl.recipe.recipe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.recipe.dto.RecipePageDTO;
import com.wgxhl.recipe.recipe.dto.RecipeSaveDTO;
import com.wgxhl.recipe.recipe.entity.Recipe;
import com.wgxhl.recipe.recipe.vo.RecipeDetailVO;
import com.wgxhl.recipe.user.entity.AppUser;

public interface RecipeService extends IService<Recipe> {

    ApiResponse<Page<Recipe>> page(RecipePageDTO dto);

    ApiResponse<RecipeDetailVO> getDetailById(String id);

    ApiResponse<RecipeDetailVO> create(RecipeSaveDTO dto, AppUser creator);

    ApiResponse<Void> update(RecipeSaveDTO dto);

    ApiResponse<Void> delete(String id);
}
