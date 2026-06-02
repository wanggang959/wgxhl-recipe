package com.wgxhl.recipe.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("app_user")
public class AppUser {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /*
     * 用户名
     */
    private String username;

    /*
     * 昵称
     */
    private String nickname;

    /*
     * 密码
     */
    private String password;

    /*
     * 头像地址
     */
    private String avatar;

    /*
     * 用户角色
     */
    private String userRole;

    /*
     * 状态
     */
    private String status;

    /*
     * 创建时间
     */
    private LocalDateTime createTime;

    /*
     * 更新时间
     */
    private LocalDateTime updateTime;
}
