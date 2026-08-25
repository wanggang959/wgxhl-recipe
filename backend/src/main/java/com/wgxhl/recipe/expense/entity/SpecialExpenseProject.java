package com.wgxhl.recipe.expense.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("special_expense_project")
public class SpecialExpenseProject {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String projectName;

    private String projectType;

    private BigDecimal totalBudget;

    private String status;

    private LocalDate startDate;

    private LocalDate endDate;

    private Boolean archived;

    private String remark;

    private String createUserId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableField(exist = false)
    private BigDecimal actualAmount;

    @TableField(exist = false)
    private BigDecimal remainingBudget;

    @TableField(exist = false)
    private Long itemCount;

    @TableField(exist = false)
    private Long pendingCount;

    @TableField(exist = false)
    private Long doneCount;
}
