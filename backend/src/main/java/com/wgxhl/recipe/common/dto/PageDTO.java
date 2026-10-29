package com.wgxhl.recipe.common.dto;

import lombok.Data;

@Data
public class PageDTO {

    /*
     * 当前页
     */
    private Long current = 1L;

    /*
     * 每页条数
     */
    private Long size = 10L;

    public Long getCurrent() {
        return normalizeCurrent(current);
    }

    public Long getSize() {
        return normalizeSize(size, 10L);
    }

    public static long normalizeCurrent(Long current) {
        return current == null ? 1L : Math.max(1L, Math.min(current, 10_000L));
    }

    public static long normalizeSize(Long size, long defaultSize) {
        return size == null ? defaultSize : Math.max(1L, Math.min(size, 50L));
    }
}
