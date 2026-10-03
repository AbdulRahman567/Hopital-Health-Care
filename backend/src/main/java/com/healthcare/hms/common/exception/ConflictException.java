package com.healthcare.hms.common.exception;

import com.healthcare.hms.common.api.FieldViolation;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * 409 conflict with a standard code: {@link ErrorCodes#DUPLICATE_RESOURCE}, {@link
 * ErrorCodes#SLOT_UNAVAILABLE}, {@link ErrorCodes#VERSION_CONFLICT} or {@link
 * ErrorCodes#RECORD_FINALIZED}.
 *
 * <p>The field-aware overload lets the service name the input that caused the conflict (D9:
 * registering a hospital whose slug already exists reports {@code field: "hospitalName"}), so the
 * client can highlight it instead of guessing.
 */
public class ConflictException extends ApiException {

  public ConflictException(String code, String message) {
    super(code, HttpStatus.CONFLICT, message);
  }

  public ConflictException(String code, String message, List<FieldViolation> fields) {
    super(code, HttpStatus.CONFLICT, message, fields);
  }
}
