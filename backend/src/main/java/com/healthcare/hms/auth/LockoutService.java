package com.healthcare.hms.auth;

import com.healthcare.hms.auth.repository.UserRepository;
import com.healthcare.hms.common.logging.Pii;
import com.healthcare.hms.common.ratelimit.RateLimitProperties;
import com.healthcare.hms.common.ratelimit.RateLimitedException;
import com.healthcare.hms.tenant.TenantKeys;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Progressive lockout after repeated sign-in failures (SECURITY section 3, decision <b>D7</b>).
 *
 * <p>Five consecutive failures lock the account for 15 minutes, doubling on each further failure
 * and capped at one hour; a successful sign-in clears the count. The progression is expressed by
 * {@link #lockDurationFor(long)}, a pure function, so "doubling, capped at 1 h" is asserted
 * directly instead of by waiting out three real locks.
 *
 * <p><b>Two keys, both self-expiring.</b> The attempt counter is {@code t:<tid>:lockout:<email>},
 * an {@code INCR} bucket with a one-hour TTL that is refreshed on every failure, so a dormant
 * attacker's history evaporates on its own; the lock itself is {@code t:<tid>:lock:<email>}, a
 * {@code SET ... EX} whose TTL <i>is</i> the remaining lock time. Reading the TTL rather than a
 * stored timestamp means the clock that matters is Redis's, and the key disappears the moment the
 * lock ends.
 *
 * <p><b>Existence is not observable.</b> The keys are built from the slug the request supplied and
 * the address in the request body &mdash; never from a user id &mdash; so an address with no row
 * behind it accrues exactly the same counter and locks at exactly the same attempt as a real one
 * (decision D7). The {@code users} mirror below is a <i>write-through</i> of a decision already
 * taken in Redis, performed only when a row happens to exist; it is never what decides, so a
 * missing row cannot make the lock disappear.
 *
 * <p><b>Why mirror at all.</b> {@code failed_attempts} and {@code locked_until} are part of the V1
 * schema and of the audit/support story: an operator looking at a row can see why a user cannot
 * sign in without knowing Redis exists. They are denormalised state, refreshed from the source of
 * truth on every decision.
 */
@Service
public class LockoutService {

  private static final Logger log = LoggerFactory.getLogger(LockoutService.class);

  private final StringRedisTemplate redis;
  private final UserRepository userRepository;
  private final RateLimitProperties properties;

  public LockoutService(
      StringRedisTemplate redis, UserRepository userRepository, RateLimitProperties properties) {
    this.redis = redis;
    this.userRepository = userRepository;
    this.properties = properties;
  }

  /** TTL of the attempt counter: always long enough to outlive any lock it can produce. */
  private Duration attemptTtl() {
    return properties.getLockoutMax().plus(properties.getLockoutBase());
  }

  /**
   * How long the account still has to wait, or {@code null} when it may try.
   *
   * <p>Called before the account lookup, so a locked address is rejected identically whether or not
   * a row exists behind it.
   */
  public Duration remainingLock(UUID tenantId, String email) {
    try {
      Long ttl = redis.getExpire(lockKey(tenantId, email), TimeUnit.SECONDS);
      if (ttl == null || ttl <= 0) {
        return null;
      }
      return Duration.ofSeconds(ttl);
    } catch (RuntimeException ex) {
      // Fail closed, exactly like the rate limiter (decision D7): Redis being unreachable must not
      // hand out a free attempt at a password.
      log.error("Lockout state unavailable, failing closed (tenantId={})", tenantId, ex);
      return properties.getLockoutMax();
    }
  }

  /**
   * Charges one failed sign-in and applies the resulting lock.
   *
   * @param user the row to mirror onto, or {@code null} when the address has no row &mdash; the
   *     decision is taken in Redis either way
   */
  public void recordFailure(UUID tenantId, String email, User user) {
    String attemptsKey = attemptsKey(tenantId, email);
    long attempts;
    try {
      Long incremented = redis.opsForValue().increment(attemptsKey);
      attempts = incremented == null ? 1L : incremented;
      // Refresh on every failure, not just the first, so the TTL always outlives the lock this
      // failure can produce and the counter cannot expire out from under an active attacker.
      redis.expire(attemptsKey, attemptTtl());
    } catch (RuntimeException ex) {
      // The lock cannot be trusted, but the attempt must still be charged somewhere. The database
      // mirror is the only remaining ledger, so fall through and still write it.
      log.error("Could not record a sign-in failure in Redis (tenantId={})", tenantId, ex);
      attempts = user == null ? 1L : Math.max(1, user.getFailedAttempts() + 1L);
    }

    Duration lock =
        lockDurationFor(
            attempts,
            properties.getLockoutThreshold(),
            properties.getLockoutBase(),
            properties.getLockoutMax());
    Instant lockedUntil = lock.isZero() ? null : Instant.now().plus(lock);
    if (!lock.isZero()) {
      try {
        redis.opsForValue().set(lockKey(tenantId, email), "1", lock);
      } catch (RuntimeException ex) {
        log.error("Could not apply a lockout in Redis (tenantId={})", tenantId, ex);
      }
      log.info(
          "Locking account after {} failures for {} (tenantId={}, until={})",
          attempts,
          Pii.maskEmail(email),
          tenantId,
          lockedUntil);
    }
    mirror(user, attempts, lockedUntil);
  }

  /** Clears the count and any lock after a successful sign-in. */
  public void recordSuccess(UUID tenantId, String email, User user) {
    try {
      redis.delete(attemptsKey(tenantId, email));
      redis.delete(lockKey(tenantId, email));
    } catch (RuntimeException ex) {
      log.warn("Could not clear lockout state (tenantId={})", tenantId, ex);
    }
    if (user != null && (user.getFailedAttempts() > 0 || user.getLockedUntil() != null)) {
      mirror(user, 0, null);
    }
  }

  /** Throws the uniform 429 for a locked account. */
  public static void reject(Duration remaining) {
    throw new RateLimitedException(remaining == null ? 60 : remaining.getSeconds());
  }

  /**
   * Length of the lock {@code consecutiveFailures} failures earns: zero below the threshold, {@code
   * base x 2^(failures - threshold)} from there, capped at {@code max}.
   *
   * <p>Static and side-effect free on purpose: it is arithmetic, so it is tested as arithmetic
   * rather than by sitting through three real locks. The shift is bounded before the left shift, so
   * the multiplication cannot overflow however absurd a failure count gets.
   */
  static Duration lockDurationFor(
      long consecutiveFailures, long threshold, Duration base, Duration max) {
    if (threshold <= 0 || consecutiveFailures < threshold) {
      return Duration.ZERO;
    }
    long shift = Math.min(consecutiveFailures - threshold, 6);
    long units = base.toMillis() << shift;
    return Duration.ofMillis(Math.min(units, max.toMillis()));
  }

  private void mirror(User user, long attempts, Instant lockedUntil) {
    if (user == null) {
      return;
    }
    try {
      user.setFailedAttempts((int) Math.min(attempts, Integer.MAX_VALUE));
      user.setLockedUntil(lockedUntil);
      userRepository.save(user);
    } catch (RuntimeException ex) {
      // The Redis decision stands; a failed mirror must not turn a rejection into a 500.
      log.error("Could not mirror lockout state onto users (userId={})", user.getId(), ex);
    }
  }

  private static String attemptsKey(UUID tenantId, String email) {
    return TenantKeys.redis(tenantId, "lockout", email);
  }

  private static String lockKey(UUID tenantId, String email) {
    return TenantKeys.redis(tenantId, "lock", email);
  }
}
