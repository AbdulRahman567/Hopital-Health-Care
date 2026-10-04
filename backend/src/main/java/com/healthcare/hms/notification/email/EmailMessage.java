package com.healthcare.hms.notification.email;

/**
 * One outbound email: recipient, subject and plain-text body.
 *
 * <p>The body carries the verification or reset link, so an {@link EmailMessage} is sensitive — it
 * must never reach a production log (decision D4: {@link LoggingEmailSender} is dev-only) and must
 * never be rendered into an API response.
 *
 * @param to recipient address (already validated by the requesting DTO)
 * @param subject subject line
 * @param body plain-text body
 */
public record EmailMessage(String to, String subject, String body) {}
