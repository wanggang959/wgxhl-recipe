package com.wgxhl.recipe.step.dto;

import com.wgxhl.recipe.step.entity.RecipeStep;
import lombok.Data;

import java.util.List;

@Data
public class RecipeStepBatchDTO {

    /*
     * 菜谱id
     */
    private String recipeId;

    /*
     * 步骤列表
     */
    private List<RecipeStep> stepList;
}
