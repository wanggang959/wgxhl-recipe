package com.wgxhl.recipe.todo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("todo_owner")
public class TodoOwner {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String todoId;

    private String ownerId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
