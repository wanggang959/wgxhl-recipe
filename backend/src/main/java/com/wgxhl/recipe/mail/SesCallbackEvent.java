package com.wgxhl.recipe.mail;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SesCallbackEvent {
    private String event;
    private String eventid;
    private String email;
    private String messageId;
    private String bounceType;
    private String reason;
    private String subject;
    private Long templateId;
    private Long timestamp;
    private Long sentTimestamp;

    @JsonAnySetter
    public void ignoreCustomHeaders(String key, Object value) {
        // 回调会原样带回邮件头里 X-Tencentcloudses-Cb-* 的自定义内容，这里不存储以免落库不可信文本
    }
}
