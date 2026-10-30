package com.wgxhl.recipe.push.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgxhl.recipe.config.PushProperties;
import com.wgxhl.recipe.config.SecurityProviderConfig;
import com.wgxhl.recipe.push.mapper.UserPushSubscriptionMapper;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PushProviderStartupTest {
    @Test
    void registersProviderBeforeInitializingPushServiceOnColdStart() throws Exception {
        Provider previous = Security.getProvider("BC");
        BouncyCastleProvider generatorProvider = new BouncyCastleProvider();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", generatorProvider);
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        PushProperties properties = new PushProperties();
        properties.getVapid().setPublicKey(Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode((ECPublicKey) pair.getPublic())));
        properties.getVapid().setPrivateKey(Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode((ECPrivateKey) pair.getPrivate())));
        properties.getVapid().setSubject("mailto:test@example.test");
        try {
            Security.removeProvider("BC");
            new ApplicationContextRunner()
                    .withUserConfiguration(UserPushSubscriptionServiceImpl.class, SecurityProviderConfig.class)
                    .withBean(PushProperties.class, () -> properties)
                    .withBean(ObjectMapper.class, ObjectMapper::new)
                    .withBean(UserPushSubscriptionMapper.class, () -> mock(UserPushSubscriptionMapper.class))
                    .run(context -> {
                        assertNotNull(Security.getProvider("BC"));
                        assertNotNull(ReflectionTestUtils.getField(context.getBean(UserPushSubscriptionServiceImpl.class), "pushService"));
                    });
        } finally {
            Security.removeProvider("BC");
            if (previous != null) Security.addProvider(previous);
        }
    }
}
