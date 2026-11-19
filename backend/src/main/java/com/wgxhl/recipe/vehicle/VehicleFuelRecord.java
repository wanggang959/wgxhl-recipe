package com.wgxhl.recipe.vehicle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("vehicle_fuel_record")
public class VehicleFuelRecord {
    @TableId(type = IdType.INPUT)
    private String id;
    private String vehicleId;
    private LocalDateTime occurredAt;
    private BigDecimal mileage;
    private BigDecimal unitPrice;
    private BigDecimal liters;
    private BigDecimal displayedAmount;
    private BigDecimal paidAmount;
    private String fuelGrade;
    private Boolean fullTank;
    private Boolean warningLight;
    private Boolean missedPrevious;
    private String station;
    private String remark;
    private String importFingerprint;
    private String createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableField(exist = false) private Boolean includeLedger;
    @TableField(exist = false) private String payerId;
}
