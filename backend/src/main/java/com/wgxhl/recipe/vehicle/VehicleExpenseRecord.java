package com.wgxhl.recipe.vehicle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("vehicle_expense_record")
public class VehicleExpenseRecord {
    @TableId(type = IdType.INPUT)
    private String id;
    private String vehicleId;
    private LocalDateTime occurredAt;
    private String category;
    private BigDecimal amount;
    private String remark;
    private String importFingerprint;
    private String createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableField(exist = false) private Boolean includeLedger;
    @TableField(exist = false) private String payerId;
    @TableField(exist = false) private String categoryKey;
}
