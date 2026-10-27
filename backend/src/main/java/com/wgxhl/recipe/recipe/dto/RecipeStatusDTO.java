package com.wgxhl.recipe.recipe.dto;

import lombok.Data;

@Data
public class RecipeStatusDTO {

    private String id;

    /**
     * 上架 / 下架
     */
    private String status;
}
