package com.wgxhl.recipe.mail;

import com.tencentcloudapi.ses.v20201002.SesClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

class SesConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(MailProperties.class, SesConfiguration.class);

    @Test
    void defaultSmtpDoesNotRequireSesCredentials() {
        context.run(app -> {
            assertNull(app.getStartupFailure());
            assertTrue(app.getBeansOfType(SesClient.class).isEmpty());
        });
    }

    @Test
    void selectedSesRejectsMissingCredentials() {
        context.withPropertyValues("app.mail.provider=ses").run(app -> {
            assertNotNull(app.getStartupFailure());
            assertTrue(app.getStartupFailure().getMessage().contains("SES requires"));
        });
    }

    @Test
    void selectedSesRejectsMissingTemplateIds() {
        credentials().run(app -> {
            assertNotNull(app.getStartupFailure());
            assertTrue(app.getStartupFailure().getMessage().contains("all three approved template IDs"));
        });
    }

    @Test
    void configuredSesUsesGuangzhouHttpsAndBoundedTimeouts() {
        credentials().withPropertyValues("app.mail.ses.code-template-id=101",
                "app.mail.ses.reset-template-id=102", "app.mail.ses.notification-template-id=103").run(app -> {
            assertNull(app.getStartupFailure());
            SesClient client = app.getBean(SesClient.class);
            assertEquals("ap-guangzhou", client.getRegion());
            assertEquals("ses.tencentcloudapi.com", client.getClientProfile().getHttpProfile().getEndpoint());
            assertEquals(10, client.getClientProfile().getHttpProfile().getReadTimeout());
            assertFalse(client.getClientProfile().isDebug());
        });
    }

    @Test
    void misspelledProviderDoesNotSilentlyUseSmtp() {
        context.withPropertyValues("app.mail.provider=sse").run(app -> assertNotNull(app.getStartupFailure()));
    }

    private ApplicationContextRunner credentials() {
        return context.withPropertyValues("app.mail.provider=ses", "app.mail.from=no-reply@example.test",
                "app.mail.ses.secret-id=test-id", "app.mail.ses.secret-key=test-key");
    }
}
