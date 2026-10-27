package com.wgxhl.recipe.favorite.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wgxhl.recipe.favorite.entity.UserFavorite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {

    @InterceptorIgnore(tenantLine = "true")
    @Delete("delete from user_favorite where recipe_id = #{recipeId}")
    int deleteByRecipeIdAcrossHouseholds(@Param("recipeId") String recipeId);
}
