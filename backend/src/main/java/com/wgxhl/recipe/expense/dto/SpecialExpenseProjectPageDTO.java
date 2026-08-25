package com.wgxhl.recipe.expense.dto;

import com.wgxhl.recipe.common.dto.PageDTO;
import lombok.Data;

@Data
public class SpecialExpenseProjectPageDTO extends PageDTO {

    private String keyword;

    private String status;

    private Boolean archived;
}
