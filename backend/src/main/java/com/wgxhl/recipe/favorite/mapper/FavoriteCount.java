package com.wgxhl.recipe.favorite.mapper;

import lombok.Data;

@Data
public class FavoriteCount {
    private String recipeId;
    private Long favoriteCount;
}
