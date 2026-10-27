package com.wgxhl.recipe.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wgxhl.recipe.recipe.entity.Recipe;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RecipeMapper extends BaseMapper<Recipe> {
    @Select("select * from recipe where id = #{id} for update")
    Recipe lockById(@Param("id") String id);
}
