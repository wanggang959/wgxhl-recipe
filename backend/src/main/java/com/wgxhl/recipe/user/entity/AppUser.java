package com.wgxhl.recipe.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
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
     * 邮箱
     */
    private String email;

    /*
     * 公历生日
     */
    private LocalDate birthday;

    /*
     * 生日历法：SOLAR-公历，LUNAR-农历
     */
    private String birthdayCalendar;

    /*
     * 农历生日月份
     */
    private Integer lunarBirthdayYear;

    /*
     * 农历生日月份
     */
    private Integer lunarBirthdayMonth;

    /*
     * 农历生日日期
     */
    private Integer lunarBirthdayDay;

    /*
     * 是否农历闰月
     */
    private Boolean lunarBirthdayLeap;

    /*
     * 用户角色
     */
    private String userRole;

    /*
     * 状态
     */
    private String status;

    /*
     * 备注
     */
    private String remark;

    /*
     * 通知偏好：site,email,push
     */
    private String notificationPreference;

    /*
     * 创建时间
     */
    private LocalDateTime createTime;

    /*
     * 更新时间
     */
    private LocalDateTime updateTime;

    /*
     * 登录令牌（非数据库字段）
     */
    @TableField(exist = false)
    private String token;

    /*
     * 近期待办数量（非数据库字段）
     */
    @TableField(exist = false)
    private Long recentTodoCount;
}
