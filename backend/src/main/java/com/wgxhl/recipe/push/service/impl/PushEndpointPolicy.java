package com.wgxhl.recipe.push.service.impl;

import java.net.URI;
import java.util.Locale;

/** Only browser push-service origins may receive server-side delivery requests. */
final class PushEndpointPolicy {
    private PushEndpointPolicy() {}

    static boolean isAllowed(String endpoint) {
        if (endpoint == null || endpoint.length() > 512) return false;
        try {
            URI uri = new URI(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return "fcm.googleapis.com".equals(host) || "android.googleapis.com".equals(host)
                    || host.endsWith(".push.apple.com")
                    || host.endsWith(".push.services.mozilla.com")
                    || host.endsWith(".notify.windows.com");
        } catch (Exception ex) {
            return false;
        }
    }
}
