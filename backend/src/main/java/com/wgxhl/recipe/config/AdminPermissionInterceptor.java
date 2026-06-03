package com.wgxhl.recipe.config;

import cn.hutool.jwt.JWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class AdminPermissionInterceptor implements HandlerInterceptor {

    private static final String ROLE_SUPER_ADMIN = "super_admin";
    private static final String ROLE_ADMIN = "admin";
    private static final String STATUS_NORMAL = "normal";
    private static final String GUEST_ID = "guest";
    private static final String GUEST_USERNAME = "guest";

    private static final Set<String> PUBLIC_USER_PATHS = new HashSet<>(Arrays.asList(
            "/user/login",
            "/user/guestLogin",
            "/user/preview"
    ));

    private final ObjectMapper objectMapper;
    private final JwtAuthUtil jwtAuthUtil;
    private final AppUserMapper appUserMapper;

    public AdminPermissionInterceptor(ObjectMapper objectMapper,
                                      JwtAuthUtil jwtAuthUtil,
                                      AppUserMapper appUserMapper) {
        this.objectMapper = objectMapper;
        this.jwtAuthUtil = jwtAuthUtil;
        this.appUserMapper = appUserMapper;
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

        String userId = jwtAuthUtil.getUserId(jwt);
        if (!StringUtils.hasText(userId)) {
            writeJson(response, ApiResponse.fail(401, "登录已失效，请重新登录"));
            return false;
        }
        AppUser currentUser = appUserMapper.selectById(userId);
        if (currentUser == null || !STATUS_NORMAL.equals(currentUser.getStatus())) {
            writeJson(response, ApiResponse.fail(403, disabledAccountMessage(currentUser)));
            return false;
        }

        String userRole = jwtAuthUtil.getUserRole(jwt);
        String path = normalizePath(request.getRequestURI());
        if (requiresSuperAdmin(path)) {
            if (ROLE_SUPER_ADMIN.equals(userRole)) {
                return true;
            }
            writeJson(response, ApiResponse.fail(403, "仅超级管理员王师傅可以禁用或启用账户"));
            return false;
        }
        if (!requiresAdmin(request.getRequestURI())) {
            return true;
        }
        if (isAdminRole(userRole)) {
            return true;
        }
        writeJson(response, ApiResponse.fail(403, "需要管理员权限"));
        return false;
    }

    private boolean isAdminRole(String userRole) {
        return ROLE_ADMIN.equals(userRole) || ROLE_SUPER_ADMIN.equals(userRole);
    }

    private boolean requiresSuperAdmin(String path) {
        return "/user/setStatus".equals(path);
    }

    private String disabledAccountMessage(AppUser user) {
        if (user != null && (GUEST_ID.equals(user.getId()) || GUEST_USERNAME.equals(user.getUsername()))) {
            return "当前系统已禁止游客登录，请联系管理员处理";
        }
        return "您的帐号已被禁用，请联系王师傅处理";
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
        if (PUBLIC_USER_PATHS.contains(path)) {
            return true;
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
        if (uri == null) {
            return "";
        }
        String path = uri.replaceFirst("^/recipe", "");
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
