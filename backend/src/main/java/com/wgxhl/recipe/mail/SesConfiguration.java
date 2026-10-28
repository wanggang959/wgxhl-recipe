package com.wgxhl.recipe.mail;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ses.v20201002.SesClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class SesConfiguration {
    @Bean
    @ConditionalOnProperty(name = "app.mail.provider", havingValue = "ses")
    public SesClient sesClient(MailProperties properties) {
        MailProperties.Ses ses = properties.getSes();
        if (!StringUtils.hasText(ses.getSecretId()) || !StringUtils.hasText(ses.getSecretKey())
                || !StringUtils.hasText(ses.getRegion()) || !StringUtils.hasText(properties.getFrom())) {
            throw new IllegalStateException("SES requires SES_SECRET_ID, SES_SECRET_KEY, SES_REGION and MAIL_FROM");
        }
        if (ses.getCodeTemplateId() == null || ses.getCodeTemplateId() <= 0
                || ses.getResetTemplateId() == null || ses.getResetTemplateId() <= 0
                || ses.getNotificationTemplateId() == null || ses.getNotificationTemplateId() <= 0) {
            throw new IllegalStateException("SES requires all three approved template IDs");
        }
        HttpProfile http = new HttpProfile();
        http.setEndpoint("ses.tencentcloudapi.com");
        http.setConnTimeout(10);
        http.setReadTimeout(10);
        http.setWriteTimeout(10);
        ClientProfile profile = new ClientProfile();
        profile.setHttpProfile(http);
        profile.setDebug(false);
        return new SesClient(new Credential(ses.getSecretId(), ses.getSecretKey()), ses.getRegion(), profile);
    }
}
