package com.healthcare.hms.common.ratelimit;

import com.healthcare.hms.common.logging.Pii;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Fixed-window counters in Redis &mdash; the enforcement half of decision <b>D7</b> (SECURITY
 * section 12).
 *
 * <p><b>Two commands, in this order.</b> {@code SET key 0 NX EX window} first, then {@code INCR}.
 * The conditional set guarantees a TTL exists before the counter can be incremented, so a crash
 * between the two leaves a key that expires on its own instead of a bucket that never resets and
 * locks an address out forever; when the key already exists the set is a no-op and the increment
 * leaves the original expiry alone, which is what makes the window fixed rather than sliding.
 *
 * <p><b>Fail closed.</b> Every Redis failure is turned into {@link RateLimitedException}: SECURITY
 * section 12 requires an auth request to be rejected when the limiter is unavailable rather than
 * quietly admitted, and the response is deliberately indistinguishable from a genuine limit so a
 * caller cannot use it to probe whether the limiter is up.
 *
 * <p><b>One window per key.</b> A bucket is only ever consumed with the limit and window its owner
 * declared, so a scope cannot inherit a neighbour's budget by sharing a key.
 */
@Service
public class RateLimiterService {

  private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

  private final StringRedisTemplate redis;

  public RateLimiterService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  /**
   * Charges one attempt against {@code key}, or throws when the budget is spent.
   *
   * <p>Callers invoke this <i>before</i> the work the limit protects &mdash; before the account
   * lookup, before the token is hashed &mdash; so a non-existent identity is charged exactly like a
   * real one and the response cannot be used to enumerate accounts (decision D7).
   *
   * @param key bucket from {@link RateLimitKeys} or {@code TenantKeys.redis}
   * @param limit attempts allowed in one window; must be positive
   * @param window length of the fixed window
   * @throws RateLimitedException 429 when the budget is spent <b>or</b> Redis is unreachable
   */
  public void consume(String key, int limit, Duration window) {
    if (limit <= 0) {
      // A misconfigured budget must never read as "unlimited": reject instead of admitting.
      throw failClosed(key, window, "limit must be positive, got " + limit);
    }
    long count;
    try {
      // SETNX first: it guarantees a TTL exists before anything can increment the counter, so a
      // failure between the two leaves a key that expires by itself rather than a bucket that
      // never resets. When the key already exists it is a no-op and the original expiry stands,
      // which is what makes the window fixed rather than sliding.
      redis.opsForValue().setIfAbsent(key, "0", window);
      Long incremented = redis.opsForValue().increment(key);
      if (incremented == null) {
        throw failClosed(key, window, "increment returned no value");
      }
      count = incremented;
    } catch (RateLimitedException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw failClosed(key, window, ex.toString());
    }

    if (count > limit) {
      throw new RateLimitedException(retryAfterSeconds(key, window));
    }
  }

  /** Seconds left in the current window, never less than 1 and never more than the window. */
  private long retryAfterSeconds(String key, Duration window) {
    try {
      Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
      if (ttl != null && ttl > 0) {
        return ttl;
      }
    } catch (RuntimeException ex) {
      log.warn(
          "Could not read the rate-limit TTL for {}; using the window length",
          Pii.maskKey(key),
          ex);
    }
    return Math.max(1, window.getSeconds());
  }

  /**
   * The fail-closed answer (decision D7).
   *
   * <p>Logged with the original reason but no stack: the call stack here is always the filter
   * chain, and the useful diagnostic is the Redis exception that was caught, which {@code reason}
   * already carries. Operators can tell "someone is being limited" from "Redis is down" &mdash; a
   * distinction the caller is deliberately not given.
   */
  private RateLimitedException failClosed(String key, Duration window, String reason) {
    log.error("Rate limiter unreachable, failing closed (key={}): {}", Pii.maskKey(key), reason);
    return new RateLimitedException(Math.max(1, window.getSeconds()));
  }
}
