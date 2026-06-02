package com.wgxhl.recipe.record.dto;

import com.wgxhl.recipe.common.dto.PageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ViewRecordPageDTO extends PageDTO {

    /*
     * 用户id
     */
    private String userId;

    /*
     * 菜谱名称
     */
    private String recipeName;
}
