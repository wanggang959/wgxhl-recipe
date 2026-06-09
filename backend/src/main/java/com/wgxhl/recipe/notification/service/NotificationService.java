package com.wgxhl.recipe.notification.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.notification.dto.NotificationPageDTO;
import com.wgxhl.recipe.notification.entity.Notification;

public interface NotificationService extends IService<Notification> {

    ApiResponse<Page<Notification>> page(NotificationPageDTO dto, String currentUserId);

    ApiResponse<Long> unreadCount(String currentUserId);

    ApiResponse<Void> markRead(String id, String currentUserId);

    ApiResponse<Void> markAllRead(String currentUserId);

    ApiResponse<Void> deleteRead(String currentUserId);

    ApiResponse<Void> delete(String id, String currentUserId);

    Notification createSiteNotice(String userId, String title, String content, String type, String relatedId);
}
