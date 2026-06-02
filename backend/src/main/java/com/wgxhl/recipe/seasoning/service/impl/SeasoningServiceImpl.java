package com.wgxhl.recipe.seasoning.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.seasoning.dto.SeasoningPageDTO;
import com.wgxhl.recipe.seasoning.entity.Seasoning;
import com.wgxhl.recipe.seasoning.mapper.SeasoningMapper;
import com.wgxhl.recipe.seasoning.service.SeasoningService;
import com.wgxhl.recipe.seasoningrelation.entity.RecipeSeasoningRel;
import com.wgxhl.recipe.seasoningrelation.mapper.RecipeSeasoningRelMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class SeasoningServiceImpl extends ServiceImpl<SeasoningMapper, Seasoning>
        implements SeasoningService {

    private final RecipeSeasoningRelMapper recipeSeasoningRelMapper;

    public SeasoningServiceImpl(RecipeSeasoningRelMapper recipeSeasoningRelMapper) {
        this.recipeSeasoningRelMapper = recipeSeasoningRelMapper;
    }

    @Override
    public boolean save(Seasoning entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return super.save(entity);
    }

    @Override
    public boolean updateById(Seasoning entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }

    @Override
    public ApiResponse<Page<Seasoning>> page(SeasoningPageDTO dto) {
        Page<Seasoning> page = new Page<>(dto.getCurrent(), dto.getSize());
        Page<Seasoning> result = lambdaQuery()
                .like(StringUtils.hasText(dto.getSeasoningName()), Seasoning::getSeasoningName, dto.getSeasoningName())
                .orderByDesc(Seasoning::getCreateTime)
                .page(page);
        return ApiResponse.success(result);
    }

    @Override
    public ApiResponse<Seasoning> getById(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("调料id不能为空");
        }
        Seasoning seasoning = super.getById(id);
        if (seasoning == null) {
            return ApiResponse.fail("调料不存在");
        }
        return ApiResponse.success(seasoning);
    }

    @Override
    public ApiResponse<Seasoning> create(Seasoning entity) {
        if (!StringUtils.hasText(entity.getSeasoningName())) {
            return ApiResponse.fail("调料名称不能为空");
        }
        if (lambdaQuery().eq(Seasoning::getSeasoningName, entity.getSeasoningName()).exists()) {
            return ApiResponse.fail("调料名称已存在");
        }
        save(entity);
        return ApiResponse.success("保存成功", entity);
    }

    @Override
    public ApiResponse<Void> update(Seasoning entity) {
        if (!StringUtils.hasText(entity.getId())) {
            return ApiResponse.fail("调料id不能为空");
        }
        if (super.getById(entity.getId()) == null) {
            return ApiResponse.fail("调料不存在");
        }
        if (StringUtils.hasText(entity.getSeasoningName())
                && lambdaQuery()
                .ne(Seasoning::getId, entity.getId())
                .eq(Seasoning::getSeasoningName, entity.getSeasoningName())
                .exists()) {
            return ApiResponse.fail("调料名称已存在");
        }
        updateById(entity);
        return ApiResponse.success("更新成功", null);
    }

    @Override
    public ApiResponse<Void> delete(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("调料id不能为空");
        }
        if (super.getById(id) == null) {
            return ApiResponse.fail("调料不存在");
        }
        Long relCount = recipeSeasoningRelMapper.selectCount(
                new LambdaQueryWrapper<RecipeSeasoningRel>()
                        .eq(RecipeSeasoningRel::getSeasoningId, id));
        if (relCount != null && relCount > 0) {
            return ApiResponse.fail("该调料已被菜谱引用，无法删除");
        }
        removeById(id);
        return ApiResponse.success("删除成功", null);
    }
}
