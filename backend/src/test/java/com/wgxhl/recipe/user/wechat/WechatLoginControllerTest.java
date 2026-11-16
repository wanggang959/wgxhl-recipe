package com.wgxhl.recipe.user.wechat;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.ClientIpResolver;
import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.user.dto.UserLoginDTO;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.service.AppUserService;
import com.wgxhl.recipe.user.service.LoginAttemptService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WechatLoginControllerTest {
    final WechatIdentityClient wechat = mock(WechatIdentityClient.class);
    final JdbcTemplate db = mock(JdbcTemplate.class);
    final AppUserService users = mock(AppUserService.class);
    final LoginAttemptService attempts = mock(LoginAttemptService.class);
    final ClientIpResolver ips = mock(ClientIpResolver.class);
    final WechatLoginController controller = new WechatLoginController(wechat, db, users, attempts, ips);
    final MockHttpServletRequest request = new MockHttpServletRequest();

    @AfterEach void clear() { UserContext.clear(); }
    @Test void unboundIdentityCannotLogIn() {
        when(wechat.exchange("code")).thenReturn("openid");
        when(wechat.appId()).thenReturn("app");
        when(db.query(anyString(), any(RowMapper.class), any(), any())).thenReturn(Collections.emptyList());
        WechatLoginController.LoginRequest dto = new WechatLoginController.LoginRequest(); dto.setCode("code");
        assertEquals(404, controller.login(dto, request).getStatus());
        verify(users, never()).loginWithWechatBinding(anyString());
    }
    @Test void boundIdentityUsesExistingAccountLogin() {
        when(wechat.exchange("code")).thenReturn("openid");
        when(wechat.appId()).thenReturn("app");
        when(db.query(anyString(), any(RowMapper.class), any(), any())).thenReturn(Collections.singletonList("u1"));
        AppUser user = new AppUser(); user.setId("u1");
        when(users.loginWithWechatBinding("u1")).thenReturn(ApiResponse.success(user));
        WechatLoginController.LoginRequest dto = new WechatLoginController.LoginRequest(); dto.setCode("code");
        assertEquals("u1", controller.login(dto, request).getData().getId());
    }
    @Test void bindRequiresPasswordForCurrentUser() {
        AppUser current = new AppUser(); current.setId("u1"); current.setUsername("one"); UserContext.set(current);
        AppUser other = new AppUser(); other.setId("u2");
        when(users.login(any(UserLoginDTO.class), any())).thenReturn(ApiResponse.success(other));
        WechatLoginController.BindRequest dto = new WechatLoginController.BindRequest(); dto.setUsername("two"); dto.setPassword("password"); dto.setCode("code");
        assertEquals(403, controller.bind(dto, request).getStatus());
        verifyNoInteractions(wechat);
        verifyNoInteractions(db);
    }
    @Test void guestCannotBind() {
        AppUser current = new AppUser(); current.setId("guest"); UserContext.set(current);
        assertEquals(403, controller.bind(new WechatLoginController.BindRequest(), request).getStatus());
        verifyNoInteractions(users, wechat, db);
    }
    @Test void duplicateWechatIdentityCannotBindToAnotherUser() {
        AppUser current = new AppUser(); current.setId("u1"); current.setUsername("one"); UserContext.set(current);
        when(users.login(any(UserLoginDTO.class), any())).thenReturn(ApiResponse.success(current));
        when(wechat.exchange("code")).thenReturn("openid"); when(wechat.appId()).thenReturn("app");
        when(db.query(anyString(), any(RowMapper.class), any(), any())).thenReturn(Collections.emptyList());
        when(db.update(anyString(), any(), any(), any())).thenThrow(new DuplicateKeyException("duplicate"));
        WechatLoginController.BindRequest dto = new WechatLoginController.BindRequest(); dto.setUsername("one"); dto.setPassword("password"); dto.setCode("code");
        assertNotEquals(200, controller.bind(dto, request).getStatus());
    }
    @Test void matchingPasswordBindsWechatToCurrentAccount() {
        AppUser current = new AppUser(); current.setId("u1"); current.setUsername("one"); UserContext.set(current);
        when(users.login(any(UserLoginDTO.class), any())).thenReturn(ApiResponse.success(current));
        when(wechat.exchange("code")).thenReturn("openid"); when(wechat.appId()).thenReturn("app");
        when(db.query(anyString(), any(RowMapper.class), any(), any())).thenReturn(Collections.emptyList());
        WechatLoginController.BindRequest dto = new WechatLoginController.BindRequest(); dto.setUsername("one"); dto.setPassword("password"); dto.setCode("code");
        assertEquals(200, controller.bind(dto, request).getStatus());
        verify(db).update(anyString(), eq("u1"), eq("app"), eq("openid"));
    }
    @Test void statusReturnsOnlyBindingStateForCurrentUser() {
        AppUser current = new AppUser(); current.setId("u1"); UserContext.set(current);
        when(wechat.appId()).thenReturn("app");
        when(db.queryForObject(anyString(), eq(Integer.class), eq("u1"), eq("app"))).thenReturn(1);
        assertTrue(controller.status().getData().isBound());
    }
    @Test void unbindRequiresCurrentPasswordAndKeepsOtherUsersBindings() {
        AppUser current = new AppUser(); current.setId("u1"); current.setUsername("one"); current.setPassword("stored-hash"); UserContext.set(current);
        when(wechat.appId()).thenReturn("app");
        WechatLoginController.UnbindRequest dto = new WechatLoginController.UnbindRequest(); dto.setPassword("wrong");
        when(users.login(any(UserLoginDTO.class), any())).thenReturn(ApiResponse.fail("用户名、邮箱或密码错误"));
        assertNotEquals(200, controller.unbind(dto, request).getStatus());
        verifyNoInteractions(db);
        when(users.login(any(UserLoginDTO.class), any())).thenReturn(ApiResponse.success(current));
        dto.setPassword("correct");
        assertEquals(200, controller.unbind(dto, request).getStatus());
        verify(db).update(contains("where user_id=? and app_id=?"), eq("u1"), eq("app"));
    }
    @Test void accountWithoutPasswordCannotUnbind() {
        AppUser current = new AppUser(); current.setId("u1"); current.setUsername("one"); UserContext.set(current);
        when(wechat.appId()).thenReturn("app");
        WechatLoginController.UnbindRequest dto = new WechatLoginController.UnbindRequest(); dto.setPassword("password");
        assertNotEquals(200, controller.unbind(dto, request).getStatus());
        verifyNoInteractions(users, db);
    }
}
