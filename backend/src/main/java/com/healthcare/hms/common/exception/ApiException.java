package com.healthcare.hms.common.exception;

import com.healthcare.hms.common.api.FieldViolation;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Base domain exception: carries a standard error code and HTTP status. Thrown by services and
 * rendered by {@link ApiExceptionHandler} into the standard error envelope. Stack traces are never
 * sent to clients.
 *
 * <p>Optionally carries field-level violations (API.md section 3), so a domain conflict can name
 * the offending field the same way bean validation does — P5.2's duplicate-slug 409 reports {@code
 * field: "hospitalName"} through this path.
 */
public class ApiException extends RuntimeException {

  private final String code;
  private final HttpStatus status;
  private final List<FieldViolation> fields;

  protected ApiException(String code, HttpStatus status, String message) {
    this(code, status, message, null, null);
  }

  protected ApiException(String code, HttpStatus status, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.status = status;
    this.fields = null;
  }

  protected ApiException(
      String code, HttpStatus status, String message, List<FieldViolation> fields) {
    this(code, status, message, fields, null);
  }

  private ApiException(
      String code,
      HttpStatus status,
      String message,
      List<FieldViolation> fields,
      Throwable cause) {
    super(message, cause);
    this.code = code;
    this.status = status;
    this.fields = fields == null || fields.isEmpty() ? null : List.copyOf(fields);
  }

  public String getCode() {
    return code;
  }

  public HttpStatus getStatus() {
    return status;
  }

  /** Field-level violations for this error, or {@code null} when the error has no field detail. */
  public List<FieldViolation> getFields() {
    return fields;
  }
}
