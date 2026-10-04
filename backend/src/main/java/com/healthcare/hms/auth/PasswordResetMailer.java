package com.healthcare.hms.auth;

import com.healthcare.hms.notification.email.EmailMessage;
import com.healthcare.hms.notification.email.EmailSender;
import org.springframework.stereotype.Component;

/**
 * Builds and sends the password-reset email (decision D4, plan P5.7).
 *
 * <p>A separate mailer from {@link VerificationMailer} rather than a second method on it: the two
 * links point at different pages, carry different tokens with different lifetimes and mean
 * different things if they leak, so sharing a builder would only make it possible for one to grow a
 * flag the other does not have. What they do share is the {@link EmailSender} port, so there is
 * still exactly one way mail leaves the application.
 *
 * <p>The link targets the web application, never the API (API.md section 5): the browser opens it
 * with a GET and the SPA re-presents the token in a POST body. The raw token exists only in this
 * outbound message and in the caller's memory &mdash; only its SHA-256 digest is stored (V4), and
 * neither is ever logged.
 */
@Component
public class PasswordResetMailer {

  static final String SUBJECT = "Reset your password";

  private final EmailSender emailSender;
  private final AuthProperties properties;

  public PasswordResetMailer(EmailSender emailSender, AuthProperties properties) {
    this.emailSender = emailSender;
    this.properties = properties;
  }

  /**
   * Sends the reset link.
   *
   * @param to recipient address
   * @param hospitalName hospital the account belongs to, for the greeting
   * @param rawToken the token itself (only its hash is stored)
   */
  public void send(String to, String hospitalName, String rawToken) {
    String link = properties.getFrontendBaseUrl() + "/reset-password?token=" + rawToken;
    String body =
        """
        Hello,

        Someone asked to reset the password for the %s administrator account on this address.

        Choose a new password:
        %s

        The link works once and expires in half an hour. If that was not you, ignore this
        message - nothing has changed yet.
        """
            .formatted(hospitalName, link);
    emailSender.send(new EmailMessage(to, SUBJECT, body));
  }
}
