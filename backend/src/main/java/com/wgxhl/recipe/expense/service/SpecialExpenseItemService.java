package com.wgxhl.recipe.expense.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.expense.dto.SpecialExpenseItemPageDTO;
import com.wgxhl.recipe.expense.entity.SpecialExpenseItem;
import com.wgxhl.recipe.user.entity.AppUser;

public interface SpecialExpenseItemService extends IService<SpecialExpenseItem> {

    ApiResponse<Page<SpecialExpenseItem>> page(SpecialExpenseItemPageDTO dto, AppUser actor);

    ApiResponse<SpecialExpenseItem> detail(String id, AppUser actor);

    ApiResponse<SpecialExpenseItem> create(SpecialExpenseItem entity, AppUser actor);

    ApiResponse<Void> update(SpecialExpenseItem entity, AppUser actor);

    ApiResponse<Void> delete(String id, AppUser actor);
}
