package com.healthcare.hms.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Error payload inside the error envelope: {@code {code, message, fields?}}. Field-level violations
 * are present for validation failures (422) and omitted otherwise.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorDetail(String code, String message, List<FieldViolation> fields) {

  /** Error detail without field violations. */
  public static ErrorDetail of(String code, String message) {
    return new ErrorDetail(code, message, null);
  }

  /** Error detail with field-level violations. */
  public static ErrorDetail of(String code, String message, List<FieldViolation> fields) {
    return new ErrorDetail(code, message, fields);
  }
}
