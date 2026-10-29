package com.wgxhl.recipe.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ClientIpResolver {
    private final Set<String> trustedProxies;
    public ClientIpResolver(@Value("${app.trusted-proxy-ips:127.0.0.1,::1}") String proxies) {
        trustedProxies = Arrays.stream(proxies.split(",")).map(ClientIpResolver::literalIp)
                .filter(StringUtils::hasText).collect(Collectors.toSet());
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) return "";
        String remote = literalIp(request.getRemoteAddr());
        if (!StringUtils.hasText(remote)) return "";
        if (trustedProxies.contains(remote)) {
            String forwarded = literalIp(request.getHeader("X-Real-IP"));
            if (StringUtils.hasText(forwarded)) return forwarded;
        }
        return remote;
    }

    private static String literalIp(String value) {
        if (!StringUtils.hasText(value)) return "";
        String input = value.trim();
        if (!input.matches("[0-9a-fA-F:.]+") || (!input.contains(".") && !input.contains(":"))) return "";
        try { return InetAddress.getByName(input).getHostAddress(); }
        catch (Exception ignored) { return ""; }
    }
}
