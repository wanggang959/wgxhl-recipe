package com.wgxhl.recipe.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuestReadOnlyPathTest {

    @Test
    void guestReadsOnlyPublicRecipeBrowsingAndOwnNotificationPaths() {
        String[] allowed = {
                "/health",
                "/user/login",
                "/user/guestLogin",
                "/user/preview",
                "/user/register/sendCode",
                "/user/register/inviteInfo",
                "/user/register/email",
                "/user/register/invite",
                "/user/security/requestReset",
                "/user/security/resetInfo",
                "/user/security/confirmReset",
                "/recipe/page",
                "/recipe/getById",
                "/recipe/batch",
                "/category/page",
                "/favorite/check",
                "/want/check",
                "/notification/page",
                "/notification/unreadCount",
                "/push/status",
        };
        for (String path : allowed) {
            assertTrue(AdminPermissionInterceptor.isReadOnlyPath(path), path);
        }
    }

    @Test
    void guestCannotReadAnyHouseholdOrAccountData() {
        String[] denied = {
                "/user/members",
                "/user/page",
                "/user/getById",
                "/household/list",
                "/household/me",
                "/expense/project/page",
                "/expense/project/getById",
                "/expense/item/page",
                "/expense/project/summary",
                "/todo/page",
                "/todo/getById",
                "/todo/summary",
                "/todo/upcoming",
                "/favorite/page",
                "/want/page",
                "/want/dateList",
                "/want/notifyPreview",
                "/commute/records",
                "/commute/statistics",
                "/ingredient/page",
                "/seasoning/page",
                "/recipe/toggleLike",
                "/recipe/setStatus",
                "/user/security/status",
                "/user/changePassword",
        };
        for (String path : denied) {
            assertFalse(AdminPermissionInterceptor.isReadOnlyPath(path), path);
        }
    }
}
