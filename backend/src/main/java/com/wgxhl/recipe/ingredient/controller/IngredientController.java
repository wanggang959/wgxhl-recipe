package com.wgxhl.recipe.ingredient.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.dto.IdDTO;
import com.wgxhl.recipe.ingredient.dto.IngredientPageDTO;
import com.wgxhl.recipe.ingredient.entity.Ingredient;
import com.wgxhl.recipe.ingredient.service.IngredientService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ingredient")
public class IngredientController {

    private final IngredientService ingredientService;

    public IngredientController(IngredientService ingredientService) {
        this.ingredientService = ingredientService;
    }

    /***
     * @Description 分页查询食材
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/page")
    public ApiResponse<Page<Ingredient>> page(@RequestBody IngredientPageDTO dto) {
        return ingredientService.page(dto);
    }

    /***
     * @Description 根据id查询食材
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/getById")
    public ApiResponse<Ingredient> getById(@RequestBody IdDTO dto) {
        return ingredientService.getById(dto.getId());
    }

    /***
     * @Description 新增食材
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/create")
    public ApiResponse<Ingredient> create(@RequestBody Ingredient entity) {
        return ingredientService.create(entity);
    }

    /***
     * @Description 更新食材
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/update")
    public ApiResponse<Void> update(@RequestBody Ingredient entity) {
        return ingredientService.update(entity);
    }

    /***
     * @Description 删除食材
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody IdDTO dto) {
        return ingredientService.delete(dto.getId());
    }
}
