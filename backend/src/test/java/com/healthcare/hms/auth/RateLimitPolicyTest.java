package com.healthcare.hms.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.healthcare.hms.common.ratelimit.RateLimitKeys;
import com.healthcare.hms.common.ratelimit.RateLimitProperties;
import com.healthcare.hms.tenant.TenantKeys;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Decision <b>D7</b>'s arithmetic, asserted as arithmetic.
 *
 * <p>Two things must not be able to drift from their source documents without something failing:
 *
 * <ul>
 *   <li>the limits &mdash; SECURITY section 12 is a table of numbers, and {@link
 *       RateLimitProperties} is where they are actually enforced from;
 *   <li>the key shapes &mdash; TDD section 13 lists them, and every bucket the limiter ever opens
 *       is built by {@link RateLimitKeys} or {@code TenantKeys}.
 * </ul>
 *
 * <p>The progressive lockout is here rather than in the integration suite because it is pure
 * arithmetic: proving "doubles per further failure, capped at one hour" by waiting through three
 * real locks would take an hour and prove nothing that this does not.
 */
class RateLimitPolicyTest {

  @Test
  void theDefaultsAreExactlyTheSecuritySection12Table() {
    RateLimitProperties defaults = new RateLimitProperties();

    assertThat(defaults.getLoginIpLimit()).isEqualTo(20);
    assertThat(defaults.getLoginIpWindow()).isEqualTo(Duration.ofMinutes(1));
    assertThat(defaults.getLoginAccountLimit()).isEqualTo(5);
    assertThat(defaults.getLoginAccountWindow()).isEqualTo(Duration.ofMinutes(1));

    assertThat(defaults.getEmailIpLimit()).isEqualTo(10);
    assertThat(defaults.getEmailIpWindow()).isEqualTo(Duration.ofHours(1));
    assertThat(defaults.getEmailAccountLimit()).isEqualTo(3);
    assertThat(defaults.getEmailAccountWindow()).isEqualTo(Duration.ofHours(1));

    assertThat(defaults.getResetAccountLimit()).isEqualTo(3);
    assertThat(defaults.getResetAccountWindow()).isEqualTo(Duration.ofHours(1));
  }

  @Test
  void theDefaultLockoutIsFiveFailuresFifteenMinutesDoublingToOneHour() {
    RateLimitProperties defaults = new RateLimitProperties();

    assertThat(defaults.getLockoutThreshold()).isEqualTo(5);
    assertThat(defaults.getLockoutBase()).isEqualTo(Duration.ofMinutes(15));
    assertThat(defaults.getLockoutMax()).isEqualTo(Duration.ofHours(1));
  }

  @Test
  void theLockoutStartsAtTheThresholdDoublesAndThenCaps() {
    Duration base = Duration.ofMinutes(15);
    Duration max = Duration.ofHours(1);

    assertThat(LockoutService.lockDurationFor(0, 5, base, max)).isEqualTo(Duration.ZERO);
    assertThat(LockoutService.lockDurationFor(4, 5, base, max)).isEqualTo(Duration.ZERO);
    assertThat(LockoutService.lockDurationFor(5, 5, base, max)).isEqualTo(Duration.ofMinutes(15));
    assertThat(LockoutService.lockDurationFor(6, 5, base, max)).isEqualTo(Duration.ofMinutes(30));
    assertThat(LockoutService.lockDurationFor(7, 5, base, max)).isEqualTo(Duration.ofMinutes(60));
    // and stays there however far an attacker pushes it
    assertThat(LockoutService.lockDurationFor(8, 5, base, max)).isEqualTo(Duration.ofMinutes(60));
    assertThat(LockoutService.lockDurationFor(1_000_000, 5, base, max))
        .isEqualTo(Duration.ofMinutes(60));
  }

  @Test
  void anAbsentThresholdMeansNever() {
    // A misconfigured threshold must disable the lockout rather than lock everything at once.
    assertThat(LockoutService.lockDurationFor(1, 0, Duration.ofMinutes(15), Duration.ofHours(1)))
        .isEqualTo(Duration.ZERO);
  }

  @Test
  void theAnonymousKeysMatchTddSection13() {
    assertThat(RateLimitKeys.ip("203.0.113.7")).isEqualTo("rl:ip:203.0.113.7");
    assertThat(RateLimitKeys.ip("  203.0.113.7 ")).isEqualTo("rl:ip:203.0.113.7");
    assertThat(RateLimitKeys.ip(null)).isEqualTo("rl:ip:unknown");
    assertThat(RateLimitKeys.ip("   ")).isEqualTo("rl:ip:unknown");

    assertThat(RateLimitKeys.email(" Admin@Example.TEST "))
        .isEqualTo("rl:email:admin@example.test");
    assertThatThrownBy(() -> RateLimitKeys.email(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RateLimitKeys.email("  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theAccountKeyIsTenantScopedAndTheTwoFamiliesNeverCollide() {
    UUID tenantId = UUID.randomUUID();
    String email = "admin@example.test";

    assertThat(TenantKeys.redis(tenantId, "rl", "login", email))
        .isEqualTo("t:" + tenantId + ":rl:login:" + email);
    assertThat(TenantKeys.redis(tenantId, "lockout", email))
        .isEqualTo("t:" + tenantId + ":lockout:" + email);

    // An address shaped like an IP cannot borrow the IP budget, and the tenant prefix keeps an
    // account bucket away from both anonymous families.
    assertThat(RateLimitKeys.email("1.2.3.4")).isNotEqualTo(RateLimitKeys.ip("1.2.3.4"));
    assertThat(TenantKeys.redis(tenantId, "rl", "login", email)).doesNotStartWith("rl:");
  }
}
