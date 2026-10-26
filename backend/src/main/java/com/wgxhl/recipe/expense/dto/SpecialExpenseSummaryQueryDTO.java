package com.wgxhl.recipe.expense.dto;

import com.wgxhl.recipe.common.dto.IdDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SpecialExpenseSummaryQueryDTO extends IdDTO {

    private String category;

    private String status;
}
