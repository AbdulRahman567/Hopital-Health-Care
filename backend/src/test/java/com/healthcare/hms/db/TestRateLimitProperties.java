package com.healthcare.hms.db;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Raises every SECURITY section 12 limit to a value no test can reach (decision D7).
 *
 * <p>The suite is one JVM talking to one Redis, so every context shares the same counters; more
 * importantly every request arrives from the same peer address, so the per-IP buckets would be
 * shared by all ~190 tests regardless of which context made them. At the production limits (10
 * registrations an hour, 20 logins a minute) the suite would start returning 429 for reasons that
 * have nothing to do with the test being run.
 *
 * <p>Raising them keeps the limiter <i>on</i> &mdash; every auth request still takes the real Redis
 * round trip, so a wiring mistake still fails loudly &mdash; while removing only the budget as a
 * source of cross-test interference. Enforcement itself is proved by {@code RateLimitLockoutTest},
 * which lowers individual limits in a nested context where it can own the counters, and by {@code
 * RateLimitPropertiesTest}, which asserts the defaults still equal the documented table.
 *
 * <p>Registered next to the Redis wiring in {@code spring.factories}; never applied outside tests.
 */
public final class TestRateLimitProperties {

  /** High enough that no test can spend it; a real hour cannot produce this many requests. */
  static final int UNREACHABLE = 1_000_000;

  private TestRateLimitProperties() {}

  /** Installs the raised limits unless the context already declared its own. */
  public static void apply(ConfigurableEnvironment environment) {
    Map<String, Object> properties = new LinkedHashMap<>();
    putIfAbsent(environment, properties, "hms.ratelimit.login-ip-limit");
    putIfAbsent(environment, properties, "hms.ratelimit.login-account-limit");
    putIfAbsent(environment, properties, "hms.ratelimit.email-ip-limit");
    putIfAbsent(environment, properties, "hms.ratelimit.email-account-limit");
    putIfAbsent(environment, properties, "hms.ratelimit.reset-account-limit");
    if (!properties.isEmpty()) {
      // Lowest precedence on purpose: a nested context that narrows a limit inlines it, and
      // inlined properties must win whichever way Spring orders the two sources.
      environment
          .getPropertySources()
          .addLast(new MapPropertySource("hmsTestRateLimits", properties));
    }
  }

  private static void putIfAbsent(
      ConfigurableEnvironment environment, Map<String, Object> properties, String key) {
    // A nested context that deliberately narrows a limit (RateLimitLockoutTest) must keep it, so
    // the default is only ever a fallback rather than an override.
    if (environment.getProperty(key) == null) {
      properties.put(key, UNREACHABLE);
    }
  }
}
