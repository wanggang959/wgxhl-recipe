package com.wgxhl.recipe.todo.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TodoPageDTO {

    private long current = 1;

    private long size = 20;

    private String title;

    private String category;

    private String ownerId;

    private String status;

    private LocalDateTime dueTimeStart;

    private LocalDateTime dueTimeEnd;
}
