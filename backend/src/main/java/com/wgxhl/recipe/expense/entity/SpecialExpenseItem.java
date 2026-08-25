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
@TableName("special_expense_item")
public class SpecialExpenseItem {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String projectId;

    private String itemName;

    private String category;

    private BigDecimal budgetAmount;

    private BigDecimal actualAmount;

    private String quantity;

    private String status;

    private String payerId;

    private String ownerId;

    private LocalDate purchaseDate;

    private LocalDateTime plannedTime;

    private String purchaseChannel;

    private String purchaseLink;

    private String remark;

    private String imageUrl;

    private String todoId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String projectName;

    @TableField(exist = false)
    private String payerName;

    @TableField(exist = false)
    private String ownerName;
}
