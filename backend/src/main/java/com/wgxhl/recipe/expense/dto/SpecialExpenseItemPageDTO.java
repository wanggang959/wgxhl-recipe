package com.wgxhl.recipe.expense.dto;

import com.wgxhl.recipe.common.dto.PageDTO;
import lombok.Data;

@Data
public class SpecialExpenseItemPageDTO extends PageDTO {

    private String projectId;

    private String keyword;

    private String category;

    private String status;
}
