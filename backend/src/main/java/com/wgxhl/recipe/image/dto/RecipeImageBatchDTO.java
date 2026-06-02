package com.wgxhl.recipe.image.dto;

import com.wgxhl.recipe.image.entity.RecipeImage;
import lombok.Data;

import java.util.List;

@Data
public class RecipeImageBatchDTO {

    /*
     * 菜谱id
     */
    private String recipeId;

    /*
     * 图片列表
     */
    private List<RecipeImage> imageList;
}
