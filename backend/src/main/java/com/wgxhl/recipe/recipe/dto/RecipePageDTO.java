package com.wgxhl.recipe.recipe.dto;

import com.wgxhl.recipe.common.dto.PageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RecipePageDTO extends PageDTO {

    /*
     * 菜谱名称
     */
    private String recipeName;

    /*
     * 分类id
     */
    private String categoryId;

    /*
     * 分类名称
     */
    private String categoryName;

    /*
     * 难度
     */
    private String difficulty;

    /*
     * 口味
     */
    private String taste;

    /*
     * 状态
     */
    private String status;
}
