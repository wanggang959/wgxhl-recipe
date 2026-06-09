package com.wgxhl.recipe.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String userId;

    private String title;

    private String content;

    private String notificationType;

    private String relatedId;

    private Boolean isRead;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
