package com.wgxhl.recipe.push.service.impl;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import org.apache.http.client.methods.HttpPost;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;

import static org.junit.jupiter.api.Assertions.*;

class CompatiblePushServiceTest {
    @BeforeAll
    static void provider() {
        if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
    }

    private KeyPair keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", "BC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    @Test
    void fcmHeadersAreUnpaddedForBothEncodings() throws Exception {
        CompatiblePushService service = new CompatiblePushService(keys(), "mailto:test@example.com");
        for (Encoding encoding : new Encoding[]{Encoding.AESGCM, Encoding.AES128GCM}) {
            Notification notification = new Notification("https://fcm.googleapis.com/fcm/send/test",
                    keys().getPublic(), new byte[16], "test".getBytes(), 60);
            HttpPost post = service.preparePost(notification, encoding);
            String header = post.getFirstHeader("Crypto-Key").getValue();
            assertFalse(header.matches(".*(?:dh|p256ecdsa)=[A-Za-z0-9_-]+={1,2}(?:;|$).*"), header);
            assertTrue(header.contains("p256ecdsa="));
        }
    }

    @Test
    void normalizesOnlyKeyPadding() {
        assertEquals("dh=abc;p256ecdsa=xyz;other=keep=", CompatiblePushService.unpadCryptoKey("dh=abc=;p256ecdsa=xyz==;other=keep="));
        assertEquals("p256ecdsa=xyz", CompatiblePushService.unpadCryptoKey("p256ecdsa=xyz"));
    }

    @Test
    void appleHeadersAreUnchanged() throws Exception {
        KeyPair signing = keys();
        Notification notification = new Notification("https://web.push.apple.com/test", keys().getPublic(),
                new byte[16], new byte[0], 60);
        HttpPost compatible = new CompatiblePushService(signing, "mailto:test@example.com").preparePost(notification, Encoding.AES128GCM);
        nl.martijndwars.webpush.PushService upstream = new nl.martijndwars.webpush.PushService(signing, "mailto:test@example.com");
        HttpPost original = upstream.preparePost(notification, Encoding.AES128GCM);
        assertEquals(original.getFirstHeader("Crypto-Key").getValue(), compatible.getFirstHeader("Crypto-Key").getValue());
    }

    @Test
    void onlyExpiredOrMissingSubscriptionsAreRemoved() {
        assertFalse(UserPushSubscriptionServiceImpl.isInvalidSubscriptionStatus(401));
        assertFalse(UserPushSubscriptionServiceImpl.isInvalidSubscriptionStatus(403));
        assertTrue(UserPushSubscriptionServiceImpl.isInvalidSubscriptionStatus(404));
        assertTrue(UserPushSubscriptionServiceImpl.isInvalidSubscriptionStatus(410));
        assertFalse(UserPushSubscriptionServiceImpl.isInvalidSubscriptionStatus(500));
    }

    @Test
    void errorClassificationDoesNotReturnResponseContents() {
        assertEquals("invalid_crypto_key_format", UserPushSubscriptionServiceImpl.classifyErrorText("crypto-key header had invalid format"));
        assertEquals("vapid_key_mismatch", UserPushSubscriptionServiceImpl.classifyErrorText("authorization header does not correspond to subscription"));
        assertEquals("invalid_vapid_jwt", UserPushSubscriptionServiceImpl.classifyErrorText("invalid JWT provided"));
        assertEquals("unclassified", UserPushSubscriptionServiceImpl.classifyErrorText("private endpoint or token"));
    }
}
