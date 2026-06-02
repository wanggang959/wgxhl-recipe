package com.wgxhl.recipe.record.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.record.dto.ViewRecordCreateDTO;
import com.wgxhl.recipe.record.dto.ViewRecordPageDTO;
import com.wgxhl.recipe.record.entity.RecipeViewRecord;
import com.wgxhl.recipe.record.mapper.RecipeViewRecordMapper;
import com.wgxhl.recipe.record.service.RecipeViewRecordService;
import com.wgxhl.recipe.recipe.entity.Recipe;
import com.wgxhl.recipe.recipe.mapper.RecipeMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class RecipeViewRecordServiceImpl extends ServiceImpl<RecipeViewRecordMapper, RecipeViewRecord>
        implements RecipeViewRecordService {

    private final RecipeMapper recipeMapper;

    public RecipeViewRecordServiceImpl(RecipeMapper recipeMapper) {
        this.recipeMapper = recipeMapper;
    }

    @Override
    public boolean save(RecipeViewRecord entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return super.save(entity);
    }

    @Override
    public ApiResponse<Page<RecipeViewRecord>> page(ViewRecordPageDTO dto) {
        Page<RecipeViewRecord> page = new Page<>(dto.getCurrent(), dto.getSize());
        Page<RecipeViewRecord> result = lambdaQuery()
                .eq(StringUtils.hasText(dto.getUserId()), RecipeViewRecord::getUserId, dto.getUserId())
                .like(StringUtils.hasText(dto.getRecipeName()), RecipeViewRecord::getRecipeName, dto.getRecipeName())
                .orderByDesc(RecipeViewRecord::getViewTime)
                .page(page);
        return ApiResponse.success(result);
    }

    @Override
    public ApiResponse<RecipeViewRecord> create(ViewRecordCreateDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getRecipeId())) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        Recipe recipe = recipeMapper.selectById(dto.getRecipeId());
        if (recipe == null) {
            return ApiResponse.fail("菜谱不存在");
        }
        RecipeViewRecord record = new RecipeViewRecord();
        record.setUserId(dto.getUserId());
        record.setRecipeId(dto.getRecipeId());
        record.setRecipeName(recipe.getRecipeName());
        record.setViewTime(LocalDateTime.now());
        save(record);
        return ApiResponse.success("保存成功", record);
    }

    @Override
    public ApiResponse<Void> delete(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("浏览记录id不能为空");
        }
        if (super.getById(id) == null) {
            return ApiResponse.fail("浏览记录不存在");
        }
        removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    @Override
    public ApiResponse<Void> clearByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return ApiResponse.fail("用户id不能为空");
        }
        remove(new LambdaQueryWrapper<RecipeViewRecord>().eq(RecipeViewRecord::getUserId, userId));
        return ApiResponse.success("清空成功", null);
    }

    @Override
    public void deleteByRecipeId(String recipeId) {
        if (!StringUtils.hasText(recipeId)) {
            return;
        }
        remove(new LambdaQueryWrapper<RecipeViewRecord>().eq(RecipeViewRecord::getRecipeId, recipeId));
    }

    @Override
    public void deleteByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        remove(new LambdaQueryWrapper<RecipeViewRecord>().eq(RecipeViewRecord::getUserId, userId));
    }
}
