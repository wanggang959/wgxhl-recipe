package com.wgxhl.recipe.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wgxhl.recipe.user.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {

    @Select("select * from app_user where id = #{id} for update")
    AppUser lockById(@Param("id") String id);

    @Update("update app_user set password=#{password}, auth_version=auth_version+1, update_time=now() where id=#{id}")
    int replacePassword(@Param("id") String id, @Param("password") String password);

    @Update("update app_user set email=#{email}, email_verified=1, auth_version=auth_version+1, update_time=now() where id=#{id}")
    int replaceVerifiedEmail(@Param("id") String id, @Param("email") String email);

    @Select("select * from app_user where username = #{identifier} limit 2")
    List<AppUser> findLoginByUsername(@Param("identifier") String identifier);

    @Select("select * from app_user where lower(trim(email)) = lower(#{identifier}) limit 2")
    List<AppUser> findLoginByEmail(@Param("identifier") String identifier);

    @Select("select count(*) from app_user where lower(trim(email)) = lower(trim(#{email})) " +
            "and (#{excludeId} is null or id != #{excludeId})")
    long countEmailUsedByAnotherUser(@Param("email") String email, @Param("excludeId") String excludeId);
}
