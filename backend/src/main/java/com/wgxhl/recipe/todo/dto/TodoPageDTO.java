package com.wgxhl.recipe.todo.dto;

import lombok.Data;

import java.time.LocalDateTime;
import com.wgxhl.recipe.common.dto.PageDTO;

@Data
public class TodoPageDTO {

    private long current = 1;

    private long size = 20;

    public long getCurrent() { return PageDTO.normalizeCurrent(current); }
    public long getSize() { return PageDTO.normalizeSize(size, 20L); }

    private String title;

    private String category;

    private String ownerId;

    private String status;

    private LocalDateTime dueTimeStart;

    private LocalDateTime dueTimeEnd;
}
