package com.healthcare.hms.config;

import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail-fast startup validation for the JWT signing secret (ENGINEERING_RULES section 8): the
 * application refuses to start when {@code hms.security.jwt-secret} (env {@code HMS_JWT_SECRET}) is
 * missing, empty, shorter than 32 characters, or a placeholder value. The secret itself is never
 * echoed into the failure message or logs.
 */
@Component
public class JwtSecretValidator implements InitializingBean {

  /** Property name; bound from env var {@code HMS_JWT_SECRET} via application.yml. */
  public static final String PROPERTY = "hms.security.jwt-secret";

  private static final int MIN_LENGTH = 32;
  private static final List<String> PLACEHOLDER_TOKENS =
      List.of(
          "changeme",
          "change-me",
          "change_me",
          "example",
          "placeholder",
          "replace-me",
          "your-secret");

  private final String secret;

  public JwtSecretValidator(Environment environment) {
    this.secret = environment.getProperty(PROPERTY);
  }

  @Override
  public void afterPropertiesSet() {
    validate(secret);
  }

  /**
   * Validates a candidate secret.
   *
   * @throws IllegalStateException when missing, too short or a placeholder
   */
  static void validate(String candidate) {
    if (candidate == null || candidate.isBlank()) {
      throw new IllegalStateException(
          "Missing required property '"
              + PROPERTY
              + "' (env HMS_JWT_SECRET). Application refuses to start without it.");
    }
    String trimmed = candidate.trim();
    if (trimmed.length() < MIN_LENGTH) {
      throw new IllegalStateException(
          "Property '" + PROPERTY + "' must be at least " + MIN_LENGTH + " characters long.");
    }
    String lowered = trimmed.toLowerCase(Locale.ROOT);
    for (String token : PLACEHOLDER_TOKENS) {
      if (lowered.contains(token)) {
        throw new IllegalStateException(
            "Property '"
                + PROPERTY
                + "' is a placeholder value; set a strong random secret instead.");
      }
    }
  }
}
