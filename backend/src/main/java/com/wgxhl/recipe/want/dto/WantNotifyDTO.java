package com.wgxhl.recipe.want.dto;

import java.time.LocalDate;
import java.util.List;

public class WantNotifyDTO {

    private LocalDate plannedDate;

    private List<String> targetUserIds;

    public LocalDate getPlannedDate() {
        return plannedDate;
    }

    public void setPlannedDate(LocalDate plannedDate) {
        this.plannedDate = plannedDate;
    }

    public List<String> getTargetUserIds() {
        return targetUserIds;
    }

    public void setTargetUserIds(List<String> targetUserIds) {
        this.targetUserIds = targetUserIds;
    }
}
