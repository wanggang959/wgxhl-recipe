package com.wgxhl.recipe.push.controller;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.AuthRequestAttributes;
import com.wgxhl.recipe.push.dto.PushSubscriptionDTO;
import com.wgxhl.recipe.push.service.UserPushSubscriptionService;
import com.wgxhl.recipe.push.vo.PushSubscriptionStatusVO;
import com.wgxhl.recipe.user.entity.AppUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/push")
public class PushSubscriptionController {

    private final UserPushSubscriptionService userPushSubscriptionService;

    public PushSubscriptionController(UserPushSubscriptionService userPushSubscriptionService) {
        this.userPushSubscriptionService = userPushSubscriptionService;
    }

    @PostMapping("/status")
    public ApiResponse<PushSubscriptionStatusVO> status(HttpServletRequest request) {
        AppUser currentUser = currentUser(request);
        return userPushSubscriptionService.status(currentUser == null ? null : currentUser.getId());
    }

    @PostMapping("/subscribe")
    public ApiResponse<PushSubscriptionStatusVO> subscribe(@RequestBody PushSubscriptionDTO dto,
                                                           HttpServletRequest request) {
        AppUser currentUser = currentUser(request);
        return userPushSubscriptionService.subscribe(currentUser == null ? null : currentUser.getId(), dto);
    }

    @PostMapping("/unsubscribe")
    public ApiResponse<PushSubscriptionStatusVO> unsubscribe(@RequestBody(required = false) PushSubscriptionDTO dto,
                                                             HttpServletRequest request) {
        AppUser currentUser = currentUser(request);
        return userPushSubscriptionService.unsubscribe(currentUser == null ? null : currentUser.getId(), dto);
    }

    private AppUser currentUser(HttpServletRequest request) {
        Object value = request.getAttribute(AuthRequestAttributes.CURRENT_USER);
        return value instanceof AppUser ? (AppUser) value : null;
    }
}
