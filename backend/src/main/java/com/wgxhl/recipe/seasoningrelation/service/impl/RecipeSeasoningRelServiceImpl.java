package com.wgxhl.recipe.seasoningrelation.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.seasoning.entity.Seasoning;
import com.wgxhl.recipe.seasoning.service.SeasoningService;
import com.wgxhl.recipe.seasoningrelation.dto.RecipeSeasoningRelBatchDTO;
import com.wgxhl.recipe.seasoningrelation.entity.RecipeSeasoningRel;
import com.wgxhl.recipe.seasoningrelation.mapper.RecipeSeasoningRelMapper;
import com.wgxhl.recipe.seasoningrelation.service.RecipeSeasoningRelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RecipeSeasoningRelServiceImpl extends ServiceImpl<RecipeSeasoningRelMapper, RecipeSeasoningRel>
        implements RecipeSeasoningRelService {

    private final SeasoningService seasoningService;

    public RecipeSeasoningRelServiceImpl(SeasoningService seasoningService) {
        this.seasoningService = seasoningService;
    }

    @Override
    public boolean save(RecipeSeasoningRel entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return super.save(entity);
    }

    @Override
    public boolean updateById(RecipeSeasoningRel entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }

    @Override
    public ApiResponse<List<RecipeSeasoningRel>> listByRecipeId(String recipeId) {
        if (!StringUtils.hasText(recipeId)) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        List<RecipeSeasoningRel> list = lambdaQuery()
                .eq(RecipeSeasoningRel::getRecipeId, recipeId)
                .orderByAsc(RecipeSeasoningRel::getSortNo)
                .orderByAsc(RecipeSeasoningRel::getCreateTime)
                .list();
        list.forEach(this::fillSeasoningView);
        return ApiResponse.success(list);
    }

    @Override
    public ApiResponse<RecipeSeasoningRel> create(RecipeSeasoningRel entity) {
        if (!StringUtils.hasText(entity.getRecipeId())) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        if (!StringUtils.hasText(entity.getSeasoningId())) {
            return ApiResponse.fail("调料id不能为空");
        }
        if (lambdaQuery()
                .eq(RecipeSeasoningRel::getRecipeId, entity.getRecipeId())
                .eq(RecipeSeasoningRel::getSeasoningId, entity.getSeasoningId())
                .exists()) {
            return ApiResponse.fail("该菜谱下调料关系已存在");
        }
        ApiResponse<Void> fillResult = fillSeasoningName(entity);
        if (fillResult != null) {
            return ApiResponse.fail(fillResult.getMessage());
        }
        save(entity);
        return ApiResponse.success("保存成功", entity);
    }

    @Override
    public ApiResponse<Void> update(RecipeSeasoningRel entity) {
        if (!StringUtils.hasText(entity.getId())) {
            return ApiResponse.fail("关系id不能为空");
        }
        RecipeSeasoningRel existing = super.getById(entity.getId());
        if (existing == null) {
            return ApiResponse.fail("关系不存在");
        }
        if (StringUtils.hasText(entity.getSeasoningId())
                && !entity.getSeasoningId().equals(existing.getSeasoningId())) {
            ApiResponse<Void> fillResult = fillSeasoningName(entity);
            if (fillResult != null) {
                return fillResult;
            }
            if (lambdaQuery()
                    .ne(RecipeSeasoningRel::getId, entity.getId())
                    .eq(RecipeSeasoningRel::getRecipeId, existing.getRecipeId())
                    .eq(RecipeSeasoningRel::getSeasoningId, entity.getSeasoningId())
                    .exists()) {
                return ApiResponse.fail("该菜谱下调料关系已存在");
            }
        }
        updateById(entity);
        return ApiResponse.success("更新成功", null);
    }

    @Override
    public ApiResponse<Void> delete(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("关系id不能为空");
        }
        if (super.getById(id) == null) {
            return ApiResponse.fail("关系不存在");
        }
        removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> saveBatchByRecipeId(RecipeSeasoningRelBatchDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getRecipeId())) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        ApiResponse<Void> validateResult = validateRelList(dto.getRelList());
        if (validateResult != null) {
            return validateResult;
        }
        doSaveBatchByRecipeId(dto.getRecipeId(), dto.getRelList());
        return ApiResponse.success("保存成功", null);
    }

    @Override
    public void deleteByRecipeId(String recipeId) {
        if (!StringUtils.hasText(recipeId)) {
            return;
        }
        remove(new LambdaQueryWrapper<RecipeSeasoningRel>()
                .eq(RecipeSeasoningRel::getRecipeId, recipeId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBatchByRecipeId(String recipeId, List<RecipeSeasoningRel> relList) {
        if (!StringUtils.hasText(recipeId)) {
            return;
        }
        doSaveBatchByRecipeId(recipeId, relList);
    }

    private void doSaveBatchByRecipeId(String recipeId, List<RecipeSeasoningRel> relList) {
        remove(new LambdaQueryWrapper<RecipeSeasoningRel>()
                .eq(RecipeSeasoningRel::getRecipeId, recipeId));
        if (relList != null) {
            for (RecipeSeasoningRel rel : relList) {
                rel.setId(null);
                rel.setRecipeId(recipeId);
                ApiResponse<Void> fillResult = fillSeasoningName(rel);
                if (fillResult == null) {
                    save(rel);
                }
            }
        }
    }

    private ApiResponse<Void> validateRelList(List<RecipeSeasoningRel> relList) {
        if (relList == null) {
            return null;
        }
        Set<String> seasoningIds = new HashSet<>();
        for (RecipeSeasoningRel rel : relList) {
            if (!StringUtils.hasText(rel.getSeasoningId())) {
                return ApiResponse.fail("调料id不能为空");
            }
            if (!seasoningIds.add(rel.getSeasoningId())) {
                return ApiResponse.fail("调料不能重复");
            }
            ApiResponse<Void> fillResult = fillSeasoningName(rel);
            if (fillResult != null) {
                return fillResult;
            }
        }
        return null;
    }

    private ApiResponse<Void> fillSeasoningName(RecipeSeasoningRel entity) {
        Seasoning seasoning = seasoningService.lambdaQuery()
                .eq(Seasoning::getId, entity.getSeasoningId())
                .one();
        if (seasoning == null) {
            return ApiResponse.fail("调料不存在");
        }
        entity.setSeasoningName(seasoning.getSeasoningName());
        entity.setSeasoningImage(seasoning.getSeasoningImage());
        return null;
    }

    private void fillSeasoningView(RecipeSeasoningRel entity) {
        if (entity == null || !StringUtils.hasText(entity.getSeasoningId())) {
            return;
        }
        Seasoning seasoning = seasoningService.lambdaQuery()
                .eq(Seasoning::getId, entity.getSeasoningId())
                .one();
        if (seasoning == null) {
            return;
        }
        entity.setSeasoningName(seasoning.getSeasoningName());
        entity.setSeasoningImage(seasoning.getSeasoningImage());
    }
}
