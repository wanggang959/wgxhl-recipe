package com.wgxhl.recipe.want.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.want.dto.WantedRecipePageDTO;
import com.wgxhl.recipe.want.entity.UserWantedRecipe;

import java.time.LocalDate;
import java.util.List;

public interface UserWantedRecipeService extends IService<UserWantedRecipe> {

    ApiResponse<Page<UserWantedRecipe>> page(WantedRecipePageDTO dto);

    ApiResponse<List<LocalDate>> dateList(String userId);

    ApiResponse<UserWantedRecipe> create(UserWantedRecipe entity);

    ApiResponse<Void> updatePlannedDate(UserWantedRecipe entity);

    ApiResponse<Void> delete(String id);

    ApiResponse<Void> deleteByRecipeId(String userId, String recipeId);

    ApiResponse<Boolean> check(String userId, String recipeId);

    void deleteByRecipeId(String recipeId);

    void deleteByUserId(String userId);
}
