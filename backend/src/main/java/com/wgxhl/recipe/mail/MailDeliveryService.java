package com.wgxhl.recipe.mail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hutool.core.util.IdUtil;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.ses.v20201002.SesClient;
import com.tencentcloudapi.ses.v20201002.models.SendEmailRequest;
import com.tencentcloudapi.ses.v20201002.models.SendEmailResponse;
import com.tencentcloudapi.ses.v20201002.models.Template;
import com.wgxhl.recipe.mail.entity.MailMessageLog;
import com.wgxhl.recipe.mail.mapper.MailMessageLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;

@Service
public class MailDeliveryService {
    public enum Kind { CODE, PASSWORD_RESET, NOTIFICATION }
    public enum ReminderType {
        TODO("待办事项"), BIRTHDAY("生日"), MEAL_PREPARATION("备菜");

        private final String label;

        ReminderType(String label) { this.label = label; }
    }

    private static final Logger log = LoggerFactory.getLogger(MailDeliveryService.class);
    private static final String RESET_URL_PREFIX = "https://recipe.wgxhl.space/#/reset-password?token=";
    private static final String NOTICE_FOOTER = "\n\n请打开王师傅生活助手查看完整事项：https://recipe.wgxhl.space/\n"
            + "不想接收此类提醒，可在应用的“我的”中关闭邮件通知。此邮箱不接收回复。";
    private final MailProperties properties;
    private final JavaMailSender smtp;
    private final SesClient ses;
    private final ObjectMapper json;
    private final MailMessageLogMapper messageLog;
    private final String smtpUsername;

    public MailDeliveryService(MailProperties properties, ObjectProvider<JavaMailSender> smtp,
                               ObjectProvider<SesClient> ses, ObjectMapper json,
                               MailMessageLogMapper messageLog,
                               @Value("${spring.mail.username:}") String smtpUsername) {
        this.properties = properties;
        this.smtp = smtp.getIfAvailable();
        this.ses = ses.getIfAvailable();
        this.json = json;
        this.messageLog = messageLog;
        this.smtpUsername = smtpUsername;
    }

    public boolean isReady(Kind kind) {
        if (properties.getProvider() == MailProperties.Provider.SES) {
            Long id = templateId(kind);
            return ses != null && StringUtils.hasText(properties.getFrom()) && id != null && id > 0;
        }
        return smtp != null && StringUtils.hasText(smtpFrom());
    }

    public void sendCode(String recipient, String purpose, String code) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("purpose", purpose);
        variables.put("code", code);
        String text = "你正在进行" + purpose + "操作，验证码：" + code
                + "。10 分钟内有效。非本人操作请忽略此邮件，请勿向他人提供验证码。";
        send(Kind.CODE, recipient, "【王师傅生活助手】" + purpose + "验证码", text, variables);
    }

    public void sendPasswordReset(String recipient, String url) {
        Map<String, String> variables = Collections.emptyMap();
        if (properties.getProvider() == MailProperties.Provider.SES) {
            // The approved SES template fixes the origin and path; only the opaque token is variable.
            if (url == null || !url.startsWith(RESET_URL_PREFIX)
                    || !url.substring(RESET_URL_PREFIX.length()).matches("[A-Za-z0-9_-]{43}")) {
                throw new MailSendException("密码重置地址与 SES 模板不匹配");
            }
            variables = Collections.singletonMap("token", url.substring(RESET_URL_PREFIX.length()));
        }
        String text = "你收到了一次密码重置申请。请在 30 分钟内打开以下链接，自行设置新密码：\n"
                + url + "\n\n链接只能使用一次。非本人操作请忽略，不会自动修改密码。请勿转发此链接。";
        send(Kind.PASSWORD_RESET, recipient, "【王师傅生活助手】重置密码", text, variables);
    }

    public void sendNotification(String recipient, ReminderType type, String remindTime, String remindDetail) {
        String detail = remindDetail.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (detail.codePointCount(0, detail.length()) > 200) {
            detail = detail.substring(0, detail.offsetByCodePoints(0, 200)) + "…";
        }
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("remindType", type.label);
        variables.put("remindTime", remindTime);
        variables.put("remindDetail", detail);
        String text = "你好，王师傅生活助手用户：\n\n你已开启家庭生活提醒，本次提醒信息如下：\n"
                + "提醒类型：" + type.label + "\n事项时间：" + remindTime + "\n事项名称或菜品：" + detail
                + "\n\n此邮件仅用于你或家人在应用中设置的待办、生日及备菜提醒。" + NOTICE_FOOTER;
        send(Kind.NOTIFICATION, recipient, "【王师傅生活助手】" + type.label + "提醒", text, variables);
    }

    private void send(Kind kind, String recipient, String subject, String text, Map<String, String> variables) {
        if (!isReady(kind)) throw new MailSendException("邮箱服务或邮件模板未配置");
        if (properties.getProvider() == MailProperties.Provider.SMTP) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(smtpFrom());
            message.setTo(recipient);
            message.setSubject(subject);
            message.setText(text);
            smtp.send(message);
            return;
        }
        SendEmailRequest request = new SendEmailRequest();
        request.setFromEmailAddress(properties.getFrom());
        request.setDestination(new String[]{recipient});
        request.setSubject(subject);
        Template template = new Template();
        template.setTemplateID(templateId(kind));
        try {
            template.setTemplateData(templateData(kind, variables));
            request.setTemplate(template);
            SendEmailResponse response = ses.SendEmail(request);
            recordMessage(recipient, kind, response == null ? null : response.getMessageId());
        } catch (JsonProcessingException ex) {
            throw new MailSendException("邮件模板参数生成失败");
        } catch (TencentCloudSDKException ex) {
            // SDK messages may include template data containing verification codes or reset links.
            log.warn("SES delivery failed, kind={}, code={}, requestId={}", kind, ex.getErrorCode(), ex.getRequestId());
            throw new MailSendException("邮件发送失败，请稍后重试");
        }
    }

    private String templateData(Kind kind, Map<String, String> variables) throws JsonProcessingException {
        if (!properties.getSes().isHtmlTemplates()) return json.writeValueAsString(variables);
        Map<String, String> escaped = new LinkedHashMap<>();
        variables.forEach((key, value) -> escaped.put(key, value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8")));
        String result = json.writeValueAsString(escaped);
        String detail = variables.get("remindDetail");
        if (kind == Kind.NOTIFICATION && detail != null) {
            int length = detail.codePointCount(0, detail.length());
            while (result.getBytes(StandardCharsets.UTF_8).length > 800 && length > 0) {
                length--;
                String shortened = detail.substring(0, detail.offsetByCodePoints(0, length)) + "…";
                escaped.put("remindDetail", HtmlUtils.htmlEscape(shortened, "UTF-8"));
                result = json.writeValueAsString(escaped);
            }
        }
        if (result.getBytes(StandardCharsets.UTF_8).length > 800) {
            throw new MailSendException("邮件模板参数过长");
        }
        return result;
    }

    private void recordMessage(String recipient, Kind kind, String messageId) {
        if (!StringUtils.hasText(messageId)) {
            return;
        }
        try {
            MailMessageLog entry = new MailMessageLog();
            entry.setId(IdUtil.simpleUUID());
            entry.setMessageId(messageId);
            entry.setRecipient(recipient);
            entry.setMailKind(kind.name());
            entry.setTemplateId(templateId(kind));
            entry.setCreateTime(java.time.LocalDateTime.now());
            messageLog.insert(entry);
        } catch (Exception ex) {
            // 关联记录失败不应让已受理的邮件失败；回调只能少一条来源信息
            log.warn("Mail message log failed, kind={}, type={}", kind, ex.getClass().getSimpleName());
        }
    }

    private String smtpFrom() {
        return StringUtils.hasText(properties.getFrom()) ? properties.getFrom() : smtpUsername;
    }

    private Long templateId(Kind kind) {
        switch (kind) {
            case CODE: return properties.getSes().getCodeTemplateId();
            case PASSWORD_RESET: return properties.getSes().getResetTemplateId();
            case NOTIFICATION: return properties.getSes().getNotificationTemplateId();
            default: throw new IllegalArgumentException("Unknown mail kind");
        }
    }
}
