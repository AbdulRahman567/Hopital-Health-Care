package com.healthcare.hms.common.exception;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.healthcare.hms.common.api.ApiErrorResponse;
import com.healthcare.hms.common.api.ErrorDetail;
import com.healthcare.hms.common.api.FieldViolation;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * The single {@code @RestControllerAdvice} for the whole API (ENGINEERING_RULES section 4).
 *
 * <p>Maps exceptions to the standard error envelope from API.md section 3 with exact HTTP statuses:
 * 422 for validation failures naming every offending field, 400 for malformed input, 404 for
 * missing/foreign resources, 409 for conflicts and a generic 500 without stack traces.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  private static final String GENERIC_SERVER_MESSAGE = "An unexpected error occurred.";
  private static final String VALIDATION_MESSAGE = "One or more fields are invalid.";

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex) {
    List<FieldViolation> fields =
        ex.getBindingResult().getFieldErrors().stream().map(this::toViolation).toList();
    return error(422, ErrorCodes.VALIDATION_FAILED, VALIDATION_MESSAGE, fields);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
    UnrecognizedPropertyException unknown = findUnrecognizedProperty(ex);
    if (unknown != null) {
      String field =
          unknown.getPath().stream()
              .map(reference -> reference.getFieldName())
              .filter(Objects::nonNull)
              .collect(Collectors.joining("."));
      return error(
          422,
          ErrorCodes.VALIDATION_FAILED,
          VALIDATION_MESSAGE,
          List.of(new FieldViolation(field, "Unknown property.")));
    }
    return error(400, ErrorCodes.VALIDATION_FAILED, "Malformed request.", null);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiErrorResponse> handleMissingParameter(
      MissingServletRequestParameterException ex) {
    return error(
        422,
        ErrorCodes.VALIDATION_FAILED,
        VALIDATION_MESSAGE,
        List.of(new FieldViolation(ex.getParameterName(), "is required.")));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex) {
    String typeName = ex.getRequiredType() == null ? "value" : ex.getRequiredType().getSimpleName();
    return error(
        400,
        ErrorCodes.VALIDATION_FAILED,
        "Malformed request.",
        List.of(new FieldViolation(ex.getName(), "must be a valid " + typeName + ".")));
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
    return ResponseEntity.status(ex.getStatus())
        .body(ApiErrorResponse.of(ErrorDetail.of(ex.getCode(), ex.getMessage())));
  }

  @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
  public ResponseEntity<ApiErrorResponse> handleNoResource(Exception ex) {
    return error(404, ErrorCodes.NOT_FOUND, "Resource not found.", null);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return error(500, ErrorCodes.INTERNAL_ERROR, GENERIC_SERVER_MESSAGE, null);
  }

  private ResponseEntity<ApiErrorResponse> error(
      int status, String code, String message, List<FieldViolation> fields) {
    ApiErrorResponse body =
        ApiErrorResponse.of(
            fields == null ? ErrorDetail.of(code, message) : ErrorDetail.of(code, message, fields));
    return ResponseEntity.status(status).body(body);
  }

  private FieldViolation toViolation(FieldError fieldError) {
    String message =
        fieldError.getDefaultMessage() == null ? "is invalid." : fieldError.getDefaultMessage();
    return new FieldViolation(fieldError.getField(), message);
  }

  private UnrecognizedPropertyException findUnrecognizedProperty(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof UnrecognizedPropertyException unrecognized) {
        return unrecognized;
      }
      current = current.getCause();
    }
    return null;
  }
}
