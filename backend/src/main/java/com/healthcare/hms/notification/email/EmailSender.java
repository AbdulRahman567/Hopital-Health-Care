package com.healthcare.hms.notification.email;

/**
 * Outbound email port (ARCHITECTURE section 3 allows {@code auth -> notification}; CONF-6 "SMTP
 * abstraction + local dev transport").
 *
 * <p>{@code auth} depends on this interface only — never on a transport — so the sending mechanism
 * stays swappable and testable: production may bind {@link SmtpEmailSender}, development binds
 * {@link LoggingEmailSender}, and tests bind a recording fake that captures the link.
 *
 * <p>Sending is synchronous for Phase 5 (CONF-6); the outbox/async retry lands at P18.3 without
 * changing this interface.
 */
public interface EmailSender {

  /**
   * Sends one email.
   *
   * <p>A transport failure propagates as a 500 rather than being swallowed: silently dropping the
   * only link the recipient will ever receive would strand the flow, which FR-1.3 forbids.
   *
   * @param message the message to send
   */
  void send(EmailMessage message);
}
