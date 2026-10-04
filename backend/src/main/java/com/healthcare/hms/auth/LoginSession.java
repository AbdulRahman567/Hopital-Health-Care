package com.healthcare.hms.auth;

import com.healthcare.hms.auth.api.LoginResponse;
import java.util.UUID;

/**
 * What a successful login produces: the body the caller may read, and the secret that must only
 * ever travel in a cookie.
 *
 * <p>The pairing is deliberate. Returning just a {@link LoginResponse} would leave the raw refresh
 * token in a local variable somewhere, and returning it inside the response body would publish it —
 * this type forces the controller to handle both, and makes it obvious that the second field has no
 * JSON representation.
 *
 * @param response the access token, safe to serialize
 * @param refresh the opaque refresh token, for {@code Set-Cookie} and nowhere else
 */
public record LoginSession(LoginResponse response, IssuedRefreshToken refresh) {

  /** Convenience for callers that only need the id of the account just signed in. */
  public UUID userId() {
    return refresh.userId();
  }
}
