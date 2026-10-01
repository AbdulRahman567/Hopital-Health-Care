package com.healthcare.hms.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard error envelope per API.md section 3: {@code {success:false, error, traceId}}.
 *
 * @param error error detail (code, message, optional field violations)
 * @param traceId request correlation id
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(boolean success, ErrorDetail error, String traceId) {

  /** Builds an error envelope carrying the current request {@code traceId}. */
  public static ApiErrorResponse of(ErrorDetail error) {
    return new ApiErrorResponse(false, error, com.healthcare.hms.common.logging.TraceIds.current());
  }
}
