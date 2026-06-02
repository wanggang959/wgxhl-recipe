package com.wgxhl.recipe.recipe.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.category.entity.RecipeCategory;
import com.wgxhl.recipe.category.service.RecipeCategoryService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.favorite.service.UserFavoriteService;
import com.wgxhl.recipe.image.dto.RecipeImageBatchDTO;
import com.wgxhl.recipe.image.service.RecipeImageService;
import com.wgxhl.recipe.recipe.dto.RecipePageDTO;
import com.wgxhl.recipe.recipe.dto.RecipeSaveDTO;
import com.wgxhl.recipe.recipe.entity.Recipe;
import com.wgxhl.recipe.recipe.mapper.RecipeMapper;
import com.wgxhl.recipe.recipe.service.RecipeService;
import com.wgxhl.recipe.recipe.vo.RecipeDetailVO;
import com.wgxhl.recipe.record.service.RecipeViewRecordService;
import com.wgxhl.recipe.relation.dto.RecipeIngredientRelBatchDTO;
import com.wgxhl.recipe.relation.service.RecipeIngredientRelService;
import com.wgxhl.recipe.seasoningrelation.dto.RecipeSeasoningRelBatchDTO;
import com.wgxhl.recipe.seasoningrelation.service.RecipeSeasoningRelService;
import com.wgxhl.recipe.step.dto.RecipeStepBatchDTO;
import com.wgxhl.recipe.step.entity.RecipeStep;
import com.wgxhl.recipe.step.service.RecipeStepService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RecipeServiceImpl extends ServiceImpl<RecipeMapper, Recipe>
        implements RecipeService {

    private final RecipeCategoryService recipeCategoryService;
    private final RecipeStepService recipeStepService;
    private final RecipeImageService recipeImageService;
    private final RecipeIngredientRelService recipeIngredientRelService;
    private final RecipeSeasoningRelService recipeSeasoningRelService;
    private final UserFavoriteService userFavoriteService;
    private final RecipeViewRecordService recipeViewRecordService;

    public RecipeServiceImpl(RecipeCategoryService recipeCategoryService,
                             RecipeStepService recipeStepService,
                             RecipeImageService recipeImageService,
                             RecipeIngredientRelService recipeIngredientRelService,
                             RecipeSeasoningRelService recipeSeasoningRelService,
                             UserFavoriteService userFavoriteService,
                             RecipeViewRecordService recipeViewRecordService) {
        this.recipeCategoryService = recipeCategoryService;
        this.recipeStepService = recipeStepService;
        this.recipeImageService = recipeImageService;
        this.recipeIngredientRelService = recipeIngredientRelService;
        this.recipeSeasoningRelService = recipeSeasoningRelService;
        this.userFavoriteService = userFavoriteService;
        this.recipeViewRecordService = recipeViewRecordService;
    }

    @Override
    public boolean save(Recipe entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return super.save(entity);
    }

    @Override
    public boolean updateById(Recipe entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }

    @Override
    public ApiResponse<Page<Recipe>> page(RecipePageDTO dto) {
        Page<Recipe> page = new Page<>(dto.getCurrent(), dto.getSize());
        Page<Recipe> result = lambdaQuery()
                .like(StringUtils.hasText(dto.getRecipeName()), Recipe::getRecipeName, dto.getRecipeName())
                .eq(StringUtils.hasText(dto.getCategoryId()), Recipe::getCategoryId, dto.getCategoryId())
                .like(StringUtils.hasText(dto.getCategoryName()), Recipe::getCategoryName, dto.getCategoryName())
                .eq(StringUtils.hasText(dto.getDifficulty()), Recipe::getDifficulty, dto.getDifficulty())
                .like(StringUtils.hasText(dto.getTaste()), Recipe::getTaste, dto.getTaste())
                .eq(StringUtils.hasText(dto.getStatus()), Recipe::getStatus, dto.getStatus())
                .orderByDesc(Recipe::getCreateTime)
                .page(page);
        return ApiResponse.success(result);
    }

    @Override
    public ApiResponse<RecipeDetailVO> getDetailById(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        Recipe recipe = super.getById(id);
        if (recipe == null) {
            return ApiResponse.fail("菜谱不存在");
        }
        return ApiResponse.success(buildDetailVO(recipe));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<RecipeDetailVO> create(RecipeSaveDTO dto) {
        if (dto == null || dto.getRecipe() == null) {
            return ApiResponse.fail("菜谱信息不能为空");
        }
        Recipe recipe = dto.getRecipe();
        ApiResponse<Void> validateResult = validateRecipe(recipe, null, dto.getRecipeStepList());
        if (validateResult != null) {
            return ApiResponse.fail(validateResult.getMessage());
        }
        save(recipe);
        ApiResponse<Void> saveChildrenResult = saveChildren(recipe.getId(), dto);
        if (saveChildrenResult.getStatus() != 200) {
            return ApiResponse.fail(saveChildrenResult.getMessage());
        }
        return ApiResponse.success("保存成功", buildDetailVO(recipe));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> update(RecipeSaveDTO dto) {
        if (dto == null || dto.getRecipe() == null) {
            return ApiResponse.fail("菜谱信息不能为空");
        }
        Recipe recipe = dto.getRecipe();
        if (!StringUtils.hasText(recipe.getId())) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        if (super.getById(recipe.getId()) == null) {
            return ApiResponse.fail("菜谱不存在");
        }
        ApiResponse<Void> validateResult = validateRecipe(recipe, recipe.getId(), dto.getRecipeStepList());
        if (validateResult != null) {
            return ApiResponse.fail(validateResult.getMessage());
        }
        updateById(recipe);
        ApiResponse<Void> saveChildrenResult = saveChildren(recipe.getId(), dto);
        if (saveChildrenResult.getStatus() != 200) {
            return ApiResponse.fail(saveChildrenResult.getMessage());
        }
        return ApiResponse.success("更新成功", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> delete(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        if (super.getById(id) == null) {
            return ApiResponse.fail("菜谱不存在");
        }
        clearChildrenByRecipeId(id);
        userFavoriteService.deleteByRecipeId(id);
        recipeViewRecordService.deleteByRecipeId(id);
        removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    private ApiResponse<Void> validateRecipe(Recipe recipe, String excludeId, List<RecipeStep> stepList) {
        if (!StringUtils.hasText(recipe.getRecipeName())) {
            return ApiResponse.fail("菜谱名称不能为空");
        }
        if (lambdaQuery()
                .ne(StringUtils.hasText(excludeId), Recipe::getId, excludeId)
                .eq(Recipe::getRecipeName, recipe.getRecipeName())
                .exists()) {
            return ApiResponse.fail("菜谱名称已存在");
        }
        if (StringUtils.hasText(recipe.getCategoryId())) {
            RecipeCategory category = recipeCategoryService.lambdaQuery()
                    .eq(RecipeCategory::getId, recipe.getCategoryId())
                    .one();
            if (category == null) {
                return ApiResponse.fail("分类不存在");
            }
            recipe.setCategoryName(category.getCategoryName());
        }
        String stepError = validateStepNoDuplicate(stepList);
        if (stepError != null) {
            return ApiResponse.fail(stepError);
        }
        return null;
    }

    private String validateStepNoDuplicate(List<RecipeStep> stepList) {
        if (stepList == null || stepList.isEmpty()) {
            return null;
        }
        Set<Integer> stepNos = new HashSet<>();
        for (RecipeStep step : stepList) {
            if (step.getStepNo() == null) {
                return "步骤序号不能为空";
            }
            if (!stepNos.add(step.getStepNo())) {
                return "步骤序号不能重复";
            }
        }
        return null;
    }

    private ApiResponse<Void> saveChildren(String recipeId, RecipeSaveDTO dto) {
        RecipeStepBatchDTO stepBatchDTO = new RecipeStepBatchDTO();
        stepBatchDTO.setRecipeId(recipeId);
        stepBatchDTO.setStepList(dto.getRecipeStepList());
        ApiResponse<Void> stepResult = recipeStepService.saveBatchByRecipeId(stepBatchDTO);
        if (stepResult.getStatus() != 200) {
            return stepResult;
        }

        RecipeIngredientRelBatchDTO relBatchDTO = new RecipeIngredientRelBatchDTO();
        relBatchDTO.setRecipeId(recipeId);
        relBatchDTO.setRelList(dto.getIngredientList());
        ApiResponse<Void> relResult = recipeIngredientRelService.saveBatchByRecipeId(relBatchDTO);
        if (relResult.getStatus() != 200) {
            return relResult;
        }

        RecipeImageBatchDTO imageBatchDTO = new RecipeImageBatchDTO();
        imageBatchDTO.setRecipeId(recipeId);
        imageBatchDTO.setImageList(dto.getImageList());
        ApiResponse<Void> imageResult = recipeImageService.saveBatchByRecipeId(imageBatchDTO);
        if (imageResult.getStatus() != 200) {
            return imageResult;
        }

        RecipeSeasoningRelBatchDTO seasoningBatchDTO = new RecipeSeasoningRelBatchDTO();
        seasoningBatchDTO.setRecipeId(recipeId);
        seasoningBatchDTO.setRelList(dto.getSeasoningList());
        return recipeSeasoningRelService.saveBatchByRecipeId(seasoningBatchDTO);
    }

    private void clearChildrenByRecipeId(String recipeId) {
        RecipeStepBatchDTO stepBatchDTO = new RecipeStepBatchDTO();
        stepBatchDTO.setRecipeId(recipeId);
        recipeStepService.saveBatchByRecipeId(stepBatchDTO);

        RecipeIngredientRelBatchDTO relBatchDTO = new RecipeIngredientRelBatchDTO();
        relBatchDTO.setRecipeId(recipeId);
        recipeIngredientRelService.saveBatchByRecipeId(relBatchDTO);

        RecipeImageBatchDTO imageBatchDTO = new RecipeImageBatchDTO();
        imageBatchDTO.setRecipeId(recipeId);
        recipeImageService.saveBatchByRecipeId(imageBatchDTO);

        RecipeSeasoningRelBatchDTO seasoningBatchDTO = new RecipeSeasoningRelBatchDTO();
        seasoningBatchDTO.setRecipeId(recipeId);
        recipeSeasoningRelService.saveBatchByRecipeId(seasoningBatchDTO);
    }

    private RecipeDetailVO buildDetailVO(Recipe recipe) {
        RecipeDetailVO vo = new RecipeDetailVO();
        vo.setRecipe(recipe);
        vo.setRecipeStepList(recipeStepService.listByRecipeId(recipe.getId()).getData());
        vo.setIngredientList(recipeIngredientRelService.listByRecipeId(recipe.getId()).getData());
        vo.setSeasoningList(recipeSeasoningRelService.listByRecipeId(recipe.getId()).getData());
        vo.setImageList(recipeImageService.listByRecipeId(recipe.getId()).getData());
        return vo;
    }
}
