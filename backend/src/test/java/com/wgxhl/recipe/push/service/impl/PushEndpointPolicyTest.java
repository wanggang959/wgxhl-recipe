package com.wgxhl.recipe.push.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgxhl.recipe.config.PushProperties;
import com.wgxhl.recipe.push.dto.PushSubscriptionDTO;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import static org.junit.jupiter.api.Assertions.*;

class PushEndpointPolicyTest {
    @Test
    void acceptsValidBrowserSubscriptionKeys() throws Exception {
        if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", "BC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        byte[] key = ((org.bouncycastle.jce.interfaces.ECPublicKey) generator.generateKeyPair().getPublic()).getQ().getEncoded(false);
        PushProperties config = new PushProperties();
        config.getVapid().setPublicKey("configured"); config.getVapid().setPrivateKey("configured");
        CapturingService service = new CapturingService(config);
        PushSubscriptionDTO dto = new PushSubscriptionDTO();
        dto.setEndpoint("https://web.push.apple.com/test");
        dto.setP256dh(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(key));
        dto.setAuth(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]));
        assertEquals(200, service.subscribe("user", dto).getStatus());
        assertEquals(dto.getEndpoint(), service.saved.getEndpoint());
        assertEquals("user", service.saved.getUserId());
    }

    private static class CapturingService extends UserPushSubscriptionServiceImpl {
        private com.wgxhl.recipe.push.entity.UserPushSubscription saved;
        CapturingService(PushProperties config) { super(config, new ObjectMapper()); }
        @Override public boolean remove(com.baomidou.mybatisplus.core.conditions.Wrapper<com.wgxhl.recipe.push.entity.UserPushSubscription> wrapper) { return true; }
        @Override public boolean save(com.wgxhl.recipe.push.entity.UserPushSubscription entity) { saved = entity; return true; }
    }

    @Test
    void allowsKnownBrowserPushServices() {
        for (String url : new String[]{"https://fcm.googleapis.com/wp/token", "https://android.googleapis.com/gcm/send/token",
                "https://web.push.apple.com/token", "https://updates.push.services.mozilla.com/wpush/v2/token",
                "https://wns2.notify.windows.com/w/?token=x", "https://FCM.GOOGLEAPIS.COM:443/wp/token"}) {
            assertTrue(PushEndpointPolicy.isAllowed(url), url);
        }
    }

    @Test
    void rejectsPrivateAddressesAndAuthorityTricks() {
        for (String url : new String[]{null, "", "http://fcm.googleapis.com/wp/token", "http://127.0.0.1/test",
                "https://127.0.0.1/", "https://[::1]/", "https://10.0.0.1/", "https://169.254.169.254/",
                "https://localhost/", "file:///etc/passwd", "https://fcm.googleapis.com.evil.test/",
                "https://evilnotify.windows.com/", "https://evil.test/fcm.googleapis.com",
                "https://fcm.googleapis.com@127.0.0.1/", "https://x@fcm.googleapis.com/",
                "https://fcm.googleapis.com:9001/", "https://fcm.googleapis.com./", "https://fcm.googleapis.com/#fragment",
                "https://fcm.googleapis.com\\@127.0.0.1/", "https://%66cm.googleapis.com/"}) {
            assertFalse(PushEndpointPolicy.isAllowed(url), String.valueOf(url));
        }
        assertFalse(PushEndpointPolicy.isAllowed("https://fcm.googleapis.com/" + String.join("", java.util.Collections.nCopies(513, "x"))));
    }

    @Test
    void subscriptionRejectsUnsafeEndpointBeforeDatabaseAccess() {
        PushProperties config = new PushProperties();
        config.getVapid().setPublicKey("configured");
        config.getVapid().setPrivateKey("configured");
        UserPushSubscriptionServiceImpl service = new UserPushSubscriptionServiceImpl(config, new ObjectMapper());
        PushSubscriptionDTO dto = new PushSubscriptionDTO();
        dto.setEndpoint("https://127.0.0.1/test"); dto.setP256dh("test"); dto.setAuth("test");
        assertNotEquals(200, service.subscribe("user", dto).getStatus());
        dto.setEndpoint("https://fcm.googleapis.com/wp/test");
        assertNotEquals(200, service.subscribe("user", dto).getStatus());
    }

    @Test
    void senderRejectsLegacyUnsafeEndpointAndDisablesRedirects() throws Exception {
        if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", "BC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        CompatiblePushService service = new CompatiblePushService(generator.generateKeyPair(), "mailto:test@example.test");
        Notification bad = new Notification("http://127.0.0.1:9999/test", generator.generateKeyPair().getPublic(), new byte[16], new byte[0], 60);
        assertThrows(IOException.class, () -> service.preparePost(bad, Encoding.AES128GCM));
        Notification good = new Notification("https://fcm.googleapis.com/wp/test", generator.generateKeyPair().getPublic(), new byte[16], new byte[0], 60);
        assertFalse(service.preparePost(good, Encoding.AES128GCM).getConfig().isRedirectsEnabled());
    }
}
