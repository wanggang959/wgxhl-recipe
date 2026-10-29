package com.wgxhl.recipe.recipe.service;

import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.config.TenantContext;
import com.wgxhl.recipe.household.service.HouseholdMemberService;
import com.wgxhl.recipe.recipe.entity.Recipe;
import com.wgxhl.recipe.recipe.mapper.RecipeMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 菜谱及其子表（步骤、图片、食材关系、调料关系）的归属校验：只有作者本人可以改动。
 */
@Component
public class RecipeOwnershipGuard {

    private final RecipeMapper recipeMapper;
    private final HouseholdMemberService members;

    public RecipeOwnershipGuard(RecipeMapper recipeMapper, HouseholdMemberService members) {
        this.recipeMapper = recipeMapper;
        this.members = members;
    }

    public String checkReadable(String recipeId) {
        if (!StringUtils.hasText(recipeId)) return "菜谱id不能为空";
        return checkReadable(recipeMapper.selectById(recipeId));
    }

    public String checkReadable(Recipe recipe) {
        if (recipe == null || !canView(recipe)) return "菜谱不存在";
        if ("下架".equals(recipe.getStatus()) && !canViewOffline(recipe)) return "这道菜已被下架";
        return null;
    }

    public boolean canView(Recipe recipe) {
        if (recipe == null) return false;
        String userId = UserContext.requireUserId();
        if (userId.equals(recipe.getOwnerUserId()) || "public".equals(recipe.getVisibility())) return true;
        return "family".equals(recipe.getVisibility())
                && members.listUserIds(TenantContext.require()).contains(recipe.getOwnerUserId());
    }

    public boolean canViewOffline(Recipe recipe) {
        return UserContext.isSuperAdmin() || UserContext.requireUserId().equals(recipe.getOwnerUserId());
    }

    /**
     * @return 校验通过返回 null，否则返回给用户看的错误提示
     */
    public String checkEditable(String recipeId) {
        if (!StringUtils.hasText(recipeId)) {
            return "菜谱id不能为空";
        }
        Recipe recipe = recipeMapper.selectById(recipeId);
        if (recipe == null) {
            return "菜谱不存在";
        }
        if (!UserContext.requireUserId().equals(recipe.getOwnerUserId())) {
            return "只能编辑自己的菜谱";
        }
        return null;
    }
}
