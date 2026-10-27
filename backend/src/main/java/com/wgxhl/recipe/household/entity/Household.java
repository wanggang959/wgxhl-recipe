package com.wgxhl.recipe.household.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("household")
public class Household {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /*
     * 家庭名称
     */
    private String householdName;

    /*
     * 家庭创建者用户id
     */
    private String ownerUserId;

    /*
     * 状态：normal-正常，disabled-停用
     */
    private String status;

    /*
     * 备注
     */
    private String remark;

    /*
     * 创建时间
     */
    private LocalDateTime createTime;

    /*
     * 更新时间
     */
    private LocalDateTime updateTime;
}
