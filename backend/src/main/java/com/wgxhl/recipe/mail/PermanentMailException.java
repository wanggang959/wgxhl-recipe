package com.wgxhl.recipe.mail;

import org.springframework.mail.MailSendException;

/** A known local validation failure or an explicit non-transient provider rejection. */
public class PermanentMailException extends MailSendException {
    public PermanentMailException(String message) { super(message); }
}
