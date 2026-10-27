package com.wgxhl.recipe.household.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("household_invite")
public class HouseholdInvite {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String householdId;

    private String inviteCode;

    private String memberRole;

    private Integer maxUses;

    private Integer usedCount;

    private LocalDateTime expiresAt;

    private String status;

    private String createdBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
