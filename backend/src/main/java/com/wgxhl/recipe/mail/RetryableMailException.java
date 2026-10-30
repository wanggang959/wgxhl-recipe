package com.wgxhl.recipe.mail;

import org.springframework.mail.MailSendException;

/** A provider explicitly rejected the request before accepting the message. */
public class RetryableMailException extends MailSendException {
    private final int retryAfterMinutes;
    public RetryableMailException(String message) { this(message, 1); }
    public RetryableMailException(String message, int retryAfterMinutes) {
        super(message); this.retryAfterMinutes = Math.max(1, retryAfterMinutes);
    }
    public int getRetryAfterMinutes() { return retryAfterMinutes; }
}
