package com.wgxhl.recipe.user.dto;

import lombok.Data;

@Data
public class AccountSecurityDTO {
    private String userId;
    private String email;
    private String newEmail;
    private String currentPassword;
    private String newPassword;
    private String token;
    private String requestId;
    private String oldCode;
    private String newCode;
}
