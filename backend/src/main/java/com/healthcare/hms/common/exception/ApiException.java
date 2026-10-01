package com.healthcare.hms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base domain exception: carries a standard error code and HTTP status. Thrown by services and
 * rendered by {@link ApiExceptionHandler} into the standard error envelope. Stack traces are never
 * sent to clients.
 */
public class ApiException extends RuntimeException {

  private final String code;
  private final HttpStatus status;

  protected ApiException(String code, HttpStatus status, String message) {
    super(message);
    this.code = code;
    this.status = status;
  }

  protected ApiException(String code, HttpStatus status, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.status = status;
  }

  public String getCode() {
    return code;
  }

  public HttpStatus getStatus() {
    return status;
  }
}
