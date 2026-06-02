package com.wgxhl.recipe.recipe.vo;

import com.wgxhl.recipe.image.entity.RecipeImage;
import com.wgxhl.recipe.recipe.entity.Recipe;
import com.wgxhl.recipe.relation.entity.RecipeIngredientRel;
import com.wgxhl.recipe.seasoningrelation.entity.RecipeSeasoningRel;
import com.wgxhl.recipe.step.entity.RecipeStep;
import lombok.Data;

import java.util.List;

@Data
public class RecipeDetailVO {

    /*
     * 菜谱信息
     */
    private Recipe recipe;

    /*
     * 制作步骤列表
     */
    private List<RecipeStep> recipeStepList;

    /*
     * 食材关系列表
     */
    private List<RecipeIngredientRel> ingredientList;

    /*
     * 调料关系列表
     */
    private List<RecipeSeasoningRel> seasoningList;

    /*
     * 图片列表
     */
    private List<RecipeImage> imageList;
}
