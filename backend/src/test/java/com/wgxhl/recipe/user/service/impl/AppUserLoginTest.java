package com.wgxhl.recipe.user.service.impl;

import com.wgxhl.recipe.config.JwtAuthUtil;
import com.wgxhl.recipe.config.TenantContext;
import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.household.entity.Household;
import com.wgxhl.recipe.household.entity.HouseholdMember;
import com.wgxhl.recipe.household.service.HouseholdMemberService;
import com.wgxhl.recipe.household.service.HouseholdService;
import com.wgxhl.recipe.user.dto.UserLoginDTO;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import com.wgxhl.recipe.user.service.LoginAttemptService;
import com.wgxhl.recipe.user.util.PasswordUtil;
import com.wgxhl.recipe.user.util.EmailAddress;
import com.wgxhl.recipe.todo.service.TodoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppUserLoginTest {
    private final AppUserMapper mapper = mock(AppUserMapper.class);
    private final HouseholdMemberService members = mock(HouseholdMemberService.class);
    private final HouseholdService households = mock(HouseholdService.class);
    private final JwtAuthUtil jwt = mock(JwtAuthUtil.class);
    private final TodoService todos = mock(TodoService.class);
    private final AppUserServiceImpl service = new AppUserServiceImpl(
            null, null, null, null, todos, null, jwt, members, households, new LoginAttemptService());
    private AppUser user;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        user = new AppUser();
        user.setId("user-1");
        user.setUsername("cook");
        user.setPassword(PasswordUtil.hash("password"));
        user.setStatus("normal");
        HouseholdMember member = new HouseholdMember();
        member.setHouseholdId("home-1");
        Household household = new Household();
        household.setId("home-1");
        household.setStatus("normal");
        when(members.findFirstByUser("user-1")).thenReturn(member);
        when(households.getById("home-1")).thenReturn(household);
        when(jwt.createToken(any(), eq("home-1"))).thenReturn("token");
    }

    private UserLoginDTO login(String identifier, String password) {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setUsername(identifier);
        dto.setPassword(password);
        return dto;
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
        TenantContext.clear();
    }

    @Test
    void usernameAndEmailBothAuthenticate() {
        when(mapper.findLoginByUsername("cook")).thenReturn(Collections.singletonList(user));
        assertEquals(200, service.login(login(" cook ", "password"), "ip1").getStatus());

        user.setPassword(PasswordUtil.hash("password"));
        when(mapper.findLoginByUsername("COOK@example.com")).thenReturn(Collections.emptyList());
        when(mapper.findLoginByEmail("COOK@example.com")).thenReturn(Collections.singletonList(user));
        assertEquals(200, service.login(login("COOK@example.com", "password"), "ip1").getStatus());
    }

    @Test
    void sharedEmailDoesNotSelectAnArbitraryAccount() {
        when(mapper.findLoginByUsername("shared@example.com")).thenReturn(Collections.emptyList());
        when(mapper.findLoginByEmail("shared@example.com")).thenReturn(Arrays.asList(user, new AppUser()));
        assertEquals(500, service.login(login("shared@example.com", "password"), "ip2").getStatus());
        verifyNoInteractions(members);
    }

    @Test
    void usernameAndEmailShareAccountLock() {
        when(mapper.findLoginByUsername("cook")).thenReturn(Collections.singletonList(user));
        when(mapper.findLoginByUsername("cook@example.com")).thenReturn(Collections.emptyList());
        when(mapper.findLoginByEmail("cook@example.com")).thenReturn(Collections.singletonList(user));
        for (int i = 0; i < 5; i++) {
            assertEquals(500, service.login(login("cook", "wrong"), "ip3").getStatus());
        }
        assertEquals(429, service.login(login("cook@example.com", "password"), "ip4").getStatus());
    }

    @Test
    void memberCannotUseAnEmailAlreadyOwnedByAnotherAccount() {
        UserContext.set(user);
        TenantContext.set("home-1");
        when(mapper.selectById("user-1")).thenReturn(user);
        when(members.find("home-1", "user-1")).thenReturn(new HouseholdMember());
        when(mapper.countEmailUsedByAnotherUser("cook@example.com", "user-1")).thenReturn(1L);
        AppUser edit = new AppUser();
        edit.setId("user-1");
        edit.setEmail(" COOK@example.com ");

        assertEquals(500, service.update(edit).getStatus());
        verify(mapper, never()).updateById(any(AppUser.class));
    }

    @Test
    void invalidMemberEmailIsRejected() {
        UserContext.set(user);
        TenantContext.set("home-1");
        when(mapper.selectById("user-1")).thenReturn(user);
        when(members.find("home-1", "user-1")).thenReturn(new HouseholdMember());
        AppUser edit = new AppUser();
        edit.setId("user-1");
        edit.setEmail("not-an-email");
        assertEquals("请输入有效的邮箱地址", service.update(edit).getMessage());
        verify(mapper, never()).countEmailUsedByAnotherUser(anyString(), anyString());
    }

    @Test
    void emptyMemberEmailIsAllowed() {
        UserContext.set(user);
        TenantContext.set("home-1");
        when(mapper.selectById("user-1")).thenReturn(user);
        when(members.find("home-1", "user-1")).thenReturn(new HouseholdMember());
        AppUser edit = new AppUser();
        edit.setId("user-1");
        edit.setEmail("  ");
        assertEquals(200, service.update(edit).getStatus());
        assertEquals("", edit.getEmail());
        verify(mapper, never()).countEmailUsedByAnotherUser(anyString(), anyString());
    }

    @Test
    void emailFormatEnforcesAddressAndLength() {
        assertTrue(EmailAddress.isValid("person@example.com"));
        assertFalse(EmailAddress.isValid("person@example"));
        assertFalse(EmailAddress.isValid("person name@example.com"));
        assertFalse(EmailAddress.isValid(new String(new char[201]).replace('\0', 'a') + "@example.com"));
    }

    @Test
    void invalidProfileDoesNotChangeMemberRole() {
        AppUser owner = new AppUser();
        owner.setId("owner-1");
        UserContext.set(owner, UserContext.MEMBER_OWNER);
        TenantContext.set("home-1");
        HouseholdMember member = new HouseholdMember();
        member.setId("membership-1");
        member.setUserId("user-1");
        member.setMemberRole("member");
        when(mapper.selectById("user-1")).thenReturn(user);
        when(members.find("home-1", "user-1")).thenReturn(member);
        AppUser edit = new AppUser();
        edit.setId("user-1");
        edit.setMemberRole("admin");
        edit.setPublicName("123456789012345678901");
        assertEquals(500, service.update(edit).getStatus());
        verify(members, never()).updateById(any(HouseholdMember.class));
    }
}
