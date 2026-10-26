package com.wgxhl.recipe.recipe.service;

import com.wgxhl.recipe.config.UserContext;
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

    public RecipeOwnershipGuard(RecipeMapper recipeMapper) {
        this.recipeMapper = recipeMapper;
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
