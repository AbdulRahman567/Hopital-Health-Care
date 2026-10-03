package com.healthcare.hms.common.ratelimit;

import com.healthcare.hms.common.exception.ApiException;
import com.healthcare.hms.common.exception.ErrorCodes;
import org.springframework.http.HttpStatus;

/**
 * 429 {@code RATE_LIMITED} &mdash; thrown both when a bucket is empty and, per decision <b>D7</b>,
 * when the limiter itself cannot be reached.
 *
 * <p><b>Fail closed</b> (SECURITY section 12): "Auth endpoints fail closed if the limiter is
 * unavailable." An unreachable Redis must not turn into a free pass to brute-force a password, so
 * the two cases answer identically &mdash; same status, same code, same body. Telling a caller "the
 * limiter is down" would also be a probe for the deployment's health, which is why the message does
 * not distinguish them either. The operational difference lives in the server log.
 *
 * <p>Carries {@code Retry-After} in seconds, rendered by {@code ApiExceptionHandler} for the
 * in-controller path and by {@code RateLimitFilter} for the pre-authentication path, because a
 * filter runs outside the {@code DispatcherServlet} where {@code @RestControllerAdvice} never sees
 * it.
 */
public class RateLimitedException extends ApiException {

  private final long retryAfterSeconds;

  /**
   * @param retryAfterSeconds how long the caller should wait, at least 1
   */
  public RateLimitedException(long retryAfterSeconds) {
    super(
        ErrorCodes.RATE_LIMITED,
        HttpStatus.TOO_MANY_REQUESTS,
        "Too many requests. Please try again later.");
    this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
