package com.wgxhl.recipe.user.dto;

import lombok.Data;

@Data
public class RegisterInviteDTO {

    private String inviteCode;

    private String username;

    private String password;

    private String nickname;
}
