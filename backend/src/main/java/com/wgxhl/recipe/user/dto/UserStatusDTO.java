package com.wgxhl.recipe.user.dto;

import lombok.Data;

@Data
public class UserStatusDTO {

    /**
     * 用户id
     */
    private String id;

    /**
     * 状态：normal-正常，disabled-禁用
     */
    private String status;
}
