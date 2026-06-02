package com.wgxhl.recipe.recipe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.dto.IdDTO;
import com.wgxhl.recipe.recipe.dto.RecipePageDTO;
import com.wgxhl.recipe.recipe.dto.RecipeSaveDTO;
import com.wgxhl.recipe.recipe.entity.Recipe;
import com.wgxhl.recipe.recipe.service.RecipeService;
import com.wgxhl.recipe.recipe.vo.RecipeDetailVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recipe")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    /***
     * @Description 分页查询菜谱
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/page")
    public ApiResponse<Page<Recipe>> page(@RequestBody RecipePageDTO dto) {
        return recipeService.page(dto);
    }

    /***
     * @Description 根据id查询菜谱详情
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/getById")
    public ApiResponse<RecipeDetailVO> getById(@RequestBody IdDTO dto) {
        return recipeService.getDetailById(dto.getId());
    }

    /***
     * @Description 新增菜谱
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/create")
    public ApiResponse<RecipeDetailVO> create(@RequestBody RecipeSaveDTO dto) {
        return recipeService.create(dto);
    }

    /***
     * @Description 更新菜谱
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/update")
    public ApiResponse<Void> update(@RequestBody RecipeSaveDTO dto) {
        return recipeService.update(dto);
    }

    /***
     * @Description 删除菜谱
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody IdDTO dto) {
        return recipeService.delete(dto.getId());
    }
}
