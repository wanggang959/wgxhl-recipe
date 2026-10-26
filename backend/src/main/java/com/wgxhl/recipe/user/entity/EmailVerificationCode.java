package com.wgxhl.recipe.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("email_verification_code")
public class EmailVerificationCode {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String email;

    private String code;

    private String purpose;

    private LocalDateTime expiresAt;

    private Boolean used;

    private Integer attempts;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
