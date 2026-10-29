package com.wgxhl.recipe.config;

import cn.hutool.jwt.JWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgxhl.recipe.admin.PlatformAdminController;
import com.wgxhl.recipe.household.service.HouseholdMemberService;
import com.wgxhl.recipe.household.service.HouseholdService;
import com.wgxhl.recipe.household.entity.Household;
import com.wgxhl.recipe.household.entity.HouseholdMember;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformAdminPermissionTest {
    @Test
    void onlyExactConfiguredIdIsAllowed() {
        assertTrue(AdminPermissionInterceptor.isPlatformAdmin("wang-id", "wang-id"));
        assertFalse(AdminPermissionInterceptor.isPlatformAdmin("", "wang-id"));
        assertFalse(AdminPermissionInterceptor.isPlatformAdmin("wang-id", "other-id"));
        assertFalse(AdminPermissionInterceptor.isPlatformAdmin(null, null));
    }

    @Test
    void interceptorRejectsOtherSuperAdminBeforeHouseholdLookup() throws Exception {
        JwtAuthUtil jwtUtil = mock(JwtAuthUtil.class);
        AppUserMapper users = mock(AppUserMapper.class);
        HouseholdMemberService members = mock(HouseholdMemberService.class);
        AdminPermissionInterceptor interceptor = new AdminPermissionInterceptor(new ObjectMapper(), jwtUtil, users,
                members, mock(HouseholdService.class));
        ReflectionTestUtils.setField(interceptor, "platformAdminUserId", "wang-id");
        JWT jwt = mock(JWT.class);
        when(jwtUtil.verifyAndGet("token")).thenReturn(jwt);
        when(jwtUtil.getUserId(jwt)).thenReturn("other-id");
        AppUser other = new AppUser();
        other.setId("other-id"); other.setStatus("normal"); other.setUserRole("super_admin");
        when(users.selectById("other-id")).thenReturn(other);
        when(jwtUtil.isVersionCurrent(jwt, other)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/recipe/platform-admin/summary");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = new HandlerMethod(mock(PlatformAdminController.class),
                PlatformAdminController.class.getMethod("summary"));
        assertFalse(interceptor.preHandle(request, response, handler));
        assertEquals(403, new ObjectMapper().readTree(response.getContentAsString()).get("status").asInt());
        verifyNoInteractions(members);
    }

    @Test
    void configuredAccountCanReachAdminHandler() throws Exception {
        JwtAuthUtil jwtUtil = mock(JwtAuthUtil.class);
        AppUserMapper users = mock(AppUserMapper.class);
        HouseholdMemberService members = mock(HouseholdMemberService.class);
        HouseholdService households = mock(HouseholdService.class);
        AdminPermissionInterceptor interceptor = new AdminPermissionInterceptor(new ObjectMapper(), jwtUtil, users, members, households);
        ReflectionTestUtils.setField(interceptor, "platformAdminUserId", "wang-id");
        JWT jwt = mock(JWT.class);
        when(jwtUtil.verifyAndGet("token")).thenReturn(jwt);
        when(jwtUtil.getUserId(jwt)).thenReturn("wang-id");
        when(jwtUtil.getHouseholdId(jwt)).thenReturn("home");
        AppUser user = new AppUser(); user.setId("wang-id"); user.setStatus("normal");
        when(users.selectById("wang-id")).thenReturn(user);
        when(jwtUtil.isVersionCurrent(jwt, user)).thenReturn(true);
        HouseholdMember member = new HouseholdMember(); member.setHouseholdId("home"); member.setStatus("normal");
        when(members.find("home", "wang-id")).thenReturn(member);
        Household household = new Household(); household.setId("home"); household.setStatus("normal");
        when(households.getById("home")).thenReturn(household);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/recipe/platform-admin/summary");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = new HandlerMethod(mock(PlatformAdminController.class), PlatformAdminController.class.getMethod("summary"));
        try { assertTrue(interceptor.preHandle(request, response, handler)); }
        finally { interceptor.afterCompletion(request, response, handler, null); }
    }
}
