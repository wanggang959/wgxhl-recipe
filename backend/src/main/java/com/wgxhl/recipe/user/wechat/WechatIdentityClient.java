package com.wgxhl.recipe.user.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class WechatIdentityClient {
    private final ObjectMapper mapper;
    @Value("${wechat.miniapp.app-id:}") private String appId;
    @Value("${wechat.miniapp.app-secret:}") private String secret;
    public WechatIdentityClient(ObjectMapper mapper) { this.mapper = mapper; }
    public String appId() { return appId; }
    public String exchange(String code) {
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(secret)) throw new IllegalStateException("微信登录尚未配置");
        if (!StringUtils.hasText(code) || code.length() > 256) throw new IllegalArgumentException("微信登录凭证无效");
        HttpURLConnection connection = null;
        try {
            String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + encode(appId)
                    + "&secret=" + encode(secret) + "&js_code=" + encode(code) + "&grant_type=authorization_code";
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != 200) throw new IllegalStateException("微信服务暂不可用，请稍后重试");
            JsonNode response = mapper.readTree(connection.getInputStream());
            String openId = response.path("openid").asText("");
            if (!StringUtils.hasText(openId) || openId.length() > 128 || response.path("errcode").asInt(0) != 0)
                throw new IllegalStateException("微信登录失败，请重试");
            return openId;
        } catch (IllegalStateException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("微信服务暂不可用，请稍后重试"); }
        finally { if (connection != null) connection.disconnect(); }
    }
    private String encode(String value) throws Exception { return URLEncoder.encode(value, StandardCharsets.UTF_8.name()); }
}
