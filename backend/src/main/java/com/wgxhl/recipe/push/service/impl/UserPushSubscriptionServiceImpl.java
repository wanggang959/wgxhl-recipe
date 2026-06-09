package com.wgxhl.recipe.push.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.PushProperties;
import com.wgxhl.recipe.push.dto.PushSubscriptionDTO;
import com.wgxhl.recipe.push.entity.UserPushSubscription;
import com.wgxhl.recipe.push.mapper.UserPushSubscriptionMapper;
import com.wgxhl.recipe.push.service.UserPushSubscriptionService;
import com.wgxhl.recipe.push.vo.PushSubscriptionStatusVO;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class UserPushSubscriptionServiceImpl extends ServiceImpl<UserPushSubscriptionMapper, UserPushSubscription>
        implements UserPushSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(UserPushSubscriptionServiceImpl.class);

    private final PushProperties pushProperties;
    private final ObjectMapper objectMapper;
    private PushService pushService;

    public UserPushSubscriptionServiceImpl(PushProperties pushProperties, ObjectMapper objectMapper) {
        this.pushProperties = pushProperties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initPushService() {
        if (!isConfigured()) {
            log.info("Web Push delivery disabled, VAPID public/private key is not configured.");
            return;
        }
        try {
            pushService = new PushService(
                    pushProperties.getVapid().getPublicKey(),
                    pushProperties.getVapid().getPrivateKey(),
                    pushProperties.getVapid().getSubject()
            );
            log.info("Web Push delivery initialized, subject={}, ttlSeconds={}",
                    pushProperties.getVapid().getSubject(), pushProperties.getTtlSeconds());
        } catch (Exception ex) {
            pushService = null;
            log.warn("Web Push VAPID config is invalid, push delivery disabled.", ex);
        }
    }

    @Override
    public boolean save(UserPushSubscription entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return super.save(entity);
    }

    @Override
    public boolean updateById(UserPushSubscription entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }

    @Override
    public ApiResponse<PushSubscriptionStatusVO> status(String userId) {
        PushSubscriptionStatusVO vo = baseStatus();
        vo.setSubscribed(isSubscribed(userId));
        return ApiResponse.success(vo);
    }

    @Override
    public ApiResponse<PushSubscriptionStatusVO> subscribe(String userId, PushSubscriptionDTO dto) {
        if (!StringUtils.hasText(userId)) {
            return ApiResponse.fail(401, "Login required");
        }
        if (!isConfigured()) {
            return ApiResponse.fail("Push notification is not configured");
        }
        if (dto == null || !StringUtils.hasText(dto.getEndpoint())
                || !StringUtils.hasText(dto.getP256dh())
                || !StringUtils.hasText(dto.getAuth())) {
            return ApiResponse.fail("Invalid push subscription");
        }

        remove(new LambdaQueryWrapper<UserPushSubscription>()
                .eq(UserPushSubscription::getEndpoint, dto.getEndpoint()));

        UserPushSubscription entity = new UserPushSubscription();
        entity.setUserId(userId);
        entity.setEndpoint(dto.getEndpoint());
        entity.setP256dh(dto.getP256dh());
        entity.setAuth(dto.getAuth());
        entity.setUserAgent(dto.getUserAgent());
        entity.setEnabled(true);
        save(entity);

        PushSubscriptionStatusVO vo = baseStatus();
        vo.setSubscribed(true);
        return ApiResponse.success("Push notification enabled", vo);
    }

    @Override
    public ApiResponse<PushSubscriptionStatusVO> unsubscribe(String userId, PushSubscriptionDTO dto) {
        if (!StringUtils.hasText(userId)) {
            return ApiResponse.fail(401, "Login required");
        }
        if (dto != null && StringUtils.hasText(dto.getEndpoint())) {
            remove(new LambdaQueryWrapper<UserPushSubscription>()
                    .eq(UserPushSubscription::getUserId, userId)
                    .eq(UserPushSubscription::getEndpoint, dto.getEndpoint()));
        } else {
            remove(new LambdaQueryWrapper<UserPushSubscription>()
                    .eq(UserPushSubscription::getUserId, userId));
        }

        PushSubscriptionStatusVO vo = baseStatus();
        vo.setSubscribed(isSubscribed(userId));
        return ApiResponse.success("Push notification disabled", vo);
    }

    @Override
    public int sendToUserIds(List<String> targetUserIds, String title, String body, String url, String tag) {
        log.info("Web Push send requested, rawTargets={}, title={}, tag={}, configured={}",
                targetUserIds == null ? 0 : targetUserIds.size(), title, tag, pushService != null);
        if (pushService == null) {
            log.info("Web Push send skipped, reason=not_configured_or_invalid_vapid");
            return 0;
        }
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            log.info("Web Push send skipped, reason=no_target_user");
            return 0;
        }
        List<String> userIds = targetUserIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (userIds.isEmpty()) {
            log.info("Web Push send skipped, reason=no_valid_target_user");
            return 0;
        }

        List<UserPushSubscription> subscriptions = lambdaQuery()
                .in(UserPushSubscription::getUserId, userIds)
                .eq(UserPushSubscription::getEnabled, true)
                .list();
        if (subscriptions.isEmpty()) {
            log.info("Web Push send skipped, targetUserIds={}, reason=no_enabled_subscription", userIds);
            return 0;
        }

        log.info("Web Push sending, targetUserIds={}, subscriptionCount={}", userIds, subscriptions.size());
        Map<String, Object> payload = buildPushPayload(title, body, url, tag);
        int delivered = 0;
        for (UserPushSubscription subscription : subscriptions) {
            if (sendToSubscription(subscription, payload)) {
                delivered++;
            }
        }
        log.info("Web Push send finished, targetUserIds={}, subscriptionCount={}, delivered={}",
                userIds, subscriptions.size(), delivered);
        return delivered;
    }

    private Map<String, Object> buildPushPayload(String title, String body, String url, String tag) {
        String targetUrl = StringUtils.hasText(url) ? url : "/#/todo";
        String notifyTag = StringUtils.hasText(tag) ? tag : "wgxhl-recipe";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", StringUtils.hasText(title) ? title : "备菜提醒");
        payload.put("body", StringUtils.hasText(body) ? body : "有人更新了想吃菜单");
        payload.put("icon", "/pwa-icon-192.png");
        payload.put("badge", "/pwa-icon-192.png");
        payload.put("tag", notifyTag);
        payload.put("url", targetUrl);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", targetUrl);
        payload.put("data", data);
        return payload;
    }

    @Override
    public void deleteByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        remove(new LambdaQueryWrapper<UserPushSubscription>().eq(UserPushSubscription::getUserId, userId));
    }

    private boolean sendToSubscription(UserPushSubscription subscription, Map<String, Object> payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            Notification notification = new Notification(
                    subscription.getEndpoint(),
                    subscription.getP256dh(),
                    subscription.getAuth(),
                    payloadJson.getBytes(StandardCharsets.UTF_8),
                    pushProperties.getTtlSeconds()
            );
            HttpResponse response = pushService.send(notification);
            int code = response.getStatusLine().getStatusCode();
            if (isInvalidSubscriptionStatus(code)) {
                log.info("Removed invalid push subscription, status={}, host={}",
                        code, endpointHost(subscription.getEndpoint()));
                removeById(subscription.getId());
                return false;
            }
            if (code < 200 || code >= 300) {
                log.warn("Web Push delivery failed, status={}, host={}",
                        code, endpointHost(subscription.getEndpoint()));
                return false;
            }
            log.info("Web Push delivered, userId={}, host={}", subscription.getUserId(), endpointHost(subscription.getEndpoint()));
            return true;
        } catch (Exception ex) {
            log.warn("Web Push delivery failed, host={}", endpointHost(subscription.getEndpoint()), ex);
            return false;
        }
    }

    /** 订阅失效或 VAPID 与 endpoint 不匹配（常见于换密钥后遗留的旧订阅） */
    private boolean isInvalidSubscriptionStatus(int code) {
        return code == 401 || code == 403 || code == 404 || code == 410;
    }

    private String endpointHost(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return "unknown";
        }
        try {
            return URI.create(endpoint).getHost();
        } catch (Exception ex) {
            return "unknown";
        }
    }

    private PushSubscriptionStatusVO baseStatus() {
        PushSubscriptionStatusVO vo = new PushSubscriptionStatusVO();
        vo.setConfigured(isConfigured());
        vo.setPublicKey(pushProperties.getVapid().getPublicKey());
        vo.setSubscribed(false);
        return vo;
    }

    private boolean isSubscribed(String userId) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }
        return lambdaQuery()
                .eq(UserPushSubscription::getUserId, userId)
                .eq(UserPushSubscription::getEnabled, true)
                .exists();
    }

    private boolean isConfigured() {
        return pushProperties != null
                && pushProperties.getVapid() != null
                && StringUtils.hasText(pushProperties.getVapid().getPublicKey())
                && StringUtils.hasText(pushProperties.getVapid().getPrivateKey());
    }

}
