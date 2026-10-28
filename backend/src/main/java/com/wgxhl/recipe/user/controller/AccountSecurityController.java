package com.wgxhl.recipe.user.controller;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.ClientIpResolver;
import com.wgxhl.recipe.user.dto.AccountSecurityDTO;
import com.wgxhl.recipe.user.service.AccountSecurityService;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/user/security")
public class AccountSecurityController {
    private final AccountSecurityService service;
    public AccountSecurityController(AccountSecurityService service) { this.service = service; }

    @PostMapping("/status")
    public ApiResponse<Map<String, Object>> status() { return service.status(); }

    @PostMapping("/requestReset")
    public ApiResponse<Void> requestReset(@RequestBody AccountSecurityDTO dto, HttpServletRequest request) {
        return service.requestReset(dto.getEmail(), ClientIpResolver.resolve(request));
    }

    @PostMapping("/resetInfo")
    public ApiResponse<Void> resetInfo(@RequestBody AccountSecurityDTO dto) { return service.resetInfo(dto.getToken()); }

    @PostMapping("/confirmReset")
    public ApiResponse<Void> confirmReset(@RequestBody AccountSecurityDTO dto) { return service.confirmReset(dto.getToken(), dto.getNewPassword()); }

    @PostMapping("/email/send")
    public ApiResponse<Map<String, Object>> sendEmail(@RequestBody AccountSecurityDTO dto, HttpServletRequest request) {
        return service.sendEmailChange(dto.getNewEmail(), dto.getCurrentPassword(), ClientIpResolver.resolve(request));
    }

    @PostMapping("/email/confirm")
    public ApiResponse<Void> confirmEmail(@RequestBody AccountSecurityDTO dto) {
        return service.confirmEmailChange(dto.getRequestId(), dto.getOldCode(), dto.getNewCode());
    }

    @PostMapping("/member/options")
    public ApiResponse<Map<String, Object>> memberOptions(@RequestBody AccountSecurityDTO dto) { return service.memberOptions(dto.getUserId()); }

    @PostMapping("/member/requestReset")
    public ApiResponse<Void> requestMemberReset(@RequestBody AccountSecurityDTO dto, HttpServletRequest request) {
        return service.requestMemberReset(dto.getUserId(), ClientIpResolver.resolve(request));
    }

    @PostMapping("/member/resetManaged")
    public ApiResponse<Void> resetManaged(@RequestBody AccountSecurityDTO dto) {
        return service.resetManaged(dto.getUserId(), dto.getCurrentPassword(), dto.getNewPassword());
    }
}
