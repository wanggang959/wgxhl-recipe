package com.wgxhl.recipe.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.wgxhl.recipe.user.entity.EmailVerificationCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmailVerificationCodeMapper extends BaseMapper<EmailVerificationCode> {
    @InterceptorIgnore(tenantLine = "true")
    @Select("select * from email_verification_code where email=#{email} and purpose='REGISTER' and used=0 order by create_time desc limit 1 for update")
    EmailVerificationCode lockLatest(@Param("email") String email);
}
