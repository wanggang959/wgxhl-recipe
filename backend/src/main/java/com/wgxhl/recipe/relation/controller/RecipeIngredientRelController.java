package com.wgxhl.recipe.relation.controller;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.dto.IdDTO;
import com.wgxhl.recipe.common.dto.RecipeIdDTO;
import com.wgxhl.recipe.relation.dto.RecipeIngredientRelBatchDTO;
import com.wgxhl.recipe.relation.entity.RecipeIngredientRel;
import com.wgxhl.recipe.relation.service.RecipeIngredientRelService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/recipeIngredient")
public class RecipeIngredientRelController {

    private final RecipeIngredientRelService recipeIngredientRelService;

    public RecipeIngredientRelController(RecipeIngredientRelService recipeIngredientRelService) {
        this.recipeIngredientRelService = recipeIngredientRelService;
    }

    /***
     * @Description 根据菜谱id查询食材关系列表
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/listByRecipeId")
    public ApiResponse<List<RecipeIngredientRel>> listByRecipeId(@RequestBody RecipeIdDTO dto) {
        return recipeIngredientRelService.listByRecipeId(dto.getRecipeId());
    }

    /***
     * @Description 新增菜谱食材关系
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/create")
    public ApiResponse<RecipeIngredientRel> create(@RequestBody RecipeIngredientRel entity) {
        return recipeIngredientRelService.create(entity);
    }

    /***
     * @Description 更新菜谱食材关系
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/update")
    public ApiResponse<Void> update(@RequestBody RecipeIngredientRel entity) {
        return recipeIngredientRelService.update(entity);
    }

    /***
     * @Description 删除菜谱食材关系
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody IdDTO dto) {
        return recipeIngredientRelService.delete(dto.getId());
    }

    /***
     * @Description 批量保存菜谱食材关系
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/saveBatchByRecipeId")
    public ApiResponse<Void> saveBatchByRecipeId(@RequestBody RecipeIngredientRelBatchDTO dto) {
        return recipeIngredientRelService.saveBatchByRecipeId(dto);
    }
}
