package com.wgxhl.recipe.want.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.recipe.entity.Recipe;
import com.wgxhl.recipe.recipe.mapper.RecipeMapper;
import com.wgxhl.recipe.want.dto.WantedRecipePageDTO;
import com.wgxhl.recipe.want.entity.UserWantedRecipe;
import com.wgxhl.recipe.want.mapper.UserWantedRecipeMapper;
import com.wgxhl.recipe.want.service.UserWantedRecipeService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserWantedRecipeServiceImpl extends ServiceImpl<UserWantedRecipeMapper, UserWantedRecipe>
        implements UserWantedRecipeService {

    private final RecipeMapper recipeMapper;

    public UserWantedRecipeServiceImpl(RecipeMapper recipeMapper) {
        this.recipeMapper = recipeMapper;
    }

    @Override
    public boolean save(UserWantedRecipe entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return super.save(entity);
    }

    @Override
    public boolean updateById(UserWantedRecipe entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }

    @Override
    public ApiResponse<Page<UserWantedRecipe>> page(WantedRecipePageDTO dto) {
        Page<UserWantedRecipe> page = new Page<>(dto.getCurrent(), dto.getSize());
        Page<UserWantedRecipe> result = lambdaQuery()
                .eq(StringUtils.hasText(dto.getUserId()), UserWantedRecipe::getUserId, dto.getUserId())
                .like(StringUtils.hasText(dto.getRecipeName()), UserWantedRecipe::getRecipeName, dto.getRecipeName())
                .eq(dto.getPlannedDate() != null, UserWantedRecipe::getPlannedDate, dto.getPlannedDate())
                .ge(dto.getPlannedDateStart() != null, UserWantedRecipe::getPlannedDate, dto.getPlannedDateStart())
                .orderByAsc(UserWantedRecipe::getPlannedDate)
                .orderByDesc(UserWantedRecipe::getUpdateTime)
                .page(page);
        return ApiResponse.success(result);
    }

    @Override
    public ApiResponse<List<LocalDate>> dateList(String userId) {
        if (!StringUtils.hasText(userId)) {
            return ApiResponse.success(java.util.Collections.emptyList());
        }
        List<LocalDate> dates = lambdaQuery()
                .eq(UserWantedRecipe::getUserId, userId)
                .select(UserWantedRecipe::getPlannedDate)
                .orderByAsc(UserWantedRecipe::getPlannedDate)
                .list()
                .stream()
                .map(UserWantedRecipe::getPlannedDate)
                .filter(date -> date != null)
                .distinct()
                .collect(Collectors.toList());
        return ApiResponse.success(dates);
    }

    @Override
    public ApiResponse<UserWantedRecipe> create(UserWantedRecipe entity) {
        if (!StringUtils.hasText(entity.getUserId())) {
            return ApiResponse.fail("用户id不能为空");
        }
        if (!StringUtils.hasText(entity.getRecipeId())) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        if (entity.getPlannedDate() == null) {
            return ApiResponse.fail("想吃日期不能为空");
        }
        Recipe recipe = recipeMapper.selectById(entity.getRecipeId());
        if (recipe == null) {
            return ApiResponse.fail("菜谱不存在");
        }
        UserWantedRecipe existing = lambdaQuery()
                .eq(UserWantedRecipe::getUserId, entity.getUserId())
                .eq(UserWantedRecipe::getRecipeId, entity.getRecipeId())
                .one();
        if (existing != null) {
            existing.setRecipeName(recipe.getRecipeName());
            existing.setCoverImage(recipe.getCoverImage());
            existing.setPlannedDate(entity.getPlannedDate());
            updateById(existing);
            return ApiResponse.success("已更新想吃日期", existing);
        }
        entity.setRecipeName(recipe.getRecipeName());
        entity.setCoverImage(recipe.getCoverImage());
        save(entity);
        return ApiResponse.success("已添加到想吃", entity);
    }

    @Override
    public ApiResponse<Void> updatePlannedDate(UserWantedRecipe entity) {
        if (!StringUtils.hasText(entity.getId())) {
            return ApiResponse.fail("想吃记录id不能为空");
        }
        if (entity.getPlannedDate() == null) {
            return ApiResponse.fail("想吃日期不能为空");
        }
        UserWantedRecipe existing = super.getById(entity.getId());
        if (existing == null) {
            return ApiResponse.fail("想吃记录不存在");
        }
        existing.setPlannedDate(entity.getPlannedDate());
        updateById(existing);
        return ApiResponse.success("已修改想吃日期", null);
    }

    @Override
    public ApiResponse<Void> delete(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("想吃记录id不能为空");
        }
        if (super.getById(id) == null) {
            return ApiResponse.fail("想吃记录不存在");
        }
        removeById(id);
        return ApiResponse.success("已从想吃移除", null);
    }

    @Override
    public ApiResponse<Void> deleteByRecipeId(String userId, String recipeId) {
        if (!StringUtils.hasText(userId)) {
            return ApiResponse.fail("用户id不能为空");
        }
        if (!StringUtils.hasText(recipeId)) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        boolean removed = remove(new LambdaQueryWrapper<UserWantedRecipe>()
                .eq(UserWantedRecipe::getUserId, userId)
                .eq(UserWantedRecipe::getRecipeId, recipeId));
        if (!removed) {
            return ApiResponse.fail("想吃记录不存在");
        }
        return ApiResponse.success("已从想吃移除", null);
    }

    @Override
    public ApiResponse<Boolean> check(String userId, String recipeId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(recipeId)) {
            return ApiResponse.success(false);
        }
        boolean exists = lambdaQuery()
                .eq(UserWantedRecipe::getUserId, userId)
                .eq(UserWantedRecipe::getRecipeId, recipeId)
                .exists();
        return ApiResponse.success(exists);
    }

    @Override
    public void deleteByRecipeId(String recipeId) {
        if (!StringUtils.hasText(recipeId)) {
            return;
        }
        remove(new LambdaQueryWrapper<UserWantedRecipe>().eq(UserWantedRecipe::getRecipeId, recipeId));
    }

    @Override
    public void deleteByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        remove(new LambdaQueryWrapper<UserWantedRecipe>().eq(UserWantedRecipe::getUserId, userId));
    }
}
