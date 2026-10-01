package com.healthcare.hms.common.exception;

import org.springframework.http.HttpStatus;

/** 404 NOT_FOUND — also used for foreign-tenant resources (API.md section 2). */
public class NotFoundException extends ApiException {

  public NotFoundException(String message) {
    super(ErrorCodes.NOT_FOUND, HttpStatus.NOT_FOUND, message);
  }
}
