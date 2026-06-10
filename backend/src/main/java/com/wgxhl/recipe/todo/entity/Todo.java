package com.wgxhl.recipe.todo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("todo")
public class Todo {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String title;

    private String category;

    private String description;

    private String relatedType;

    private String relatedId;

    private String ownerId;

    private LocalDateTime dueTime;

    private String dueCalendar;

    private Integer lunarDueMonth;

    private Integer lunarDueDay;

    private Boolean lunarDueLeap;

    private Integer birthdayYear;

    private String repeatType;

    private Boolean notifySite;

    private Boolean notifyEmail;

    private Boolean notifyPush;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String ownerName;

    @TableField(exist = false)
    private List<String> ownerIds;

    @TableField(exist = false)
    private List<String> ownerNames;

    @TableField(exist = false)
    private String ownerAvatar;

    @TableField(exist = false)
    private String ownerEmail;

    @TableField(exist = false)
    private String dueLabel;

    @TableField(exist = false)
    private String remainText;

    @TableField(exist = false)
    private Integer birthdayAge;

    @TableField(exist = false)
    private String birthdayDisplayText;

    @TableField(exist = false)
    private String birthdaySolarLabel;

    @TableField(exist = false)
    private List<Integer> noticeMinutes;

    @TableField(exist = false)
    private String nextNotifyLabel;

    @TableField(exist = false)
    private String lastOccurrenceLabel;

    @TableField(exist = false)
    private Boolean canComplete;

    @TableField(exist = false)
    private String completeDisabledReason;
}
