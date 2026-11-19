package com.wgxhl.recipe.ledger.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CompleteWithLedgerRequest {
    private String id;
    private LocalDateTime expectedDueTime;
    private String payerId;
    private BigDecimal actualAmount;
}
