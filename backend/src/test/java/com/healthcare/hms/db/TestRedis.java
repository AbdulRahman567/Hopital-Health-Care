package com.healthcare.hms.db;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Single, JVM-scoped Testcontainers Redis used by every test context (decision D7 / plan risk 4).
 *
 * <p>Two things depend on this container existing:
 *
 * <ul>
 *   <li><b>the health probe.</b> Once {@code spring-boot-starter-data-redis} is on the classpath,
 *       Spring Boot contributes a {@code RedisHealthIndicator}, and {@code ActuatorSecurityTest}
 *       asserts {@code /actuator/health} is {@code UP}. Without a reachable Redis every context in
 *       the module would turn that assertion red — which is exactly plan risk 4;
 *   <li><b>the limiter's fail-closed rule.</b> SECURITY section 12 rejects an auth request when the
 *       limiter is unavailable. A suite with no Redis would therefore reject every login, which
 *       would make the 190-odd existing tests fail for a reason that has nothing to do with them.
 * </ul>
 *
 * <p>Started lazily on first use and stopped by a shutdown hook, mirroring {@link TestDatabase}.
 * The image matches {@code infra/docker-compose.yml} ({@code redis:7.4-alpine}) so local
 * development and tests exercise the same server.
 */
public final class TestRedis {

  /** Same image as the compose service (infra/docker-compose.yml). */
  public static final String IMAGE = "redis:7.4-alpine";

  static final int CONTAINER_PORT = 6379;

  /** Name of the property source the wiring installs; wins over application.yml defaults. */
  static final String PROPERTY_SOURCE_NAME = "hmsTestRedis";

  private static GenericContainer<?> container;

  private TestRedis() {}

  /** Starts the container once per JVM and returns it. */
  public static synchronized GenericContainer<?> start() {
    if (container == null) {
      container =
          new GenericContainer<>(DockerImageName.parse(IMAGE)).withExposedPorts(CONTAINER_PORT);
      container.start();
      registerShutdownHook();
    } else if (!container.isRunning()) {
      container.start();
    }
    return container;
  }

  public static String host() {
    return start().getHost();
  }

  public static int port() {
    return start().getMappedPort(CONTAINER_PORT);
  }

  private static void registerShutdownHook() {
    GenericContainer<?> running = container;
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (running.isRunning()) {
                    running.stop();
                  }
                },
                "test-redis-shutdown"));
  }
}
