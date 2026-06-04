package com.wgxhl.recipe.push.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.push.dto.PushSubscriptionDTO;
import com.wgxhl.recipe.push.entity.UserPushSubscription;
import com.wgxhl.recipe.push.vo.PushSubscriptionStatusVO;
import java.util.List;

public interface UserPushSubscriptionService extends IService<UserPushSubscription> {

    ApiResponse<PushSubscriptionStatusVO> status(String userId);

    ApiResponse<PushSubscriptionStatusVO> subscribe(String userId, PushSubscriptionDTO dto);

    ApiResponse<PushSubscriptionStatusVO> unsubscribe(String userId, PushSubscriptionDTO dto);

    /** 向指定用户已开启的推送订阅发送通知，返回成功送达的订阅数 */
    int sendToUserIds(List<String> targetUserIds, String title, String body, String url, String tag);

    void deleteByUserId(String userId);
}
