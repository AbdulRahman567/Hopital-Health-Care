package com.healthcare.hms.auth;

import com.healthcare.hms.common.exception.ApiException;
import com.healthcare.hms.common.exception.ErrorCodes;
import org.springframework.http.HttpStatus;

/**
 * The single failure a caller can get from {@code POST /api/v1/auth/login} — plan P5.3, decisions
 * D9 and D12.
 *
 * <p>Unknown slug, unknown email, wrong password, a tenant that is {@code PENDING} or {@code
 * SUSPENDED}, an account that is not {@code ACTIVE}, and an account with MFA enabled all raise
 * <b>this</b> exception, so every branch renders the same 401 with the same message. That is what
 * makes account enumeration impossible at login: the response cannot distinguish a typo'd password
 * from an address nobody has ever registered.
 *
 * <p>Deliberately a 401 {@code UNAUTHENTICATED} rather than a 403: the caller presented no usable
 * credential, so they are not authenticated. The message is a constant — no branch may append a
 * reason to it.
 */
public final class InvalidCredentialsException extends ApiException {

  /** The one message every login failure carries. */
  public static final String MESSAGE = "Invalid hospital slug, email or password.";

  public InvalidCredentialsException() {
    super(ErrorCodes.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, MESSAGE);
  }
}
