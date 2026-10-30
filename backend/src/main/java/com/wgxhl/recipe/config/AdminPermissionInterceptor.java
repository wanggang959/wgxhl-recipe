package com.wgxhl.recipe.config;

import cn.hutool.jwt.JWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.household.entity.Household;
import com.wgxhl.recipe.household.entity.HouseholdMember;
import com.wgxhl.recipe.household.service.HouseholdMemberService;
import com.wgxhl.recipe.household.service.HouseholdService;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.method.HandlerMethod;
import com.wgxhl.recipe.admin.PlatformAdminController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class AdminPermissionInterceptor implements HandlerInterceptor {

    private static final String STATUS_NORMAL = "normal";
    private static final String GUEST_ID = "guest";
    private static final String GUEST_USERNAME = "guest";

    private static final Set<String> PUBLIC_USER_PATHS = new HashSet<>(Arrays.asList(
            "/user/login",
            "/user/guestLogin",
            "/user/preview",
            "/user/register/sendCode",
            "/user/register/inviteInfo",
            "/user/register/email",
            "/user/register/invite",
            "/user/security/requestReset",
            "/user/security/resetInfo",
            "/user/security/confirmReset"
    ));

    private final ObjectMapper objectMapper;
    private final JwtAuthUtil jwtAuthUtil;
    private final AppUserMapper appUserMapper;
    private final HouseholdMemberService householdMemberService;
    private final HouseholdService householdService;

    @Value("${app.platform-admin-user-id:}")
    private String platformAdminUserId;

    public AdminPermissionInterceptor(ObjectMapper objectMapper,
                                      JwtAuthUtil jwtAuthUtil,
                                      AppUserMapper appUserMapper,
                                      HouseholdMemberService householdMemberService,
                                      HouseholdService householdService) {
        this.objectMapper = objectMapper;
        this.jwtAuthUtil = jwtAuthUtil;
        this.appUserMapper = appUserMapper;
        this.householdMemberService = householdMemberService;
        this.householdService = householdService;
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
        if (!jwtAuthUtil.isVersionCurrent(jwt, currentUser)) {
            writeJson(response, ApiResponse.fail(401, "账号安全信息已变更，请重新登录"));
            return false;
        }
        request.setAttribute(AuthRequestAttributes.CURRENT_USER, currentUser);

        String path = normalizePath(request.getRequestURI());
        boolean platformAdminHandler = handler instanceof HandlerMethod
                && PlatformAdminController.class.isAssignableFrom(((HandlerMethod) handler).getBeanType());
        if (platformAdminHandler && !isPlatformAdmin(platformAdminUserId, currentUser.getId())) {
            writeJson(response, ApiResponse.fail(403, "无权访问系统管理后台"));
            return false;
        }
        if (isGuestUser(currentUser) && !isReadOnlyPath(path)) {
            writeJson(response, ApiResponse.fail(403, "游客仅可查看，无法执行此操作"));
            return false;
        }

        String householdId = jwtAuthUtil.getHouseholdId(jwt);
        HouseholdMember membership = householdMemberService.find(householdId, userId);
        if (membership == null) {
            membership = householdMemberService.findFirstByUser(userId);
        }
        if (membership == null || !STATUS_NORMAL.equals(membership.getStatus())) {
            writeJson(response, ApiResponse.fail(403, "该账号尚未加入任何家庭，请联系管理员"));
            return false;
        }
        Household household = householdService.getById(membership.getHouseholdId());
        if (household == null || !STATUS_NORMAL.equals(household.getStatus())) {
            writeJson(response, ApiResponse.fail(403, "所在家庭已停用，请联系管理员"));
            return false;
        }

        if (requiresSuperAdmin(path) && !UserContext.ROLE_SUPER_ADMIN.equals(currentUser.getUserRole())) {
            writeJson(response, ApiResponse.fail(403, "仅超级管理员王师傅可以禁用或启用账户"));
            return false;
        }
        return allow(request, currentUser, household, membership);
    }

    private boolean allow(HttpServletRequest request,
                          AppUser currentUser,
                          Household household,
                          HouseholdMember membership) {
        TenantContext.set(household.getId());
        UserContext.set(currentUser, membership.getMemberRole());
        currentUser.setHouseholdId(household.getId());
        currentUser.setHouseholdName(household.getHouseholdName());
        currentUser.setMemberRole(membership.getMemberRole());
        request.setAttribute(AuthRequestAttributes.CURRENT_HOUSEHOLD, household);
        request.setAttribute(AuthRequestAttributes.CURRENT_MEMBERSHIP, membership);
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        TenantContext.clear();
        UserContext.clear();
    }

    private boolean requiresSuperAdmin(String path) {
        return "/user/setStatus".equals(path);
    }

    static boolean isPlatformAdmin(String configuredId, String userId) {
        return StringUtils.hasText(configuredId) && configuredId.equals(userId);
    }

    private boolean isGuestUser(AppUser user) {
        return user != null && (GUEST_ID.equals(user.getId()) || GUEST_USERNAME.equals(user.getUsername()));
    }

    private boolean isReadOnlyPath(String path) {
        if ("/health".equals(path)) {
            return true;
        }
        if (PUBLIC_USER_PATHS.contains(path)) {
            return true;
        }
        if ("/user/members".equals(path) || "/push/status".equals(path) || "/recipe/batch".equals(path)
                || "/household/me".equals(path) || "/household/list".equals(path)) {
            return true;
        }
        return path.endsWith("/page")
                || path.endsWith("/getById")
                || path.endsWith("/check")
                || path.endsWith("/listByRecipeId")
                || path.endsWith("/summary")
                || path.endsWith("/upcoming")
                || path.endsWith("/unreadCount")
                || path.endsWith("/dateList");
    }

    private String disabledAccountMessage(AppUser user) {
        if (isGuestUser(user)) {
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
        // SES 事件回调不带登录态，靠路径中的随机令牌校验来源
        return "/health".equals(path) || PUBLIC_USER_PATHS.contains(path)
                || path.startsWith("/mail/ses/callback/");
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
