package com.wgxhl.recipe.image.controller;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.dto.IdDTO;
import com.wgxhl.recipe.common.dto.RecipeIdDTO;
import com.wgxhl.recipe.image.dto.RecipeImageBatchDTO;
import com.wgxhl.recipe.image.entity.RecipeImage;
import com.wgxhl.recipe.image.service.RecipeImageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/recipeImage")
public class RecipeImageController {

    private final RecipeImageService recipeImageService;

    public RecipeImageController(RecipeImageService recipeImageService) {
        this.recipeImageService = recipeImageService;
    }

    /***
     * @Description 根据菜谱id查询图片列表
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/listByRecipeId")
    public ApiResponse<List<RecipeImage>> listByRecipeId(@RequestBody RecipeIdDTO dto) {
        return recipeImageService.listByRecipeId(dto.getRecipeId());
    }

    /***
     * @Description 新增菜谱图片
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/create")
    public ApiResponse<RecipeImage> create(@RequestBody RecipeImage entity) {
        return recipeImageService.create(entity);
    }

    /***
     * @Description 更新菜谱图片
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/update")
    public ApiResponse<Void> update(@RequestBody RecipeImage entity) {
        return recipeImageService.update(entity);
    }

    /***
     * @Description 删除菜谱图片
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody IdDTO dto) {
        return recipeImageService.delete(dto.getId());
    }

    /***
     * @Description 批量保存菜谱图片
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/saveBatchByRecipeId")
    public ApiResponse<Void> saveBatchByRecipeId(@RequestBody RecipeImageBatchDTO dto) {
        return recipeImageService.saveBatchByRecipeId(dto);
    }
}
