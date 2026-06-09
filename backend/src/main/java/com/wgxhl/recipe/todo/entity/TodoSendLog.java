package com.wgxhl.recipe.todo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("todo_send_log")
public class TodoSendLog {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String todoId;

    private String ruleId;

    private String notifyType;

    private LocalDateTime sendTime;

    private String sendStatus;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
