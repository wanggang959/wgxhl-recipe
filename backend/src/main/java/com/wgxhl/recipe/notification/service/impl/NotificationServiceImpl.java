package com.wgxhl.recipe.notification.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.notification.dto.NotificationPageDTO;
import com.wgxhl.recipe.notification.entity.Notification;
import com.wgxhl.recipe.notification.mapper.NotificationMapper;
import com.wgxhl.recipe.notification.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    @Override
    public boolean save(Notification entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        if (entity.getIsRead() == null) {
            entity.setIsRead(false);
        }
        return super.save(entity);
    }

    @Override
    public ApiResponse<Page<Notification>> page(NotificationPageDTO dto, String currentUserId) {
        Page<Notification> page = new Page<>(dto.getCurrent(), dto.getSize());
        Page<Notification> result = lambdaQuery()
                .eq(StringUtils.hasText(currentUserId), Notification::getUserId, currentUserId)
                .eq(dto.getIsRead() != null, Notification::getIsRead, dto.getIsRead())
                .orderByDesc(Notification::getCreateTime)
                .page(page);
        return ApiResponse.success(result);
    }

    @Override
    public ApiResponse<Long> unreadCount(String currentUserId) {
        long count = lambdaQuery()
                .eq(StringUtils.hasText(currentUserId), Notification::getUserId, currentUserId)
                .eq(Notification::getIsRead, false)
                .count();
        return ApiResponse.success(count);
    }

    @Override
    public ApiResponse<Void> markRead(String id, String currentUserId) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("通知id不能为空");
        }
        Notification notice = super.getById(id);
        if (notice == null) {
            return ApiResponse.fail("通知不存在");
        }
        if (StringUtils.hasText(currentUserId) && !currentUserId.equals(notice.getUserId())) {
            return ApiResponse.fail(403, "不能操作其他成员的通知");
        }
        notice.setIsRead(true);
        notice.setUpdateTime(LocalDateTime.now());
        updateById(notice);
        return ApiResponse.success("已读", null);
    }

    @Override
    public ApiResponse<Void> markAllRead(String currentUserId) {
        lambdaUpdate()
                .eq(StringUtils.hasText(currentUserId), Notification::getUserId, currentUserId)
                .eq(Notification::getIsRead, false)
                .set(Notification::getIsRead, true)
                .set(Notification::getUpdateTime, LocalDateTime.now())
                .update();
        return ApiResponse.success("已全部已读", null);
    }

    @Override
    public ApiResponse<Void> deleteRead(String currentUserId) {
        if (!StringUtils.hasText(currentUserId)) {
            return ApiResponse.fail("请先登录");
        }
        remove(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, currentUserId)
                .eq(Notification::getIsRead, true));
        return ApiResponse.success("已删除已读消息", null);
    }

    @Override
    public Notification createSiteNotice(String userId, String title, String content, String type, String relatedId) {
        Notification notice = new Notification();
        notice.setUserId(userId);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setNotificationType(type);
        notice.setRelatedId(relatedId);
        notice.setIsRead(false);
        save(notice);
        return notice;
    }
}
