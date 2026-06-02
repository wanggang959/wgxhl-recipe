package com.wgxhl.recipe.record.dto;

import lombok.Data;

@Data
public class ViewRecordCreateDTO {

    /*
     * 用户id
     */
    private String userId;

    /*
     * 菜谱id
     */
    private String recipeId;
}
