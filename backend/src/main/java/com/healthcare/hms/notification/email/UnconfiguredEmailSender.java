package com.healthcare.hms.notification.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The transport bound where the application must run with no email server at all: the {@code prod}
 * profile without {@code spring.mail.host}.
 *
 * <p>It exists so the context can still start — an absent mail server is not a reason to refuse to
 * boot (Swagger visibility, health endpoints and the rest of the API keep working) — and it fails
 * on first use with a message that names the property to set. Refusing to send is deliberate: a
 * verification link that silently goes nowhere would leave registration stuck with no explanation.
 *
 * <p>The mirror condition of {@link LoggingEmailSender} keeps exactly one {@link EmailSender} bound
 * in every combination of profile and SMTP configuration.
 */
@Component
@Profile("prod")
@ConditionalOnProperty(
    prefix = "spring.mail",
    name = "host",
    havingValue = "false",
    matchIfMissing = true)
public class UnconfiguredEmailSender implements EmailSender {

  @Override
  public void send(EmailMessage message) {
    throw new IllegalStateException(
        "No email transport is configured: spring.mail.host is unset and the prod profile"
            + " disables the logging transport. Set spring.mail.host to send a verification"
            + " email to "
            + message.to()
            + ".");
  }
}
