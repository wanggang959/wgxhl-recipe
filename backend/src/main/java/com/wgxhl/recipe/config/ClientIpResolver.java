package com.wgxhl.recipe.config;

import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * 解析客户端 IP。后端只在 127.0.0.1 上被 OpenResty 访问，
 * X-Real-IP 由 nginx 用 $remote_addr 覆盖写入，可信。
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
