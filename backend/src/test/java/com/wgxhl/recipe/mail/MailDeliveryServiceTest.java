package com.wgxhl.recipe.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.ses.v20201002.SesClient;
import com.tencentcloudapi.ses.v20201002.models.SendEmailRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MailDeliveryServiceTest {
    private final MailProperties properties = new MailProperties();
    private final JavaMailSender smtp = mock(JavaMailSender.class);
    private final SesClient ses = mock(SesClient.class);
    private final ObjectMapper json = new ObjectMapper();
    private MailDeliveryService delivery;

    @BeforeEach
    void setup() {
        properties.setProvider(MailProperties.Provider.SES);
        properties.setFrom("王师傅生活助手 <no-reply@mail.example.test>");
        properties.getSes().setCodeTemplateId(101L);
        properties.getSes().setResetTemplateId(102L);
        properties.getSes().setNotificationTemplateId(103L);
        delivery = create(smtp, ses);
    }

    @Test
    void codeUsesTemplateWithJsonVariablesAndNoSimpleBody() throws Exception {
        delivery.sendCode("user@example.test", "更换邮箱确认", "012345");
        SendEmailRequest request = request();
        assertEquals(properties.getFrom(), request.getFromEmailAddress());
        assertArrayEquals(new String[]{"user@example.test"}, request.getDestination());
        assertEquals("【王师傅生活助手】更换邮箱确认验证码", request.getSubject());
        assertEquals(101L, request.getTemplate().getTemplateID());
        JsonNode variables = json.readTree(request.getTemplate().getTemplateData());
        assertEquals("012345", variables.get("code").asText());
        assertEquals("更换邮箱确认", variables.get("purpose").asText());
        assertNull(request.getSimple());
        verifyNoInteractions(smtp);
    }

    @Test
    void resetSendsOnlyTokenForFixedDomainTemplate() throws Exception {
        String token = "a".repeat(41) + "_-";
        delivery.sendPasswordReset("user@example.test", "https://recipe.wgxhl.space/#/reset-password?token=" + token);
        SendEmailRequest request = request();
        assertEquals(102L, request.getTemplate().getTemplateID());
        JsonNode variables = json.readTree(request.getTemplate().getTemplateData());
        assertEquals(token, variables.get("token").asText());
        assertEquals(1, variables.size());
        assertFalse(variables.has("url"));
        assertNull(request.getSimple());
    }

    @Test
    void resetRejectsChangedOriginPathOrInjectedQuery() {
        String token = "a".repeat(43);
        for (String url : new String[]{
                "https://example.test/#/reset-password?token=" + token,
                "https://recipe.wgxhl.space.evil.test/#/reset-password?token=" + token,
                "http://recipe.wgxhl.space/#/reset-password?token=" + token,
                "https://recipe.wgxhl.space/#/other?token=" + token,
                "https://recipe.wgxhl.space/#/reset-password?token=" + token + "&next=other",
                "https://recipe.wgxhl.space/#/reset-password?token=short"}) {
            assertThrows(MailSendException.class, () -> delivery.sendPasswordReset("user@example.test", url));
        }
        verifyNoInteractions(smtp, ses);
    }

    @Test
    void notificationSendsStructuredBusinessFields() throws Exception {
        delivery.sendNotification("user@example.test", MailDeliveryService.ReminderType.BIRTHDAY,
                "2026-10-28 09:00", "家人\"生日\"\n提醒");
        SendEmailRequest request = request();
        assertEquals(103L, request.getTemplate().getTemplateID());
        JsonNode variables = json.readTree(request.getTemplate().getTemplateData());
        assertEquals("生日", variables.get("remindType").asText());
        assertEquals("2026-10-28 09:00", variables.get("remindTime").asText());
        assertEquals("家人\"生日\" 提醒", variables.get("remindDetail").asText());
        assertEquals(3, variables.size());
        assertFalse(variables.has("content"));
        assertEquals("【王师傅生活助手】生日提醒", request.getSubject());
    }

    @Test
    void htmlTemplateEscapesUserContentWithoutChangingThePlainTextMode() throws Exception {
        properties.getSes().setHtmlTemplates(true);
        delivery.sendNotification("user@example.test", MailDeliveryService.ReminderType.TODO,
                "2026-10-28 09:00", "采购 <img src=x> & \"牛奶\"");
        JsonNode variables = json.readTree(request().getTemplate().getTemplateData());
        assertEquals("采购 &lt;img src=x&gt; &amp; &quot;牛奶&quot;", variables.get("remindDetail").asText());
    }

    @Test
    void htmlTemplateLimitsEscapedPayloadWithoutSplittingUnicode() throws Exception {
        properties.getSes().setHtmlTemplates(true);
        delivery.sendNotification("user@example.test", MailDeliveryService.ReminderType.TODO,
                "2026-10-28 09:00", "&😀".repeat(100));
        String data = request().getTemplate().getTemplateData();
        assertTrue(data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 800);
        String detail = json.readTree(data).get("remindDetail").asText();
        assertTrue(detail.endsWith("…"));
        assertFalse(detail.contains("�"));
        assertFalse(detail.matches(".*&(?!amp;).*"));
    }

    @Test
    void longReminderDetailsAreLimitedToAnItemSummary() throws Exception {
        delivery.sendNotification("user@example.test", MailDeliveryService.ReminderType.MEAL_PREPARATION,
                "2026-10-28", "菜".repeat(201));
        JsonNode variables = json.readTree(request().getTemplate().getTemplateData());
        assertEquals("备菜", variables.get("remindType").asText());
        assertEquals("菜".repeat(200) + "…", variables.get("remindDetail").asText());
    }

    @Test
    void missingTemplateFailsWithoutFallingBackToSmtp() {
        properties.getSes().setCodeTemplateId(0L);
        assertFalse(delivery.isReady(MailDeliveryService.Kind.CODE));
        assertThrows(MailSendException.class, () -> delivery.sendCode("user@example.test", "注册", "123456"));
        verifyNoInteractions(smtp, ses);
    }

    @Test
    void missingClientOrSenderFailsWithoutSmtpFallback() {
        assertFalse(create(smtp, null).isReady(MailDeliveryService.Kind.CODE));
        properties.setFrom("");
        assertFalse(delivery.isReady(MailDeliveryService.Kind.CODE));
        assertThrows(MailSendException.class, () -> delivery.sendCode("user@example.test", "注册", "123456"));
        verifyNoInteractions(smtp, ses);
    }

    @Test
    void sdkFailureIsSanitizedAndDoesNotRetryOrFallBack() throws Exception {
        when(ses.SendEmail(any())).thenThrow(new TencentCloudSDKException(
                "secret token=private-token code=123456", "request-id", "FailedOperation"));
        MailSendException error = assertThrows(MailSendException.class,
                () -> delivery.sendCode("user@example.test", "注册", "123456"));
        assertFalse(error.toString().contains("private-token"));
        assertFalse(error.toString().contains("123456"));
        assertNull(error.getCause());
        verify(ses, times(1)).SendEmail(any());
        verifyNoInteractions(smtp);
    }

    @Test
    void explicitSmtpRetainsPlaintextCodeAndSender() {
        properties.setProvider(MailProperties.Provider.SMTP);
        properties.setFrom("");
        delivery.sendCode("user@example.test", "注册", "012345");
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(smtp).send(captor.capture());
        assertEquals("smtp@example.test", captor.getValue().getFrom());
        assertTrue(captor.getValue().getText().contains("012345"));
        verifyNoInteractions(ses);
    }

    @Test
    void smtpNotificationIncludesStructuredFieldsAndOptOutInstructions() {
        properties.setProvider(MailProperties.Provider.SMTP);
        delivery.sendNotification("user@example.test", MailDeliveryService.ReminderType.TODO, "2026-10-28 18:00", "做饭");
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(smtp).send(captor.capture());
        String text = captor.getValue().getText();
        assertTrue(text.contains("提醒类型：待办事项"));
        assertTrue(text.contains("事项时间：2026-10-28 18:00"));
        assertTrue(text.contains("事项名称或菜品：做饭"));
        assertTrue(text.contains("关闭邮件通知"));
    }

    @Test
    void smtpResetRetainsItsConfiguredUrl() {
        properties.setProvider(MailProperties.Provider.SMTP);
        String url = "https://test.example.test/#/reset-password?token=" + "a".repeat(43);
        delivery.sendPasswordReset("user@example.test", url);
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(smtp).send(captor.capture());
        assertTrue(captor.getValue().getText().contains(url));
        verifyNoInteractions(ses);
    }

    @Test
    void smtpMissingSenderIsNotReady() {
        properties.setProvider(MailProperties.Provider.SMTP);
        assertFalse(create(null, ses).isReady(MailDeliveryService.Kind.CODE));
    }

    private SendEmailRequest request() throws Exception {
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(ses).SendEmail(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private MailDeliveryService create(JavaMailSender smtpSender, SesClient sesClient) {
        ObjectProvider<JavaMailSender> smtpProvider = mock(ObjectProvider.class);
        ObjectProvider<SesClient> sesProvider = mock(ObjectProvider.class);
        when(smtpProvider.getIfAvailable()).thenReturn(smtpSender);
        when(sesProvider.getIfAvailable()).thenReturn(sesClient);
        return new MailDeliveryService(properties, smtpProvider, sesProvider, json,
                mock(com.wgxhl.recipe.mail.mapper.MailMessageLogMapper.class), "smtp@example.test");
    }
}
