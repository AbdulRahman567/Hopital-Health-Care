package com.healthcare.hms.auth;

import com.healthcare.hms.notification.email.EmailMessage;
import com.healthcare.hms.notification.email.EmailSender;
import org.springframework.stereotype.Component;

/**
 * Builds and sends the verification email (decision D4).
 *
 * <p>Both senders — a fresh registration and a resend — go through here, so the link format is
 * defined once: a change to it can never leave one flow pointing at a different URL than the other.
 *
 * <p>The link targets the web application, never the API: the browser opens it with a GET, and the
 * SPA re-presents the token in a POST body ({@code AuthController#verifyEmail}). The raw token
 * appears only in this outbound message — it is never logged by application code (plan risk 10).
 */
@Component
public class VerificationMailer {

  static final String SUBJECT = "Verify your email address";

  private final EmailSender emailSender;
  private final AuthProperties properties;

  public VerificationMailer(EmailSender emailSender, AuthProperties properties) {
    this.emailSender = emailSender;
    this.properties = properties;
  }

  /**
   * Sends the verification link.
   *
   * @param to recipient address
   * @param hospitalName hospital the account belongs to, for the greeting
   * @param rawToken the token itself (only its hash is stored)
   */
  public void send(String to, String hospitalName, String rawToken) {
    String link = properties.getFrontendBaseUrl() + "/verify-email?token=" + rawToken;
    String body =
        """
        Hello,

        Someone registered %s with this email address.

        Confirm the address to activate the hospital:
        %s

        If that was not you, ignore this message - the account stays inactive until the link is
        opened.
        """
            .formatted(hospitalName, link);
    emailSender.send(new EmailMessage(to, SUBJECT, body));
  }
}
