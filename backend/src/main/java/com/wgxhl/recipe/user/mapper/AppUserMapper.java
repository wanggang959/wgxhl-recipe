package com.wgxhl.recipe.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wgxhl.recipe.user.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {

    @Select("select * from app_user where username = #{identifier} limit 2")
    List<AppUser> findLoginByUsername(@Param("identifier") String identifier);

    @Select("select * from app_user where lower(trim(email)) = lower(#{identifier}) limit 2")
    List<AppUser> findLoginByEmail(@Param("identifier") String identifier);

    @Select("select count(*) from app_user where lower(trim(email)) = lower(trim(#{email})) " +
            "and (#{excludeId} is null or id != #{excludeId})")
    long countEmailUsedByAnotherUser(@Param("email") String email, @Param("excludeId") String excludeId);
}
