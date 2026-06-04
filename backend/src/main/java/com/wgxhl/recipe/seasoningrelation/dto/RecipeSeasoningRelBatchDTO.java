package com.wgxhl.recipe.seasoningrelation.dto;

import com.wgxhl.recipe.seasoningrelation.entity.RecipeSeasoningRel;
import lombok.Data;

import java.util.List;

@Data
public class RecipeSeasoningRelBatchDTO {

    /*
     * 菜谱id
     */
    private String recipeId;

    /*
     * 调料关系列表
     */
    private List<RecipeSeasoningRel> relList;
}
