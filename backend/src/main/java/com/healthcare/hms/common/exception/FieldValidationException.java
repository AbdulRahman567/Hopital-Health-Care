package com.healthcare.hms.common.exception;

import com.healthcare.hms.common.api.FieldViolation;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * 422 {@link ErrorCodes#VALIDATION_FAILED} raised by a rule that lives in the service rather than
 * in a Jakarta constraint on the DTO — P5.2's {@code PasswordPolicy} is the first of these.
 *
 * <p>Both paths therefore produce the identical envelope (API.md section 3): a client cannot tell
 * whether a bad password was rejected by bean validation or by the policy, and neither response
 * leaks that the password belongs to an existing account.
 */
public class FieldValidationException extends ApiException {

  public FieldValidationException(String message, List<FieldViolation> fields) {
    super(ErrorCodes.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, message, fields);
  }
}
