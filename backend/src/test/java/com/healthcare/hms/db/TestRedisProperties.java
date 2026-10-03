package com.healthcare.hms.db;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Installs the JVM-scoped test Redis into any Spring {@code Environment} (decision D7). */
public final class TestRedisProperties {

  /** Only ever fills a gap; an explicit value always wins (see {@link #apply}). */
  public static final String PORT_PROPERTY = "spring.data.redis.port";

  private TestRedisProperties() {}

  /**
   * Points {@code spring.data.redis} at the shared container, idempotently.
   *
   * <p>Two guards make this safe for a nested context that deliberately aims the client at an
   * unreachable address (the fail-closed case in {@code RateLimitLockoutTest}): it is skipped when
   * a port is already configured, and it is installed as the <i>lowest</i>-precedence source so an
   * inlined {@code @SpringBootTest(properties=...)} wins however Spring happens to order the two.
   * Nothing in {@code application.yml} defines {@code spring.data.redis}, so lowest precedence is
   * still high enough to be the only value in play everywhere else.
   */
  public static void apply(ConfigurableEnvironment environment) {
    if (environment.getProperty(PORT_PROPERTY) != null) {
      return;
    }
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("spring.data.redis.host", TestRedis.host());
    properties.put(PORT_PROPERTY, TestRedis.port());
    environment
        .getPropertySources()
        .addLast(new MapPropertySource(TestRedis.PROPERTY_SOURCE_NAME, properties));
  }
}
