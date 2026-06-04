package com.wgxhl.recipe.want.dto;

import com.wgxhl.recipe.common.dto.PageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class WantedRecipePageDTO extends PageDTO {

    /*
     * 用户id
     */
    private String userId;

    /*
     * 菜谱名称
     */
    private String recipeName;

    /*
     * 想吃日期
     */
    private LocalDate plannedDate;

    /*
     * 想吃日期下限（含，用于筛选该日及之后）
     */
    private LocalDate plannedDateStart;
}
