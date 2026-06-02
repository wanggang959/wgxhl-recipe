package com.wgxhl.recipe.seasoning.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.dto.IdDTO;
import com.wgxhl.recipe.seasoning.dto.SeasoningPageDTO;
import com.wgxhl.recipe.seasoning.entity.Seasoning;
import com.wgxhl.recipe.seasoning.service.SeasoningService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/seasoning")
public class SeasoningController {

    private final SeasoningService seasoningService;

    public SeasoningController(SeasoningService seasoningService) {
        this.seasoningService = seasoningService;
    }

    @PostMapping("/page")
    public ApiResponse<Page<Seasoning>> page(@RequestBody SeasoningPageDTO dto) {
        return seasoningService.page(dto);
    }

    @PostMapping("/getById")
    public ApiResponse<Seasoning> getById(@RequestBody IdDTO dto) {
        return seasoningService.getById(dto.getId());
    }

    @PostMapping("/create")
    public ApiResponse<Seasoning> create(@RequestBody Seasoning entity) {
        return seasoningService.create(entity);
    }

    @PostMapping("/update")
    public ApiResponse<Void> update(@RequestBody Seasoning entity) {
        return seasoningService.update(entity);
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody IdDTO dto) {
        return seasoningService.delete(dto.getId());
    }
}
