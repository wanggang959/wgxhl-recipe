package com.wgxhl.recipe.relation.dto;

import com.wgxhl.recipe.relation.entity.RecipeIngredientRel;
import lombok.Data;

import java.util.List;

@Data
public class RecipeIngredientRelBatchDTO {

    /*
     * 菜谱id
     */
    private String recipeId;

    /*
     * 食材关系列表
     */
    private List<RecipeIngredientRel> relList;
}
