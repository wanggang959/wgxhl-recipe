package com.wgxhl.recipe.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("household_ledger_entry")
public class LedgerEntry {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String entryType;
    private String category;
    private String description;
    private BigDecimal amount;
    private LocalDate occurredOn;
    private String payerId;
    private String createdBy;
    private String remark;
    private String sourceType;
    private String sourceId;
    private String sourceCycle;
    private String sourceCycleLabel;
    private String relatedExpenseId;
    private Boolean deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
