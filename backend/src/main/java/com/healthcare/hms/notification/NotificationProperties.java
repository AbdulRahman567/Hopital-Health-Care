package com.healthcare.hms.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbound notification settings (ENGINEERING_RULES section 4: externalised through
 * {@code @ConfigurationProperties}, never hard-coded).
 *
 * @param fromAddress envelope sender used by the SMTP transport
 */
@ConfigurationProperties(prefix = "hms.notification")
public record NotificationProperties(String fromAddress) {

  /** Applied when {@code hms.notification.from-address} is not set. */
  public static final String DEFAULT_FROM_ADDRESS = "no-reply@healthcare-hms.local";

  public NotificationProperties {
    if (fromAddress == null || fromAddress.isBlank()) {
      fromAddress = DEFAULT_FROM_ADDRESS;
    }
  }
}
