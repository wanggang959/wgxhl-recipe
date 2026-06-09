package com.wgxhl.recipe.notification.dto;

import lombok.Data;

@Data
public class NotificationPageDTO {

    private long current = 1;

    private long size = 20;

    private Boolean isRead;
}
