package com.wgxhl.recipe.todo.dto;

import lombok.Data;

@Data
public class TodoSummaryDTO {

    private long todayCount;

    private long doneCount;

    private long dueSoonCount;

    private long totalCount;
}
