package com.wgxhl.recipe.mail.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mail_message_log")
public class MailMessageLog {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String messageId;

    private String recipient;

    private String mailKind;

    private Long templateId;

    private LocalDateTime createTime;
}
