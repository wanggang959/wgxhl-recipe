package com.wgxhl.recipe.mail.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mail_delivery_event")
public class MailDeliveryEvent {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String eventId;

    private String messageId;

    private String event;

    private String recipient;

    private String bounceType;

    private String reason;

    private Long occurredAt;

    private Long templateId;

    private LocalDateTime createTime;
}
