package com.wgxhl.recipe.mail;

import org.springframework.mail.MailSendException;

/** A provider explicitly rejected the request before accepting the message. */
public class RetryableMailException extends MailSendException {
    public RetryableMailException(String message) { super(message); }
}
