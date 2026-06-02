package com.wgxhl.recipe.step.controller;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.dto.IdDTO;
import com.wgxhl.recipe.common.dto.RecipeIdDTO;
import com.wgxhl.recipe.step.dto.RecipeStepBatchDTO;
import com.wgxhl.recipe.step.entity.RecipeStep;
import com.wgxhl.recipe.step.service.RecipeStepService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/recipeStep")
public class RecipeStepController {

    private final RecipeStepService recipeStepService;

    public RecipeStepController(RecipeStepService recipeStepService) {
        this.recipeStepService = recipeStepService;
    }

    /***
     * @Description 根据菜谱id查询步骤列表
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/listByRecipeId")
    public ApiResponse<List<RecipeStep>> listByRecipeId(@RequestBody RecipeIdDTO dto) {
        return recipeStepService.listByRecipeId(dto.getRecipeId());
    }

    /***
     * @Description 新增菜谱步骤
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/create")
    public ApiResponse<RecipeStep> create(@RequestBody RecipeStep entity) {
        return recipeStepService.create(entity);
    }

    /***
     * @Description 更新菜谱步骤
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/update")
    public ApiResponse<Void> update(@RequestBody RecipeStep entity) {
        return recipeStepService.update(entity);
    }

    /***
     * @Description 删除菜谱步骤
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody IdDTO dto) {
        return recipeStepService.delete(dto.getId());
    }

    /***
     * @Description 批量保存菜谱步骤
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/saveBatchByRecipeId")
    public ApiResponse<Void> saveBatchByRecipeId(@RequestBody RecipeStepBatchDTO dto) {
        return recipeStepService.saveBatchByRecipeId(dto);
    }
}
