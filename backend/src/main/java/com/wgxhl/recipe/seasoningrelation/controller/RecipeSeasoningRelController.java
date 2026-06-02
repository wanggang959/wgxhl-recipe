package com.wgxhl.recipe.seasoningrelation.controller;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.dto.IdDTO;
import com.wgxhl.recipe.common.dto.RecipeIdDTO;
import com.wgxhl.recipe.seasoningrelation.dto.RecipeSeasoningRelBatchDTO;
import com.wgxhl.recipe.seasoningrelation.entity.RecipeSeasoningRel;
import com.wgxhl.recipe.seasoningrelation.service.RecipeSeasoningRelService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/recipeSeasoningRel")
public class RecipeSeasoningRelController {

    private final RecipeSeasoningRelService recipeSeasoningRelService;

    public RecipeSeasoningRelController(RecipeSeasoningRelService recipeSeasoningRelService) {
        this.recipeSeasoningRelService = recipeSeasoningRelService;
    }

    @PostMapping("/listByRecipeId")
    public ApiResponse<List<RecipeSeasoningRel>> listByRecipeId(@RequestBody RecipeIdDTO dto) {
        return recipeSeasoningRelService.listByRecipeId(dto.getRecipeId());
    }

    @PostMapping("/create")
    public ApiResponse<RecipeSeasoningRel> create(@RequestBody RecipeSeasoningRel entity) {
        return recipeSeasoningRelService.create(entity);
    }

    @PostMapping("/update")
    public ApiResponse<Void> update(@RequestBody RecipeSeasoningRel entity) {
        return recipeSeasoningRelService.update(entity);
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody IdDTO dto) {
        return recipeSeasoningRelService.delete(dto.getId());
    }

    @PostMapping("/saveBatchByRecipeId")
    public ApiResponse<Void> saveBatchByRecipeId(@RequestBody RecipeSeasoningRelBatchDTO dto) {
        return recipeSeasoningRelService.saveBatchByRecipeId(dto);
    }
}
