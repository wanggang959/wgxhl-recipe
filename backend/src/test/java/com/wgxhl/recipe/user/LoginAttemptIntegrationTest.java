package com.wgxhl.recipe.user;

import com.wgxhl.recipe.user.service.LoginAttemptService;
import com.wgxhl.recipe.user.util.SecurityTokenUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named = "test.integration.enabled", matches = "true")
class LoginAttemptIntegrationTest {
    @Autowired private LoginAttemptService attempts;
    @Autowired private JdbcTemplate db;

    @Test void lockSurvivesServiceRecreationAndExpires() {
        String key = "test:" + UUID.randomUUID();
        try {
            for (int i=0; i<5; i++) attempts.onFailure(key, "");
            assertNotNull(new LoginAttemptService(db).lockedMessage(key, ""));
            db.update("update login_attempt_bucket set locked_until=now()-interval 1 minute where bucket_type='USER' and bucket_hash=?", SecurityTokenUtil.hash(key));
            assertNull(attempts.lockedMessage(key, ""));
            attempts.onFailure(key, "");
            assertNull(attempts.lockedMessage(key, ""));
        } finally { db.update("delete from login_attempt_bucket where bucket_type='USER' and bucket_hash=?", SecurityTokenUtil.hash(key)); }
    }

    @Test void concurrentFailuresCannotLoseUpdates() throws Exception {
        String key = "test:" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?>[] futures = new Future<?>[5];
            for (int i=0; i<5; i++) futures[i] = pool.submit(() -> { start.await(); attempts.onFailure(key, ""); return null; });
            start.countDown();
            for (Future<?> future : futures) future.get(15, TimeUnit.SECONDS);
            assertNotNull(attempts.lockedMessage(key, ""));
            assertEquals(5, db.queryForObject("select failures from login_attempt_bucket where bucket_type='USER' and bucket_hash=?", Integer.class, SecurityTokenUtil.hash(key)));
        } finally { pool.shutdownNow(); db.update("delete from login_attempt_bucket where bucket_type='USER' and bucket_hash=?", SecurityTokenUtil.hash(key)); }
    }
}
