package com.wgxhl.recipe.push.service.impl;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.jose4j.lang.JoseException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.regex.Pattern;

class CompatiblePushService extends PushService {
    private static final Pattern PADDED_KEY = Pattern.compile("(?i)(\\b(?:dh|p256ecdsa)=[A-Za-z0-9_-]+)={1,2}(?=\\s*(?:;|,|$))");

    CompatiblePushService(String publicKey, String privateKey, String subject) throws GeneralSecurityException {
        super(publicKey, privateKey, subject);
    }

    CompatiblePushService(KeyPair keys, String subject) {
        super(keys, subject);
    }

    @Override
    public HttpPost preparePost(Notification notification, Encoding encoding)
            throws GeneralSecurityException, IOException, JoseException {
        HttpPost post = super.preparePost(notification, encoding);
        post.setConfig(RequestConfig.custom()
                .setConnectTimeout(10000)
                .setConnectionRequestTimeout(10000)
                .setSocketTimeout(10000)
                .build());
        if ("fcm.googleapis.com".equalsIgnoreCase(post.getURI().getHost())) {
            Header header = post.getFirstHeader("Crypto-Key");
            if (header != null) {
                post.setHeader("Crypto-Key", unpadCryptoKey(header.getValue()));
            }
        }
        return post;
    }

    static String unpadCryptoKey(String value) {
        return PADDED_KEY.matcher(value).replaceAll("$1");
    }
}
