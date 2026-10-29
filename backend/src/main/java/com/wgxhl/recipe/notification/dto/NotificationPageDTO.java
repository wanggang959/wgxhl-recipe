package com.wgxhl.recipe.notification.dto;

import lombok.Data;
import com.wgxhl.recipe.common.dto.PageDTO;

@Data
public class NotificationPageDTO {

    private long current = 1;

    private long size = 20;

    public long getCurrent() { return PageDTO.normalizeCurrent(current); }
    public long getSize() { return PageDTO.normalizeSize(size, 20L); }

    private Boolean isRead;
}
