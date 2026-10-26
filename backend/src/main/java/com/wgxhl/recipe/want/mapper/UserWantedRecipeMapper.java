package com.wgxhl.recipe.want.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wgxhl.recipe.want.entity.UserWantedRecipe;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserWantedRecipeMapper extends BaseMapper<UserWantedRecipe> {

    @InterceptorIgnore(tenantLine = "true")
    @Delete("delete from user_wanted_recipe where recipe_id = #{recipeId}")
    int deleteByRecipeIdAcrossHouseholds(@Param("recipeId") String recipeId);
}
