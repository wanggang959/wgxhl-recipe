package com.wgxhl.recipe.seasoning.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.seasoning.dto.SeasoningPageDTO;
import com.wgxhl.recipe.seasoning.entity.Seasoning;

public interface SeasoningService extends IService<Seasoning> {

    ApiResponse<Page<Seasoning>> page(SeasoningPageDTO dto);

    ApiResponse<Seasoning> getById(String id);

    ApiResponse<Seasoning> create(Seasoning entity);

    ApiResponse<Void> update(Seasoning entity);

    ApiResponse<Void> delete(String id);
}
