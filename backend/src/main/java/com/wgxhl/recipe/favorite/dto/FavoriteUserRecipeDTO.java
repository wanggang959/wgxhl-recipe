package com.wgxhl.recipe.favorite.dto;

import lombok.Data;

@Data
public class FavoriteUserRecipeDTO {

    /*
     * 用户id
     */
    private String userId;

    /*
     * 菜谱id
     */
    private String recipeId;
}
