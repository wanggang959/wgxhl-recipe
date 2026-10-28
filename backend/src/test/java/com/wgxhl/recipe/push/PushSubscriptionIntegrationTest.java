package com.wgxhl.recipe.push;

import com.wgxhl.recipe.config.TenantContext;
import com.wgxhl.recipe.push.service.UserPushSubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named = "test.integration.enabled", matches = "true")
class PushSubscriptionIntegrationTest {
    @Autowired private UserPushSubscriptionService subscriptions;
    @Autowired private JdbcTemplate jdbc;
    private String id;
    private String endpoint;

    @BeforeEach
    void setUp() {
        TenantContext.set("00000000-0000-0000-0000-000000000001");
        id = UUID.randomUUID().toString();
        endpoint = "https://push.example.test/" + id;
        jdbc.update("insert into user_push_subscription (id, household_id, user_id, endpoint, p256dh, auth, enabled) values (?, ?, ?, ?, 'test', 'test', 1)",
                id, TenantContext.require(), "user-one", endpoint);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        jdbc.update("delete from user_push_subscription where id = ?", id);
    }

    @Test
    void statusChecksThisEndpointAndUser() {
        assertTrue(subscriptions.status("user-one", endpoint).getData().getSubscribed());
        assertFalse(subscriptions.status("user-one", endpoint + "-other").getData().getSubscribed());
        assertFalse(subscriptions.status("user-two", endpoint).getData().getSubscribed());
        assertTrue(subscriptions.status("user-one", null).getData().getSubscribed());
    }
}
