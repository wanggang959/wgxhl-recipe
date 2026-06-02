package com.wgxhl.recipe.record.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.dto.IdDTO;
import com.wgxhl.recipe.record.dto.ViewRecordClearDTO;
import com.wgxhl.recipe.record.dto.ViewRecordCreateDTO;
import com.wgxhl.recipe.record.dto.ViewRecordPageDTO;
import com.wgxhl.recipe.record.entity.RecipeViewRecord;
import com.wgxhl.recipe.record.service.RecipeViewRecordService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/viewRecord")
public class RecipeViewRecordController {

    private final RecipeViewRecordService recipeViewRecordService;

    public RecipeViewRecordController(RecipeViewRecordService recipeViewRecordService) {
        this.recipeViewRecordService = recipeViewRecordService;
    }

    /***
     * @Description 分页查询浏览记录
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/page")
    public ApiResponse<Page<RecipeViewRecord>> page(@RequestBody ViewRecordPageDTO dto) {
        return recipeViewRecordService.page(dto);
    }

    /***
     * @Description 新增浏览记录
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/create")
    public ApiResponse<RecipeViewRecord> create(@RequestBody ViewRecordCreateDTO dto) {
        return recipeViewRecordService.create(dto);
    }

    /***
     * @Description 删除浏览记录
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody IdDTO dto) {
        return recipeViewRecordService.delete(dto.getId());
    }

    /***
     * @Description 清空用户浏览记录
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/clearByUserId")
    public ApiResponse<Void> clearByUserId(@RequestBody ViewRecordClearDTO dto) {
        return recipeViewRecordService.clearByUserId(dto.getUserId());
    }
}
