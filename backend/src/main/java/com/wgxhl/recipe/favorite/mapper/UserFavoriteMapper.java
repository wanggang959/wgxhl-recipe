package com.wgxhl.recipe.favorite.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wgxhl.recipe.favorite.entity.UserFavorite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {

    @InterceptorIgnore(tenantLine = "true")
    @Delete("delete from user_favorite where recipe_id = #{recipeId}")
    int deleteByRecipeIdAcrossHouseholds(@Param("recipeId") String recipeId);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>",
            "select recipe_id as recipeId, count(distinct user_id) as favoriteCount from user_favorite",
            "where recipe_id in",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "group by recipe_id", "</script>"})
    List<FavoriteCount> countByRecipeIds(@Param("ids") List<String> ids);
}
