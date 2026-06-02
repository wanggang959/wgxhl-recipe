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
}
