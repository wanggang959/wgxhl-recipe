package com.wgxhl.recipe.expense.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.dto.IdDTO;
import com.wgxhl.recipe.config.AuthRequestAttributes;
import com.wgxhl.recipe.expense.dto.SpecialExpenseItemPageDTO;
import com.wgxhl.recipe.expense.entity.SpecialExpenseItem;
import com.wgxhl.recipe.expense.service.SpecialExpenseItemService;
import com.wgxhl.recipe.user.entity.AppUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/expense/item")
public class SpecialExpenseItemController {

    private final SpecialExpenseItemService itemService;

    public SpecialExpenseItemController(SpecialExpenseItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping("/page")
    public ApiResponse<Page<SpecialExpenseItem>> page(@RequestBody SpecialExpenseItemPageDTO dto,
                                                      HttpServletRequest request) {
        return itemService.page(dto, currentUser(request));
    }

    @PostMapping("/getById")
    public ApiResponse<SpecialExpenseItem> getById(@RequestBody IdDTO dto, HttpServletRequest request) {
        return itemService.detail(dto.getId(), currentUser(request));
    }

    @PostMapping("/create")
    public ApiResponse<SpecialExpenseItem> create(@RequestBody SpecialExpenseItem entity,
                                                  HttpServletRequest request) {
        return itemService.create(entity, currentUser(request));
    }

    @PostMapping("/update")
    public ApiResponse<Void> update(@RequestBody SpecialExpenseItem entity, HttpServletRequest request) {
        return itemService.update(entity, currentUser(request));
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody IdDTO dto, HttpServletRequest request) {
        return itemService.delete(dto.getId(), currentUser(request));
    }

    private AppUser currentUser(HttpServletRequest request) {
        Object value = request.getAttribute(AuthRequestAttributes.CURRENT_USER);
        return value instanceof AppUser ? (AppUser) value : null;
    }
}
