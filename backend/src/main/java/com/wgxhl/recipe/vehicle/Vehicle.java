package com.wgxhl.recipe.vehicle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("vehicle")
public class Vehicle {
    @TableId(type = IdType.INPUT)
    private String id;
    private String name;
    private String vehicleType;
    private String model;
    private LocalDate ownedSince;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String imagePath;
    private String createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
