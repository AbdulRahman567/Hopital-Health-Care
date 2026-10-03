package com.healthcare.hms.auth;

import com.healthcare.hms.common.exception.ApiException;
import com.healthcare.hms.common.exception.ErrorCodes;
import org.springframework.http.HttpStatus;

/**
 * The single failure a client sees when an emailed link cannot be honoured — unknown, expired or
 * already used, whether it was a verification link (P5.2) or a reset link (P5.7).
 *
 * <p>One exception with one fixed message is what makes the uniform response real: the three
 * rejection paths are indistinguishable in code, so they cannot drift apart in the response, and
 * none of them can be used to probe which tokens exist (SECURITY section 3).
 *
 * <p>Status is 400 with the standard {@link ErrorCodes#VALIDATION_FAILED} code — the request is
 * well-formed, its payload just cannot be honoured, and no field was supplied by a third party.
 */
public class InvalidTokenException extends ApiException {

  /** Fixed wording; every rejection path must use it unchanged. */
  public static final String MESSAGE = "This link is invalid or has expired.";

  public InvalidTokenException() {
    super(ErrorCodes.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, MESSAGE);
  }
}
