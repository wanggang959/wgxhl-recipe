package com.wgxhl.recipe.record.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.record.dto.ViewRecordCreateDTO;
import com.wgxhl.recipe.record.dto.ViewRecordPageDTO;
import com.wgxhl.recipe.record.entity.RecipeViewRecord;

public interface RecipeViewRecordService extends IService<RecipeViewRecord> {

    ApiResponse<Page<RecipeViewRecord>> page(ViewRecordPageDTO dto);

    ApiResponse<RecipeViewRecord> create(ViewRecordCreateDTO dto);

    ApiResponse<Void> delete(String id);

    ApiResponse<Void> clearByUserId(String userId);

    void deleteByRecipeId(String recipeId);

    void deleteByUserId(String userId);
}
