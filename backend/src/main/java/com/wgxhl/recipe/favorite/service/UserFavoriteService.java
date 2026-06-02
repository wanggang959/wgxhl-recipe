package com.wgxhl.recipe.favorite.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.favorite.dto.FavoritePageDTO;
import com.wgxhl.recipe.favorite.entity.UserFavorite;

public interface UserFavoriteService extends IService<UserFavorite> {

    ApiResponse<Page<UserFavorite>> page(FavoritePageDTO dto);

    ApiResponse<UserFavorite> create(UserFavorite entity);

    ApiResponse<Void> delete(String id);

    ApiResponse<Void> deleteByRecipeId(String userId, String recipeId);

    ApiResponse<Boolean> check(String userId, String recipeId);

    void deleteByRecipeId(String recipeId);

    void deleteByUserId(String userId);
}
