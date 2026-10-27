package com.wgxhl.recipe.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.user.entity.EmailVerificationCode;

public interface EmailVerificationCodeService extends IService<EmailVerificationCode> {

    ApiResponse<Void> sendRegisterCode(String email);

    String verifyAndConsume(String email, String code);
}
