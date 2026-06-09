package com.wgxhl.recipe.user.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UserPreviewVO {

    private String id;

    private String username;

    private String nickname;

    private String avatar;

    private String email;

    private LocalDate birthday;

    private String birthdayCalendar;

    private Integer lunarBirthdayYear;

    private Integer lunarBirthdayMonth;

    private Integer lunarBirthdayDay;

    private Boolean lunarBirthdayLeap;

    private String notificationPreference;
}
