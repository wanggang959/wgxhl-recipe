package com.wgxhl.recipe.category.dto;

import com.wgxhl.recipe.common.dto.PageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RecipeCategoryPageDTO extends PageDTO {

    /*
     * 分类名称
     */
    private String categoryName;

    /*
     * 分类编码
     */
    private String categoryCode;
}
