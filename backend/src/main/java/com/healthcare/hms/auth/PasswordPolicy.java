package com.healthcare.hms.auth;

import com.healthcare.hms.common.api.FieldViolation;
import com.healthcare.hms.common.exception.ErrorCodes;
import com.healthcare.hms.common.exception.FieldValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Password rules for registration and reset (decision D3 / TQ-2).
 *
 * <p>Deliberately a service-side rule rather than a Jakarta constraint: it is the single place the
 * minimum length and the embedded common-password list live, and it reports through the same 422
 * {@link ErrorCodes#VALIDATION_FAILED} envelope bean validation produces — a client cannot tell
 * which of the two rejected the input, and neither response reveals whether the account exists.
 *
 * <p>No HaveIBeenPwned check (D3): an outbound network call per attempt is a P23 Security Hardening
 * follow-up, not something to block this phase on.
 */
@Component
public class PasswordPolicy {

  /** Minimum length (decision D3). */
  public static final int MIN_LENGTH = 12;

  /** Maximum length — also the bcrypt input limit worth enforcing before hashing. */
  public static final int MAX_LENGTH = 128;

  private static final String MESSAGE = "One or more fields are invalid.";

  /**
   * Small embedded list of passwords that survive a length check (decision D3). Long entries are
   * intentional: anything shorter than {@link #MIN_LENGTH} is already rejected above, so listing
   * them would only duplicate work.
   */
  private static final Set<String> COMMON_PASSWORDS =
      Set.of(
          "passwordpassword",
          "password123456",
          "password1234",
          "123456789012",
          "1234567890123",
          "qwertyuiop123",
          "qwertyuiopasdf",
          "letmeinplease",
          "welcome123456",
          "administrator",
          "iloveyou12345",
          "changemenow12");

  /**
   * Validates a raw password.
   *
   * @param rawPassword the password as the client sent it
   * @throws FieldValidationException 422 with {@code field: "password"} when a rule is broken
   */
  public void validate(String rawPassword) {
    List<FieldViolation> violations = new ArrayList<>();
    if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
      violations.add(
          new FieldViolation(
              "password", "Password must be at least " + MIN_LENGTH + " characters."));
    } else if (rawPassword.length() > MAX_LENGTH) {
      violations.add(
          new FieldViolation(
              "password", "Password must be at most " + MAX_LENGTH + " characters."));
    } else if (COMMON_PASSWORDS.contains(rawPassword.toLowerCase(Locale.ROOT))) {
      violations.add(
          new FieldViolation("password", "Password is too common. Choose a different password."));
    }
    if (!violations.isEmpty()) {
      throw new FieldValidationException(MESSAGE, violations);
    }
  }
}
