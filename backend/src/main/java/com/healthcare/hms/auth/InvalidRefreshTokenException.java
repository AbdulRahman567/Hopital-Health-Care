package com.healthcare.hms.auth;

import com.healthcare.hms.common.exception.ApiException;
import com.healthcare.hms.common.exception.ErrorCodes;
import org.springframework.http.HttpStatus;

/**
 * The single failure a refresh presentation can end in — plan P5.4, decision D9.
 *
 * <p>Unknown digest, expired token, a token that was already rotated, a family poisoned by an
 * earlier reuse, a family past decision D8's 30-day cap and an account that is no longer active all
 * raise <b>this</b>, so P5.5's {@code POST /api/v1/auth/refresh} renders one identical 401 for
 * every one of them. Nothing in the body says which of the six happened, because "your token was
 * replayed" is information only the attacker needs.
 *
 * <p>401 rather than 400: API.md section 3 defines 401 as "not authenticated / expired token",
 * which is exactly what a dead refresh token is.
 */
public final class InvalidRefreshTokenException extends ApiException {

  /** The one message every refresh failure carries. */
  public static final String MESSAGE = "Refresh token is invalid or has expired.";

  public InvalidRefreshTokenException() {
    super(ErrorCodes.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, MESSAGE);
  }
}
