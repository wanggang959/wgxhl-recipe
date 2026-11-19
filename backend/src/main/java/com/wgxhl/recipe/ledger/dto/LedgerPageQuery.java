package com.wgxhl.recipe.ledger.dto;

import lombok.Data;

@Data
public class LedgerPageQuery {
    private long current = 1;
    private long size = 20;
    private String month;
    private String entryType;
    private String category;
    private String payerId;
    private String sourceTodoId;
    private boolean allMonths;
}
