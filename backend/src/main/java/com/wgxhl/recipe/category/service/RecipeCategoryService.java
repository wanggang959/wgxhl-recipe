package com.wgxhl.recipe.category.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.category.dto.RecipeCategoryPageDTO;
import com.wgxhl.recipe.category.entity.RecipeCategory;
import com.wgxhl.recipe.common.ApiResponse;

public interface RecipeCategoryService extends IService<RecipeCategory> {

    ApiResponse<Page<RecipeCategory>> page(RecipeCategoryPageDTO dto);

    ApiResponse<RecipeCategory> getById(String id);

    ApiResponse<RecipeCategory> create(RecipeCategory entity);

    ApiResponse<Void> update(RecipeCategory entity);

    ApiResponse<Void> delete(String id);
}
