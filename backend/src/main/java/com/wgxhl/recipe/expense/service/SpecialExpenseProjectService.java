package com.wgxhl.recipe.expense.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.expense.dto.SpecialExpenseProjectPageDTO;
import com.wgxhl.recipe.expense.dto.SpecialExpenseSummaryDTO;
import com.wgxhl.recipe.expense.entity.SpecialExpenseProject;
import com.wgxhl.recipe.user.entity.AppUser;

public interface SpecialExpenseProjectService extends IService<SpecialExpenseProject> {

    ApiResponse<Page<SpecialExpenseProject>> page(SpecialExpenseProjectPageDTO dto, AppUser actor);

    ApiResponse<SpecialExpenseProject> detail(String id, AppUser actor);

    ApiResponse<SpecialExpenseProject> create(SpecialExpenseProject entity, AppUser actor);

    ApiResponse<Void> update(SpecialExpenseProject entity, AppUser actor);

    ApiResponse<Void> archive(String id, AppUser actor);

    ApiResponse<Void> restore(String id, AppUser actor);

    ApiResponse<Void> delete(String id, AppUser actor);

    ApiResponse<SpecialExpenseSummaryDTO> summary(String projectId, AppUser actor);
}
