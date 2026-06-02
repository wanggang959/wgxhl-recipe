package com.wgxhl.recipe.config;

import cn.hutool.jwt.JWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgxhl.recipe.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

@Component
public class AdminPermissionInterceptor implements HandlerInterceptor {

    private static final String ROLE_ADMIN = "admin";

    private final ObjectMapper objectMapper;
    private final JwtAuthUtil jwtAuthUtil;

    public AdminPermissionInterceptor(ObjectMapper objectMapper, JwtAuthUtil jwtAuthUtil) {
        this.objectMapper = objectMapper;
        this.jwtAuthUtil = jwtAuthUtil;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isPublicPath(request.getRequestURI())) {
            return true;
        }

        String token = getBearerToken(request.getHeader("Authorization"));
        JWT jwt = jwtAuthUtil.verifyAndGet(token);
        if (jwt == null) {
            writeJson(response, ApiResponse.fail(401, "登录已失效，请重新登录"));
            return false;
        }

        if (!requiresAdmin(request.getRequestURI())) {
            return true;
        }
        String userRole = jwtAuthUtil.getUserRole(jwt);
        if (ROLE_ADMIN.equals(userRole)) {
            return true;
        }
        writeJson(response, ApiResponse.fail(403, "需要管理员权限"));
        return false;
    }

    private String getBearerToken(String authorization) {
        if (authorization == null || authorization.trim().isEmpty()) {
            return null;
        }
        if (!authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private void writeJson(HttpServletResponse response, ApiResponse<?> body) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private boolean isPublicPath(String uri) {
        String path = normalizePath(uri);
        if (path.equals("/health")) {
            return true;
        }
        if (path.startsWith("/user/")) {
            return path.equals("/user/login");
        }
        return path.endsWith("/page")
                || path.endsWith("/getById")
                || path.endsWith("/check");
    }

    private boolean requiresAdmin(String uri) {
        String path = normalizePath(uri);
        return path.startsWith("/category/")
                || path.startsWith("/ingredient/")
                || path.startsWith("/seasoning/")
                || path.startsWith("/recipe/")
                || path.startsWith("/image/")
                || path.startsWith("/step/")
                || path.startsWith("/relation/")
                || path.startsWith("/seasoningrelation/")
                || path.startsWith("/user/");
    }

    private String normalizePath(String uri) {
        return uri.replaceFirst("^/recipe", "");
    }
}
