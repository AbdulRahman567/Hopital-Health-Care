package com.healthcare.hms.notification.email;

import com.healthcare.hms.notification.NotificationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Real transport (decision D4, CONF-6): sends through the configured SMTP server.
 *
 * <p>The bean only exists when {@code spring.mail.host} is set, so a developer without mail
 * credentials never trips over it. Production must set it: {@link LoggingEmailSender} is excluded
 * from the {@code prod} profile, so a prod deployment with no SMTP server falls back to {@link
 * UnconfiguredEmailSender}, which fails on use naming the property instead of silently dropping the
 * verification link.
 */
@Component
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
public class SmtpEmailSender implements EmailSender {

  private final JavaMailSender mailSender;
  private final NotificationProperties properties;

  public SmtpEmailSender(JavaMailSender mailSender, NotificationProperties properties) {
    this.mailSender = mailSender;
    this.properties = properties;
  }

  @Override
  public void send(EmailMessage message) {
    SimpleMailMessage outbound = new SimpleMailMessage();
    outbound.setFrom(properties.fromAddress());
    outbound.setTo(message.to());
    outbound.setSubject(message.subject());
    outbound.setText(message.body());
    mailSender.send(outbound);
  }
}
