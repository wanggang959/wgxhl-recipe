package com.wgxhl.recipe.recipe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.category.entity.RecipeCategory;
import com.wgxhl.recipe.category.service.RecipeCategoryService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.TenantContext;
import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.favorite.mapper.UserFavoriteMapper;
import com.wgxhl.recipe.household.service.HouseholdMemberService;
import com.wgxhl.recipe.image.dto.RecipeImageBatchDTO;
import com.wgxhl.recipe.image.service.RecipeImageService;
import com.wgxhl.recipe.ingredient.entity.Ingredient;
import com.wgxhl.recipe.ingredient.mapper.IngredientMapper;
import com.wgxhl.recipe.recipe.dto.RecipePageDTO;
import com.wgxhl.recipe.recipe.dto.RecipeSaveDTO;
import com.wgxhl.recipe.recipe.entity.Recipe;
import com.wgxhl.recipe.recipe.entity.RecipeLike;
import com.wgxhl.recipe.recipe.mapper.RecipeLikeMapper;
import com.wgxhl.recipe.recipe.mapper.RecipeMapper;
import com.wgxhl.recipe.recipe.service.RecipeService;
import com.wgxhl.recipe.recipe.util.RecipeVersionUtil;
import com.wgxhl.recipe.recipe.vo.RecipeDetailVO;
import com.wgxhl.recipe.recipe.vo.RecipeLikeStateVO;
import com.wgxhl.recipe.record.mapper.RecipeViewRecordMapper;
import com.wgxhl.recipe.relation.dto.RecipeIngredientRelBatchDTO;
import com.wgxhl.recipe.relation.entity.RecipeIngredientRel;
import com.wgxhl.recipe.relation.service.RecipeIngredientRelService;
import com.wgxhl.recipe.seasoning.entity.Seasoning;
import com.wgxhl.recipe.seasoning.mapper.SeasoningMapper;
import com.wgxhl.recipe.seasoningrelation.dto.RecipeSeasoningRelBatchDTO;
import com.wgxhl.recipe.seasoningrelation.entity.RecipeSeasoningRel;
import com.wgxhl.recipe.seasoningrelation.service.RecipeSeasoningRelService;
import com.wgxhl.recipe.step.dto.RecipeStepBatchDTO;
import com.wgxhl.recipe.step.entity.RecipeStep;
import com.wgxhl.recipe.step.service.RecipeStepService;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import com.wgxhl.recipe.want.mapper.UserWantedRecipeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecipeServiceImpl extends ServiceImpl<RecipeMapper, Recipe>
        implements RecipeService {

    private static final String STATUS_ON = "上架";
    private static final String STATUS_OFF = "下架";

    private final RecipeCategoryService recipeCategoryService;
    private final RecipeStepService recipeStepService;
    private final RecipeImageService recipeImageService;
    private final RecipeIngredientRelService recipeIngredientRelService;
    private final RecipeSeasoningRelService recipeSeasoningRelService;
    private final UserFavoriteMapper userFavoriteMapper;
    private final RecipeViewRecordMapper recipeViewRecordMapper;
    private final UserWantedRecipeMapper userWantedRecipeMapper;
    private final AppUserMapper appUserMapper;
    private final HouseholdMemberService householdMemberService;
    private final IngredientMapper ingredientMapper;
    private final SeasoningMapper seasoningMapper;
    private final RecipeLikeMapper recipeLikeMapper;

    public RecipeServiceImpl(RecipeCategoryService recipeCategoryService,
                             RecipeStepService recipeStepService,
                             RecipeImageService recipeImageService,
                             RecipeIngredientRelService recipeIngredientRelService,
                             RecipeSeasoningRelService recipeSeasoningRelService,
                             UserFavoriteMapper userFavoriteMapper,
                             RecipeViewRecordMapper recipeViewRecordMapper,
                             UserWantedRecipeMapper userWantedRecipeMapper,
                             AppUserMapper appUserMapper,
                             HouseholdMemberService householdMemberService,
                             IngredientMapper ingredientMapper,
                             SeasoningMapper seasoningMapper,
                             RecipeLikeMapper recipeLikeMapper) {
        this.recipeCategoryService = recipeCategoryService;
        this.recipeStepService = recipeStepService;
        this.recipeImageService = recipeImageService;
        this.recipeIngredientRelService = recipeIngredientRelService;
        this.recipeSeasoningRelService = recipeSeasoningRelService;
        this.userFavoriteMapper = userFavoriteMapper;
        this.recipeViewRecordMapper = recipeViewRecordMapper;
        this.userWantedRecipeMapper = userWantedRecipeMapper;
        this.appUserMapper = appUserMapper;
        this.householdMemberService = householdMemberService;
        this.ingredientMapper = ingredientMapper;
        this.seasoningMapper = seasoningMapper;
        this.recipeLikeMapper = recipeLikeMapper;
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
        String currentUserId = UserContext.requireUserId();
        boolean publicScope = "public".equalsIgnoreCase(dto.getScope());
        boolean manager = UserContext.isHouseholdManager();
        List<String> memberIds = publicScope
                ? Collections.emptyList()
                : householdMemberService.listUserIds(TenantContext.require());
        if (!publicScope && memberIds.isEmpty()) {
            return ApiResponse.success(page);
        }
        String keyword = StringUtils.hasText(dto.getRecipeName()) ? dto.getRecipeName().trim() : "";
        List<String> ownerUserIds = publicScope ? Collections.emptyList() : findOwnerUserIds(keyword, memberIds);

        LambdaQueryWrapper<Recipe> wrapper = new LambdaQueryWrapper<>();
        if (publicScope) {
            wrapper.eq(Recipe::getVisibility, "public")
                    .eq(Recipe::getStatus, STATUS_ON);
        } else {
            wrapper.in(Recipe::getOwnerUserId, memberIds)
                    .and(scope -> scope
                            .in(Recipe::getVisibility, "family", "public")
                            .or()
                            .eq(Recipe::getOwnerUserId, currentUserId))
                    .eq(manager && StringUtils.hasText(dto.getStatus()), Recipe::getStatus, dto.getStatus())
                    .and(!manager, scope -> scope
                            .ne(Recipe::getStatus, STATUS_OFF)
                            .or()
                            .eq(Recipe::getOwnerUserId, currentUserId));
        }
        wrapper.and(StringUtils.hasText(keyword), scope -> {
                    scope.like(Recipe::getRecipeName, keyword)
                            .or()
                            .like(Recipe::getOwnerName, keyword);
                    if (!ownerUserIds.isEmpty()) {
                        scope.or().in(Recipe::getOwnerUserId, ownerUserIds);
                    }
                })
                .eq(StringUtils.hasText(dto.getCategoryId()), Recipe::getCategoryId, dto.getCategoryId())
                .like(StringUtils.hasText(dto.getCategoryName()), Recipe::getCategoryName, dto.getCategoryName())
                .eq(StringUtils.hasText(dto.getDifficulty()), Recipe::getDifficulty, dto.getDifficulty())
                .like(StringUtils.hasText(dto.getTaste()), Recipe::getTaste, dto.getTaste());
        applySort(wrapper, dto.getSort());

        Page<Recipe> result = page(page, wrapper);
        fillLiked(result.getRecords(), currentUserId);
        return ApiResponse.success(result);
    }

    private void applySort(LambdaQueryWrapper<Recipe> wrapper, String sort) {
        String value = StringUtils.hasText(sort) ? sort : "latest";
        if ("likes_desc".equals(value)) {
            wrapper.orderByDesc(Recipe::getLikeCount).orderByDesc(Recipe::getCreateTime);
        } else if ("likes_asc".equals(value)) {
            wrapper.orderByAsc(Recipe::getLikeCount).orderByDesc(Recipe::getCreateTime);
        } else if ("random".equals(value)) {
            wrapper.last("order by rand()");
        } else {
            wrapper.orderByDesc(Recipe::getCreateTime);
        }
    }

    private void fillLiked(List<Recipe> records, String currentUserId) {
        if (records == null || records.isEmpty() || !StringUtils.hasText(currentUserId)) {
            return;
        }
        List<String> recipeIds = records.stream()
                .map(Recipe::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        if (recipeIds.isEmpty()) {
            return;
        }
        Set<String> likedIds = recipeLikeMapper.selectList(new LambdaQueryWrapper<RecipeLike>()
                        .eq(RecipeLike::getUserId, currentUserId)
                        .in(RecipeLike::getRecipeId, recipeIds))
                .stream()
                .map(RecipeLike::getRecipeId)
                .collect(Collectors.toSet());
        records.forEach(recipe -> recipe.setLiked(likedIds.contains(recipe.getId())));
    }

    private List<String> findOwnerUserIds(String keyword, List<String> memberIds) {
        if (!StringUtils.hasText(keyword) || memberIds.isEmpty()) {
            return Collections.emptyList();
        }
        return appUserMapper.selectList(new LambdaQueryWrapper<AppUser>()
                        .in(AppUser::getId, memberIds)
                        .and(wrapper -> wrapper
                                .like(AppUser::getUsername, keyword)
                                .or()
                                .like(AppUser::getNickname, keyword)))
                .stream()
                .map(AppUser::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    @Override
    public ApiResponse<RecipeDetailVO> getDetailById(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        Recipe recipe = super.getById(id);
        if (recipe == null || !canView(recipe)) {
            return ApiResponse.fail("菜谱不存在");
        }
        if (STATUS_OFF.equals(recipe.getStatus()) && !canTakeDown(recipe)) {
            return ApiResponse.fail("这道菜已被下架");
        }
        recipe.setLiked(isLikedBy(id, UserContext.requireUserId()));
        return ApiResponse.success(buildDetailVO(recipe));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<RecipeLikeStateVO> toggleLike(String id) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        Recipe recipe = super.getById(id);
        if (recipe == null || !canView(recipe)) {
            return ApiResponse.fail("菜谱不存在");
        }
        if (STATUS_OFF.equals(recipe.getStatus())) {
            return ApiResponse.fail("这道菜已被下架");
        }
        String currentUserId = UserContext.requireUserId();
        RecipeLike existing = recipeLikeMapper.selectOne(new LambdaQueryWrapper<RecipeLike>()
                .eq(RecipeLike::getRecipeId, id)
                .eq(RecipeLike::getUserId, currentUserId)
                .last("limit 1"));
        boolean liked;
        if (existing == null) {
            RecipeLike like = new RecipeLike();
            like.setId(IdUtil.simpleUUID());
            like.setRecipeId(id);
            like.setUserId(currentUserId);
            LocalDateTime now = LocalDateTime.now();
            like.setCreateTime(now);
            like.setUpdateTime(now);
            recipeLikeMapper.insert(like);
            lambdaUpdate().eq(Recipe::getId, id).setSql("like_count = like_count + 1").update();
            liked = true;
        } else {
            recipeLikeMapper.deleteById(existing.getId());
            lambdaUpdate().eq(Recipe::getId, id).setSql("like_count = greatest(like_count - 1, 0)").update();
            liked = false;
        }
        Recipe fresh = super.getById(id);
        RecipeLikeStateVO state = new RecipeLikeStateVO();
        state.setLiked(liked);
        state.setLikeCount(fresh != null && fresh.getLikeCount() != null ? fresh.getLikeCount() : 0);
        return ApiResponse.success(liked ? "已点赞" : "已取消点赞", state);
    }

    private boolean isLikedBy(String recipeId, String userId) {
        if (!StringUtils.hasText(recipeId) || !StringUtils.hasText(userId)) {
            return false;
        }
        Long count = recipeLikeMapper.selectCount(new LambdaQueryWrapper<RecipeLike>()
                .eq(RecipeLike::getRecipeId, recipeId)
                .eq(RecipeLike::getUserId, userId));
        return count != null && count > 0;
    }

    @Override
    public ApiResponse<Void> setStatus(String id, String status) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("菜谱id不能为空");
        }
        if (!STATUS_ON.equals(status) && !STATUS_OFF.equals(status)) {
            return ApiResponse.fail("菜谱状态无效");
        }
        Recipe recipe = super.getById(id);
        if (recipe == null) {
            return ApiResponse.fail("菜谱不存在");
        }
        if (!canTakeDown(recipe)) {
            return ApiResponse.fail(403, "只有菜谱作者或家庭管理员可以下架菜谱");
        }
        if (status.equals(recipe.getStatus())) {
            return ApiResponse.success(STATUS_OFF.equals(status) ? "这道菜已是下架状态" : "这道菜已在架上", null);
        }
        Recipe patch = new Recipe();
        patch.setId(id);
        patch.setStatus(status);
        updateById(patch);
        return ApiResponse.success(STATUS_OFF.equals(status) ? "已下架" : "已恢复上架", null);
    }

    /**
     * 下架/恢复：作者本人，或菜谱作者所在家庭的 owner/admin
     */
    private boolean canTakeDown(Recipe recipe) {
        String currentUserId = UserContext.requireUserId();
        if (currentUserId.equals(recipe.getOwnerUserId())) {
            return true;
        }
        if ("public".equals(recipe.getVisibility()) && UserContext.isSuperAdmin()) {
            return true;
        }
        if (!UserContext.isHouseholdManager()) {
            return false;
        }
        return householdMemberService.listUserIds(TenantContext.require()).contains(recipe.getOwnerUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<RecipeDetailVO> create(RecipeSaveDTO dto, AppUser creator) {
        if (dto == null || dto.getRecipe() == null) {
            return ApiResponse.fail("菜谱信息不能为空");
        }
        Recipe recipe = dto.getRecipe();
        applyOwner(recipe, creator);
        normalizeVersion(recipe);
        normalizeVisibility(recipe);
        normalizeStatus(recipe);
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
        Recipe existing = super.getById(recipe.getId());
        if (existing == null) {
            return ApiResponse.fail("菜谱不存在");
        }
        if (!UserContext.requireUserId().equals(existing.getOwnerUserId())) {
            return ApiResponse.fail(403, "只能编辑自己的菜谱");
        }
        recipe.setOwnerUserId(existing.getOwnerUserId());
        recipe.setOwnerName(existing.getOwnerName());
        if (!StringUtils.hasText(recipe.getVisibility())) {
            recipe.setVisibility(existing.getVisibility());
        }
        normalizeVersion(recipe);
        normalizeVisibility(recipe);
        normalizeStatus(recipe);
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
        Recipe existing = super.getById(id);
        if (existing == null) {
            return ApiResponse.fail("菜谱不存在");
        }
        if (!UserContext.requireUserId().equals(existing.getOwnerUserId())) {
            return ApiResponse.fail(403, "只能删除自己的菜谱");
        }
        clearChildrenByRecipeId(id);
        recipeLikeMapper.delete(new LambdaQueryWrapper<RecipeLike>().eq(RecipeLike::getRecipeId, id));
        userFavoriteMapper.deleteByRecipeIdAcrossHouseholds(id);
        recipeViewRecordMapper.deleteByRecipeIdAcrossHouseholds(id);
        userWantedRecipeMapper.deleteByRecipeIdAcrossHouseholds(id);
        removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    private void applyOwner(Recipe recipe, AppUser creator) {
        if (recipe == null || creator == null) {
            return;
        }
        recipe.setOwnerUserId(creator.getId());
        if (StringUtils.hasText(creator.getNickname())) {
            recipe.setOwnerName(creator.getNickname().trim());
        } else if (StringUtils.hasText(creator.getUsername())) {
            recipe.setOwnerName(creator.getUsername().trim());
        }
    }

    private void normalizeVersion(Recipe recipe) {
        if (recipe == null) {
            return;
        }
        recipe.setRecipeVersion(RecipeVersionUtil.normalize(recipe.getRecipeVersion()));
    }

    private void normalizeVisibility(Recipe recipe) {
        if (recipe == null) {
            return;
        }
        String visibility = recipe.getVisibility();
        if (!"private".equals(visibility) && !"public".equals(visibility)) {
            recipe.setVisibility("family");
        }
    }

    private void normalizeStatus(Recipe recipe) {
        if (recipe == null) {
            return;
        }
        if (!STATUS_OFF.equals(recipe.getStatus())) {
            recipe.setStatus(STATUS_ON);
        }
    }

    private boolean canView(Recipe recipe) {
        String currentUserId = UserContext.requireUserId();
        if (currentUserId.equals(recipe.getOwnerUserId())) {
            return true;
        }
        if ("public".equals(recipe.getVisibility())) {
            return true;
        }
        if (!"family".equals(recipe.getVisibility())) {
            return false;
        }
        return householdMemberService.listUserIds(TenantContext.require())
                .contains(recipe.getOwnerUserId());
    }

    private ApiResponse<Void> validateRecipe(Recipe recipe, String excludeId, List<RecipeStep> stepList) {
        if (!StringUtils.hasText(recipe.getRecipeName())) {
            return ApiResponse.fail("菜谱名称不能为空");
        }
        if (!RecipeVersionUtil.isValid(recipe.getRecipeVersion())) {
            return ApiResponse.fail("版本号格式不正确，请使用如 1.0、2.1 的两位数字版本");
        }
        if (!StringUtils.hasText(recipe.getOwnerUserId())) {
            return ApiResponse.fail("无法确定上传者，请重新登录后再保存");
        }
        if (lambdaQuery()
                .ne(StringUtils.hasText(excludeId), Recipe::getId, excludeId)
                .eq(Recipe::getRecipeName, recipe.getRecipeName())
                .eq(Recipe::getRecipeVersion, recipe.getRecipeVersion())
                .eq(Recipe::getOwnerUserId, recipe.getOwnerUserId())
                .exists()) {
            String ownerLabel = StringUtils.hasText(recipe.getOwnerName()) ? recipe.getOwnerName() : "该用户";
            return ApiResponse.fail(ownerLabel + "做的" + recipe.getRecipeName()
                    + recipe.getRecipeVersion() + "版本已存在");
        }
        if (StringUtils.hasText(recipe.getCategoryId())) {
            RecipeCategory category = recipeCategoryService.lambdaQuery()
                    .eq(RecipeCategory::getId, recipe.getCategoryId())
                    .one();
            if (category == null) {
                return ApiResponse.fail("分类不存在");
            }
            boolean allowed = "platform".equals(category.getScope())
                    || UserContext.requireUserId().equals(category.getOwnerUserId());
            if (!allowed) {
                return ApiResponse.fail("分类不可用");
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
        List<RecipeIngredientRel> ingredientList = recipeIngredientRelService.listByRecipeId(recipe.getId()).getData();
        List<RecipeSeasoningRel> seasoningList = recipeSeasoningRelService.listByRecipeId(recipe.getId()).getData();
        enrichIngredientImages(ingredientList);
        enrichSeasoningImages(seasoningList);
        vo.setIngredientList(ingredientList);
        vo.setSeasoningList(seasoningList);
        vo.setImageList(recipeImageService.listByRecipeId(recipe.getId()).getData());
        return vo;
    }

    private void enrichIngredientImages(List<RecipeIngredientRel> ingredientList) {
        if (ingredientList == null || ingredientList.isEmpty()) {
            return;
        }
        Set<String> ingredientIds = ingredientList.stream()
                .map(RecipeIngredientRel::getIngredientId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (ingredientIds.isEmpty()) {
            return;
        }
        Map<String, String> imageMap = new HashMap<>();
        for (Ingredient ingredient : ingredientMapper.selectBatchIds(ingredientIds)) {
            imageMap.put(ingredient.getId(), ingredient.getIngredientImage());
        }
        ingredientList.forEach(item -> item.setIngredientImage(imageMap.get(item.getIngredientId())));
    }

    private void enrichSeasoningImages(List<RecipeSeasoningRel> seasoningList) {
        if (seasoningList == null || seasoningList.isEmpty()) {
            return;
        }
        Set<String> seasoningIds = seasoningList.stream()
                .map(RecipeSeasoningRel::getSeasoningId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (seasoningIds.isEmpty()) {
            return;
        }
        Map<String, String> imageMap = new HashMap<>();
        for (Seasoning seasoning : seasoningMapper.selectBatchIds(seasoningIds)) {
            imageMap.put(seasoning.getId(), seasoning.getSeasoningImage());
        }
        seasoningList.forEach(item -> item.setSeasoningImage(imageMap.get(item.getSeasoningId())));
    }
}
