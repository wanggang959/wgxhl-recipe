package com.wgxhl.recipe.favorite.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.favorite.dto.FavoritePageDTO;
import com.wgxhl.recipe.favorite.entity.UserFavorite;
import com.wgxhl.recipe.favorite.mapper.UserFavoriteMapper;
import com.wgxhl.recipe.favorite.service.UserFavoriteService;
import com.wgxhl.recipe.recipe.entity.Recipe;
import com.wgxhl.recipe.recipe.mapper.RecipeMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class UserFavoriteServiceImpl extends ServiceImpl<UserFavoriteMapper, UserFavorite>
        implements UserFavoriteService {

    private final RecipeMapper recipeMapper;

    public UserFavoriteServiceImpl(RecipeMapper recipeMapper) {
        this.recipeMapper = recipeMapper;
    }

    @Override
    public boolean save(UserFavorite entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return super.save(entity);
    }

    @Override
    public ApiResponse<Page<UserFavorite>> page(FavoritePageDTO dto) {
        Page<UserFavorite> page = new Page<>(dto.getCurrent(), dto.getSize());
        Page<UserFavorite> result = lambdaQuery()
                .eq(StringUtils.hasText(dto.getUserId()), UserFavorite::getUserId, dto.getUserId())
                .like(StringUtils.hasText(dto.getRecipeName()), UserFavorite::getRecipeName, dto.getRecipeName())
                .orderByDesc(UserFavorite::getCreateTime)
                .page(page);
        return ApiResponse.success(result);
    }

    @Override
    public ApiResponse<UserFavorite> create(UserFavorite entity) {
        if (!StringUtils.hasText(entity.getUserId())) {
            return ApiResponse.fail("用户id不能为空");
        }
        if (!StringUtils.hasText(entity.getRecipeId())) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        if (lambdaQuery()
                .eq(UserFavorite::getUserId, entity.getUserId())
                .eq(UserFavorite::getRecipeId, entity.getRecipeId())
                .exists()) {
            return ApiResponse.fail("已收藏该菜谱");
        }
        Recipe recipe = recipeMapper.selectById(entity.getRecipeId());
        if (recipe == null) {
            return ApiResponse.fail("菜谱不存在");
        }
        entity.setRecipeName(recipe.getRecipeName());
        entity.setCoverImage(recipe.getCoverImage());
        save(entity);
        return ApiResponse.success("收藏成功", entity);
    }

    @Override
    public ApiResponse<Void> delete(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("收藏id不能为空");
        }
        if (super.getById(id) == null) {
            return ApiResponse.fail("收藏记录不存在");
        }
        removeById(id);
        return ApiResponse.success("取消收藏成功", null);
    }

    @Override
    public ApiResponse<Void> deleteByRecipeId(String userId, String recipeId) {
        if (!StringUtils.hasText(userId)) {
            return ApiResponse.fail("用户id不能为空");
        }
        if (!StringUtils.hasText(recipeId)) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        boolean removed = remove(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getRecipeId, recipeId));
        if (!removed) {
            return ApiResponse.fail("收藏记录不存在");
        }
        return ApiResponse.success("取消收藏成功", null);
    }

    @Override
    public ApiResponse<Boolean> check(String userId, String recipeId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(recipeId)) {
            return ApiResponse.success(false);
        }
        boolean exists = lambdaQuery()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getRecipeId, recipeId)
                .exists();
        return ApiResponse.success(exists);
    }

    @Override
    public void deleteByRecipeId(String recipeId) {
        if (!StringUtils.hasText(recipeId)) {
            return;
        }
        remove(new LambdaQueryWrapper<UserFavorite>().eq(UserFavorite::getRecipeId, recipeId));
    }

    @Override
    public void deleteByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        remove(new LambdaQueryWrapper<UserFavorite>().eq(UserFavorite::getUserId, userId));
    }
}
