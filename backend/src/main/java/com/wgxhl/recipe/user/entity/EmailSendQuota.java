package com.wgxhl.recipe.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("email_send_quota")
public class EmailSendQuota {
    private String id;
    private String bucketType;
    private String bucketHash;
    private LocalDate quotaDate;
    private Integer usedCount;
    private LocalDateTime lastSentAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
