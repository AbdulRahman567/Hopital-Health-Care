package com.healthcare.hms.config;

import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail-fast startup validation for the database password in the {@code prod} profile (D7 of the
 * Phase 3 plan): production must supply {@code spring.datasource.password} (env {@code
 * HMS_DB_PASSWORD}) as a real secret. Missing, empty or placeholder values abort startup before any
 * connection is opened. The password itself is never echoed into the failure message or logs.
 *
 * <p>Dev/test keep an empty default on purpose so local wiring stays simple; the value is read from
 * {@code infra/.env} in day-to-day development.
 */
@Profile("prod")
@Component
public class DataSourceSecretValidator implements InitializingBean {

  /** Property name; bound from env var {@code HMS_DB_PASSWORD} via application.yml. */
  public static final String PROPERTY = "spring.datasource.password";

  private static final List<String> PLACEHOLDER_TOKENS =
      List.of(
          "changeme",
          "change-me",
          "change_me",
          "example",
          "placeholder",
          "replace-me",
          "your-password");

  private final String password;

  public DataSourceSecretValidator(Environment environment) {
    this.password = environment.getProperty(PROPERTY);
  }

  @Override
  public void afterPropertiesSet() {
    validate(password);
  }

  /**
   * Validates a candidate database password.
   *
   * @throws IllegalStateException when missing, blank or a placeholder
   */
  static void validate(String candidate) {
    if (candidate == null || candidate.isBlank()) {
      throw new IllegalStateException(
          "Missing required property '"
              + PROPERTY
              + "' (env HMS_DB_PASSWORD). Application refuses to start in the prod profile without it.");
    }
    String lowered = candidate.trim().toLowerCase(Locale.ROOT);
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
