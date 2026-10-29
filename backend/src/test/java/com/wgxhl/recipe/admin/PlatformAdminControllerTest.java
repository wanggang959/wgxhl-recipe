package com.wgxhl.recipe.admin;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.AuthRequestAttributes;
import com.wgxhl.recipe.todo.task.TodoNotifyTask;
import com.wgxhl.recipe.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformAdminControllerTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final PlatformAdminController controller = new PlatformAdminController(db, mock(TodoNotifyTask.class), "admin-id");

    @Test
    void cannotDisableConfiguredAdministrator() {
        ApiResponse<Void> result = controller.status(Map.of("id", "admin-id", "status", "disabled"), new MockHttpServletRequest());
        assertNotEquals(200, result.getStatus());
        verifyNoInteractions(db);
    }

    @Test
    void invalidNoticeFiltersAreRejectedWithoutQuerying() {
        assertNotEquals(200, controller.notices(1, "FAIL", "email", "", "2026-01-02", "2026-01-01").getStatus());
        assertNotEquals(200, controller.notices(1, "FAIL", "invalid", "", "", "").getStatus());
        assertNotEquals(200, controller.notices(1, "FAIL", "email", "", "bad-date", "").getStatus());
        verifyNoInteractions(db);
    }

    @Test
    void changingStatusInvalidatesTokens() {
        when(db.update(anyString(), any(), any(), any())).thenReturn(1);
        AppUser actor = new AppUser(); actor.setId("admin-id");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthRequestAttributes.CURRENT_USER, actor);
        assertEquals(200, controller.status(Map.of("id", "user-id", "status", "disabled"), request).getStatus());
        verify(db).update(contains("auth_version = coalesce(auth_version, 0) + 1"), eq("disabled"), eq("user-id"), eq("disabled"));
    }
}
