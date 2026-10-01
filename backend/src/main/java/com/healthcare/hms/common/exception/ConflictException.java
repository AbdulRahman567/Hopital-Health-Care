package com.healthcare.hms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 409 conflict with a standard code: {@link ErrorCodes#DUPLICATE_RESOURCE}, {@link
 * ErrorCodes#SLOT_UNAVAILABLE}, {@link ErrorCodes#VERSION_CONFLICT} or {@link
 * ErrorCodes#RECORD_FINALIZED}.
 */
public class ConflictException extends ApiException {

  public ConflictException(String code, String message) {
    super(code, HttpStatus.CONFLICT, message);
  }
}
