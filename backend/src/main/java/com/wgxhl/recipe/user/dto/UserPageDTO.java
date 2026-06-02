package com.wgxhl.recipe.user.dto;

import com.wgxhl.recipe.common.dto.PageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageDTO extends PageDTO {

    /*
     * 用户名
     */
    private String username;

    /*
     * 昵称
     */
    private String nickname;

    /*
     * 用户角色
     */
    private String userRole;

    /*
     * 状态
     */
    private String status;
}
