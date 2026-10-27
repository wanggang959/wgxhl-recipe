package com.wgxhl.recipe.user.service;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.user.dto.RegisterEmailDTO;
import com.wgxhl.recipe.user.dto.RegisterInviteDTO;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.vo.RegisterInviteInfoVO;

public interface UserRegistrationService {

    ApiResponse<Void> sendRegisterCode(String email);

    ApiResponse<RegisterInviteInfoVO> inviteInfo(String inviteCode);

    ApiResponse<AppUser> registerByEmail(RegisterEmailDTO dto);

    ApiResponse<AppUser> registerByInvite(RegisterInviteDTO dto);
}
