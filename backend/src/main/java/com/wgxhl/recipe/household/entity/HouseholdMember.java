package com.wgxhl.recipe.household.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("household_member")
public class HouseholdMember {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /*
     * 家庭id
     */
    private String householdId;

    /*
     * 用户id
     */
    private String userId;

    /*
     * 家庭角色：owner-所有者，admin-管理员，member-成员，guest-游客
     */
    private String memberRole;

    /*
     * 状态：normal-正常，disabled-停用
     */
    private String status;

    /*
     * 创建时间
     */
    private LocalDateTime createTime;

    /*
     * 更新时间
     */
    private LocalDateTime updateTime;
}
