package com.wgxhl.recipe.ingredient.dto;

import com.wgxhl.recipe.common.dto.PageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class IngredientPageDTO extends PageDTO {

    /*
     * 食材名称
     */
    private String ingredientName;
}
