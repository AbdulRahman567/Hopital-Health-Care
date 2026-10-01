package com.healthcare.hms.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.healthcare.hms.HealthcareHmsApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

/** P2.7 — the app fails to start on missing/placeholder/too-short JWT secrets. */
class SecretValidationTest {

  @Test
  void contextFailsToLoadWhenSecretIsMissing() {
    Throwable failure = runApp("--" + JwtSecretValidator.PROPERTY + "=");

    assertFailsBecauseOfSecretValidation(failure, "Application refuses to start without it.");
  }

  @Test
  void contextFailsToLoadWhenSecretIsPlaceholder() {
    Throwable failure =
        runApp("--" + JwtSecretValidator.PROPERTY + "=changeme-changeme-changeme-0123456789abcdef");

    assertFailsBecauseOfSecretValidation(failure, "placeholder value");
  }

  @Test
  void contextFailsToLoadWhenSecretIsTooShort() {
    Throwable failure = runApp("--" + JwtSecretValidator.PROPERTY + "=too-short");

    assertFailsBecauseOfSecretValidation(failure, "at least 32 characters");
  }

  @Test
  void validSecretPassesValidation() {
    JwtSecretValidator.validate("x".repeat(48));
  }

  private Throwable runApp(String... args) {
    SpringApplication application = new SpringApplication(HealthcareHmsApplication.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    Throwable failure =
        catchThrowable(
            () -> {
              try (ConfigurableApplicationContext ignored = application.run(args)) {
                // Context started — tests above assert failures only.
              }
            });
    assertThat(failure).as("context startup must fail for invalid secrets").isNotNull();
    return failure;
  }

  private void assertFailsBecauseOfSecretValidation(Throwable failure, String expectedFragment) {
    StringBuilder messages = new StringBuilder();
    Throwable current = failure;
    while (current != null) {
      messages.append(current.getMessage()).append('\n');
      current = current.getCause();
    }
    assertThat(messages.toString())
        .contains(JwtSecretValidator.PROPERTY)
        .contains(expectedFragment);
  }
}
