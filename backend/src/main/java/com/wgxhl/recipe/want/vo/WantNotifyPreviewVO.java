package com.wgxhl.recipe.want.vo;

import java.time.LocalDate;

public class WantNotifyPreviewVO {

    private LocalDate plannedDate;

    private String previewBody;

    public LocalDate getPlannedDate() {
        return plannedDate;
    }

    public void setPlannedDate(LocalDate plannedDate) {
        this.plannedDate = plannedDate;
    }

    public String getPreviewBody() {
        return previewBody;
    }

    public void setPreviewBody(String previewBody) {
        this.previewBody = previewBody;
    }
}
