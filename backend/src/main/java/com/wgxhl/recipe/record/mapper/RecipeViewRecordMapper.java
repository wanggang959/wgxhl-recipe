package com.wgxhl.recipe.record.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wgxhl.recipe.record.entity.RecipeViewRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RecipeViewRecordMapper extends BaseMapper<RecipeViewRecord> {

    @InterceptorIgnore(tenantLine = "true")
    @Delete("delete from recipe_view_record where recipe_id = #{recipeId}")
    int deleteByRecipeIdAcrossHouseholds(@Param("recipeId") String recipeId);
}
