package com.wgxhl.recipe.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "push")
public class PushProperties {

    private Vapid vapid = new Vapid();

    private int ttlSeconds = 604800;

    @Data
    public static class Vapid {
        private String publicKey;
        private String privateKey;
        private String subject;
    }
}
