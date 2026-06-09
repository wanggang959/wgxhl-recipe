package com.wgxhl.recipe.notification.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.dto.IdDTO;
import com.wgxhl.recipe.config.AuthRequestAttributes;
import com.wgxhl.recipe.notification.dto.NotificationPageDTO;
import com.wgxhl.recipe.notification.entity.Notification;
import com.wgxhl.recipe.notification.service.NotificationService;
import com.wgxhl.recipe.user.entity.AppUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/notification")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/page")
    public ApiResponse<Page<Notification>> page(@RequestBody NotificationPageDTO dto, HttpServletRequest request) {
        return notificationService.page(dto, currentUserId(request));
    }

    @PostMapping("/unreadCount")
    public ApiResponse<Long> unreadCount(HttpServletRequest request) {
        return notificationService.unreadCount(currentUserId(request));
    }

    @PostMapping("/markRead")
    public ApiResponse<Void> markRead(@RequestBody IdDTO dto, HttpServletRequest request) {
        return notificationService.markRead(dto.getId(), currentUserId(request));
    }

    @PostMapping("/markAllRead")
    public ApiResponse<Void> markAllRead(HttpServletRequest request) {
        return notificationService.markAllRead(currentUserId(request));
    }

    @PostMapping("/deleteRead")
    public ApiResponse<Void> deleteRead(HttpServletRequest request) {
        return notificationService.deleteRead(currentUserId(request));
    }

    private String currentUserId(HttpServletRequest request) {
        Object value = request.getAttribute(AuthRequestAttributes.CURRENT_USER);
        return value instanceof AppUser ? ((AppUser) value).getId() : null;
    }
}
