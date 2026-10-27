package com.wgxhl.recipe.user.dto;

import lombok.Data;

@Data
public class RegisterEmailDTO {

    private String email;

    private String code;

    private String username;

    private String password;

    private String nickname;

    private String inviteCode;
}
