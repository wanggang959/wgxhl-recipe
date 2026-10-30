package com.wgxhl.recipe.mail;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {
    public enum Provider { SMTP, SES }

    private Provider provider = Provider.SMTP;
    private String from = "";
    private String callbackToken = "";
    private final Ses ses = new Ses();

    @Getter
    @Setter
    public static class Ses {
        private String secretId = "";
        private String secretKey = "";
        private String region = "ap-guangzhou";
        private Long codeTemplateId = 0L;
        private Long resetTemplateId = 0L;
        private Long notificationTemplateId = 0L;
        private boolean htmlTemplates = false;
    }
}
