package com.wgxhl.recipe.category.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wgxhl.recipe.category.dto.RecipeCategoryPageDTO;
import com.wgxhl.recipe.category.entity.RecipeCategory;
import com.wgxhl.recipe.category.service.RecipeCategoryService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.dto.IdDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/category")
public class RecipeCategoryController {

    private final RecipeCategoryService recipeCategoryService;

    public RecipeCategoryController(RecipeCategoryService recipeCategoryService) {
        this.recipeCategoryService = recipeCategoryService;
    }

    /***
     * @Description 分页查询分类
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/page")
    public ApiResponse<Page<RecipeCategory>> page(@RequestBody RecipeCategoryPageDTO dto) {
        return recipeCategoryService.page(dto);
    }

    /***
     * @Description 根据id查询分类
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/getById")
    public ApiResponse<RecipeCategory> getById(@RequestBody IdDTO dto) {
        return recipeCategoryService.getById(dto.getId());
    }

    /***
     * @Description 新增分类
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/create")
    public ApiResponse<RecipeCategory> create(@RequestBody RecipeCategory entity) {
        return recipeCategoryService.create(entity);
    }

    /***
     * @Description 更新分类
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/update")
    public ApiResponse<Void> update(@RequestBody RecipeCategory entity) {
        return recipeCategoryService.update(entity);
    }

    /***
     * @Description 删除分类
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody IdDTO dto) {
        return recipeCategoryService.delete(dto.getId());
    }
}
