package com.wgxhl.recipe.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {
    @Test void onlyTrustsRealIpFromConfiguredProxy() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1,172.19.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.19.0.1"); request.addHeader("X-Real-IP", "203.0.113.10");
        assertEquals("203.0.113.10", resolver.resolve(request));
        request.setRemoteAddr("172.19.0.5");
        assertEquals("172.19.0.5", resolver.resolve(request));
    }

    @Test void ignoresMalformedOrUntrustedHeaders() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); request.addHeader("X-Real-IP", "evil.example");
        request.addHeader("X-Forwarded-For", "203.0.113.4");
        assertEquals("127.0.0.1", resolver.resolve(request));
    }
}
