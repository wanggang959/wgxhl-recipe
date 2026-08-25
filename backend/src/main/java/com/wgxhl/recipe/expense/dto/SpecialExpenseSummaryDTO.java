package com.wgxhl.recipe.expense.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SpecialExpenseSummaryDTO {

    private BigDecimal totalBudget = BigDecimal.ZERO;

    private BigDecimal actualAmount = BigDecimal.ZERO;

    private BigDecimal remainingBudget = BigDecimal.ZERO;

    private Long itemCount = 0L;

    private Long pendingCount = 0L;

    private Long doneCount = 0L;

    private Long overBudgetCount = 0L;
}
