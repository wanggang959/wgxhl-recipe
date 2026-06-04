package com.wgxhl.recipe.push.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_push_subscription")
public class UserPushSubscription {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String userId;

    private String endpoint;

    private String p256dh;

    private String auth;

    private String userAgent;

    private Boolean enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
