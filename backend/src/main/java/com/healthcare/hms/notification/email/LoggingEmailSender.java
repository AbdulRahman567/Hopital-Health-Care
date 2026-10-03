package com.healthcare.hms.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Dev/default transport (decision D4): writes the email — link included — to the application log so
 * a local flow can be completed without an SMTP server.
 *
 * <p><b>Never active in production, and never active next to a real transport.</b> Two conditions
 * enforce that:
 *
 * <ul>
 *   <li>{@code prod} is excluded outright — a verification or reset token printed into a production
 *       log is a token leak (plan risk 10). Production binds {@link SmtpEmailSender} by setting
 *       {@code spring.mail.host}.
 *   <li>It backs off whenever {@code spring.mail.host} <i>is</i> configured, so a developer who
 *       points the app at a real SMTP server gets exactly one transport instead of an ambiguous
 *       pair.
 * </ul>
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(
    prefix = "spring.mail",
    name = "host",
    havingValue = "false",
    matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

  @Override
  public void send(EmailMessage message) {
    log.info(
        "[DEV EMAIL] to={} subject={} body={}", message.to(), message.subject(), message.body());
  }
}
