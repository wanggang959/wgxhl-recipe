package com.wgxhl.recipe.ingredient.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.ingredient.dto.IngredientPageDTO;
import com.wgxhl.recipe.ingredient.entity.Ingredient;

public interface IngredientService extends IService<Ingredient> {

    ApiResponse<Page<Ingredient>> page(IngredientPageDTO dto);

    ApiResponse<Ingredient> getById(String id);

    ApiResponse<Ingredient> create(Ingredient entity);

    ApiResponse<Void> update(Ingredient entity);

    ApiResponse<Void> delete(String id);
}
