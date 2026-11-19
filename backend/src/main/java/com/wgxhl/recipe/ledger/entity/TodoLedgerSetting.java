package com.wgxhl.recipe.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("todo_ledger_setting")
public class TodoLedgerSetting {
    @TableId(value = "todo_id", type = IdType.INPUT)
    private String todoId;
    private Boolean enabled;
    private String category;
    private String description;
    private BigDecimal estimatedAmount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
