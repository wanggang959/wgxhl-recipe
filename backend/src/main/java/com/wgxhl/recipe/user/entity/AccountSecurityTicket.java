package com.wgxhl.recipe.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("account_security_ticket")
public class AccountSecurityTicket {
    private String id;
    private String userId;
    private String purpose;
    private String requesterId;
    private String initiatorHouseholdId;
    private String tokenHash;
    private String codeOldHash;
    private String codeNewHash;
    private String originalEmail;
    private String newEmail;
    private Long issuedAuthVersion;
    private LocalDateTime expiresAt;
    private Integer attempts;
    private Boolean used;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
