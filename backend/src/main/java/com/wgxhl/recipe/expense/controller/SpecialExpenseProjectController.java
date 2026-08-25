package com.wgxhl.recipe.expense.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.dto.IdDTO;
import com.wgxhl.recipe.config.AuthRequestAttributes;
import com.wgxhl.recipe.expense.dto.SpecialExpenseProjectPageDTO;
import com.wgxhl.recipe.expense.dto.SpecialExpenseSummaryDTO;
import com.wgxhl.recipe.expense.entity.SpecialExpenseProject;
import com.wgxhl.recipe.expense.service.SpecialExpenseProjectService;
import com.wgxhl.recipe.user.entity.AppUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/expense/project")
public class SpecialExpenseProjectController {

    private final SpecialExpenseProjectService projectService;

    public SpecialExpenseProjectController(SpecialExpenseProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping("/page")
    public ApiResponse<Page<SpecialExpenseProject>> page(@RequestBody SpecialExpenseProjectPageDTO dto,
                                                         HttpServletRequest request) {
        return projectService.page(dto, currentUser(request));
    }

    @PostMapping("/getById")
    public ApiResponse<SpecialExpenseProject> getById(@RequestBody IdDTO dto, HttpServletRequest request) {
        return projectService.detail(dto.getId(), currentUser(request));
    }

    @PostMapping("/create")
    public ApiResponse<SpecialExpenseProject> create(@RequestBody SpecialExpenseProject entity,
                                                     HttpServletRequest request) {
        return projectService.create(entity, currentUser(request));
    }

    @PostMapping("/update")
    public ApiResponse<Void> update(@RequestBody SpecialExpenseProject entity, HttpServletRequest request) {
        return projectService.update(entity, currentUser(request));
    }

    @PostMapping("/archive")
    public ApiResponse<Void> archive(@RequestBody IdDTO dto, HttpServletRequest request) {
        return projectService.archive(dto.getId(), currentUser(request));
    }

    @PostMapping("/restore")
    public ApiResponse<Void> restore(@RequestBody IdDTO dto, HttpServletRequest request) {
        return projectService.restore(dto.getId(), currentUser(request));
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody IdDTO dto, HttpServletRequest request) {
        return projectService.delete(dto.getId(), currentUser(request));
    }

    @PostMapping("/summary")
    public ApiResponse<SpecialExpenseSummaryDTO> summary(@RequestBody IdDTO dto, HttpServletRequest request) {
        return projectService.summary(dto.getId(), currentUser(request));
    }

    private AppUser currentUser(HttpServletRequest request) {
        Object value = request.getAttribute(AuthRequestAttributes.CURRENT_USER);
        return value instanceof AppUser ? (AppUser) value : null;
    }
}
