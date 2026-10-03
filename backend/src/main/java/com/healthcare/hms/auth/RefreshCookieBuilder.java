package com.healthcare.hms.auth;

import java.time.Duration;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The one cookie this application issues (decision D6 / ADR-003): the opaque refresh token, in a
 * cookie a script cannot read.
 *
 * <p>Each attribute is a separate reason:
 *
 * <ul>
 *   <li>{@code HttpOnly} — the value is the only long-lived credential the browser holds, so it
 *       must not be reachable from JavaScript; XSS then steals at most the in-memory access token,
 *       which expires in 12 minutes;
 *   <li>{@code Path=/api/v1/auth} — the cookie leaves the browser only on the endpoints that
 *       consume it, so a careless handler elsewhere can never see it;
 *   <li>{@code SameSite=Lax} — cross-site POSTs do not carry it at all, which is the first half of
 *       decision D5's CSRF answer (the {@code X-Requested-With} guard is the second);
 *   <li>{@code Secure} — profile driven: {@code false} in dev so browsers keep it over {@code
 *       http://localhost}, {@code true} in prod (DEPLOYMENT section 6);
 *   <li>{@code Max-Age} — the refresh TTL, so the browser stops sending a value the server has
 *       already expired.
 * </ul>
 *
 * <p>The cleared variant exists so logout can make the browser drop the cookie in the same response
 * that revokes the family server-side — clearing only one of the two would leave a session that
 * looks logged out and is not.
 */
@Component
public class RefreshCookieBuilder {

  /** Cookie name fixed by decision D6. */
  public static final String COOKIE_NAME = "hms_refresh";

  /** Scope fixed by decision D6: only the endpoints that read it ever receive it. */
  public static final String COOKIE_PATH = "/api/v1/auth";

  private static final String SAME_SITE = "Lax";

  private final boolean secure;
  private final Duration maxAge;

  public RefreshCookieBuilder(AuthProperties properties, Environment environment) {
    this.maxAge = properties.getRefreshTokenTtl();
    this.secure = environment.matchesProfiles("prod");
  }

  /**
   * @param rawToken the value only this response should ever see
   * @return a cookie carrying the secret, with every flag of decision D6 set
   */
  public ResponseCookie issue(String rawToken) {
    return base(rawToken).maxAge(maxAge).build();
  }

  /**
   * @return the same cookie with no value and no lifetime — what logout sends so the browser drops
   *     the one it is holding
   */
  public ResponseCookie cleared() {
    return base("").maxAge(Duration.ZERO).build();
  }

  private ResponseCookie.ResponseCookieBuilder base(String value) {
    return ResponseCookie.from(COOKIE_NAME, value)
        .path(COOKIE_PATH)
        .httpOnly(true)
        .secure(secure)
        .sameSite(SAME_SITE);
  }
}
