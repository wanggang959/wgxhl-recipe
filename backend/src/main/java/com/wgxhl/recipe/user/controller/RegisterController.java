package com.wgxhl.recipe.user.controller;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.user.dto.RegisterCodeDTO;
import com.wgxhl.recipe.user.dto.RegisterEmailDTO;
import com.wgxhl.recipe.user.dto.RegisterInviteDTO;
import com.wgxhl.recipe.user.dto.RegisterInviteInfoDTO;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.service.UserRegistrationService;
import com.wgxhl.recipe.user.vo.RegisterInviteInfoVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.wgxhl.recipe.config.ClientIpResolver;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/user/register")
public class RegisterController {

    private final UserRegistrationService userRegistrationService;
    private final ClientIpResolver clientIpResolver;

    public RegisterController(UserRegistrationService userRegistrationService, ClientIpResolver clientIpResolver) {
        this.userRegistrationService = userRegistrationService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/sendCode")
    public ApiResponse<Void> sendCode(@RequestBody RegisterCodeDTO dto, HttpServletRequest request) {
        return userRegistrationService.sendRegisterCode(dto == null ? null : dto.getEmail(), clientIpResolver.resolve(request));
    }

    @PostMapping("/inviteInfo")
    public ApiResponse<RegisterInviteInfoVO> inviteInfo(@RequestBody RegisterInviteInfoDTO dto) {
        return userRegistrationService.inviteInfo(dto == null ? null : dto.getInviteCode());
    }

    @PostMapping("/email")
    public ApiResponse<AppUser> registerByEmail(@RequestBody RegisterEmailDTO dto) {
        return userRegistrationService.registerByEmail(dto);
    }

    @PostMapping("/invite")
    public ApiResponse<AppUser> registerByInvite(@RequestBody RegisterInviteDTO dto) {
        return userRegistrationService.registerByInvite(dto);
    }
}
