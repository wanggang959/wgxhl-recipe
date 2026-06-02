package com.wgxhl.recipe.image.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.image.dto.RecipeImageBatchDTO;
import com.wgxhl.recipe.image.entity.RecipeImage;
import com.wgxhl.recipe.image.mapper.RecipeImageMapper;
import com.wgxhl.recipe.image.service.RecipeImageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RecipeImageServiceImpl extends ServiceImpl<RecipeImageMapper, RecipeImage>
        implements RecipeImageService {

    @Override
    public boolean save(RecipeImage entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return super.save(entity);
    }

    @Override
    public boolean updateById(RecipeImage entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }

    @Override
    public ApiResponse<List<RecipeImage>> listByRecipeId(String recipeId) {
        if (!StringUtils.hasText(recipeId)) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        List<RecipeImage> list = lambdaQuery()
                .eq(RecipeImage::getRecipeId, recipeId)
                .orderByAsc(RecipeImage::getSortNo)
                .orderByAsc(RecipeImage::getCreateTime)
                .list();
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<RecipeImage> create(RecipeImage entity) {
        if (!StringUtils.hasText(entity.getRecipeId())) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        if (!StringUtils.hasText(entity.getImageUrl())) {
            return ApiResponse.fail("图片地址不能为空");
        }
        if (!StringUtils.hasText(entity.getImageType())) {
            return ApiResponse.fail("图片类型不能为空");
        }
        save(entity);
        return ApiResponse.success("保存成功", entity);
    }

    @Override
    public ApiResponse<Void> update(RecipeImage entity) {
        if (!StringUtils.hasText(entity.getId())) {
            return ApiResponse.fail("图片id不能为空");
        }
        if (super.getById(entity.getId()) == null) {
            return ApiResponse.fail("图片不存在");
        }
        updateById(entity);
        return ApiResponse.success("更新成功", null);
    }

    @Override
    public ApiResponse<Void> delete(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("图片id不能为空");
        }
        if (super.getById(id) == null) {
            return ApiResponse.fail("图片不存在");
        }
        removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> saveBatchByRecipeId(RecipeImageBatchDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getRecipeId())) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        ApiResponse<Void> validateResult = validateImageList(dto.getImageList());
        if (validateResult != null) {
            return validateResult;
        }
        doSaveBatchByRecipeId(dto.getRecipeId(), dto.getImageList());
        return ApiResponse.success("保存成功", null);
    }

    @Override
    public void deleteByRecipeId(String recipeId) {
        if (!StringUtils.hasText(recipeId)) {
            return;
        }
        remove(new LambdaQueryWrapper<RecipeImage>().eq(RecipeImage::getRecipeId, recipeId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBatchByRecipeId(String recipeId, List<RecipeImage> imageList) {
        if (!StringUtils.hasText(recipeId)) {
            return;
        }
        doSaveBatchByRecipeId(recipeId, imageList);
    }

    private void doSaveBatchByRecipeId(String recipeId, List<RecipeImage> imageList) {
        remove(new LambdaQueryWrapper<RecipeImage>().eq(RecipeImage::getRecipeId, recipeId));
        if (imageList != null) {
            for (RecipeImage image : imageList) {
                image.setId(null);
                image.setRecipeId(recipeId);
                save(image);
            }
        }
    }

    private ApiResponse<Void> validateImageList(List<RecipeImage> imageList) {
        if (imageList == null) {
            return null;
        }
        for (RecipeImage image : imageList) {
            if (!StringUtils.hasText(image.getImageUrl())) {
                return ApiResponse.fail("图片地址不能为空");
            }
            if (!StringUtils.hasText(image.getImageType())) {
                return ApiResponse.fail("图片类型不能为空");
            }
        }
        return null;
    }
}
