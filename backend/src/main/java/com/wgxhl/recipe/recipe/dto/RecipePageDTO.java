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

    /*
     * 列表范围：family-家庭（默认），public-公共
     */
    private String scope;

    /*
     * 排序：latest-最新（默认），likes_desc-赞多优先，likes_asc-赞少优先，random-随机
     */
    private String sort;
}
