package com.healthcare.hms.db;

import org.testcontainers.containers.MySQLContainer;

/**
 * Single, JVM-scoped Testcontainers MySQL used by every test context (decision D1, option B of the
 * Phase 3 plan).
 *
 * <p>Adding a {@code DataSource} in P3.1 would otherwise force all pre-existing
 * {@code @SpringBootTest} classes to reach a real database. Instead of editing them, the container
 * started here is injected through {@link TestDatabaseContextInitializer} (raw {@code
 * SpringApplication} startup, as used by SecretValidationTest) and {@link
 * TestDatabaseContextCustomizerFactory} (every {@code @SpringBootTest}), both registered in {@code
 * src/test/resources/META-INF/spring.factories}.
 *
 * <p>The image matches {@code infra/docker-compose.yml} ({@code mysql:8.4}) so local development
 * and tests exercise the same server version. Migration suites that need a pristine schema build
 * their own container through {@link MigrationTestSupport}.
 */
public final class TestDatabase {

  /** Same major version as the compose service (infra/docker-compose.yml). */
  public static final String IMAGE = "mysql:8.4";

  static final String DATABASE_NAME = "hms_test";
  static final String USERNAME = "hms";
  static final String PASSWORD = "hms-test-password";

  /** Name of the property source the wiring installs; wins over application.yml defaults. */
  public static final String PROPERTY_SOURCE_NAME = "hmsTestDatabase";

  private static MySQLContainer<?> container;

  private TestDatabase() {}

  /** Starts the container once per JVM and returns it. */
  public static synchronized MySQLContainer<?> start() {
    if (container == null) {
      container =
          new MySQLContainer<>(IMAGE)
              .withDatabaseName(DATABASE_NAME)
              .withUsername(USERNAME)
              .withPassword(PASSWORD);
      container.start();
      registerShutdownHook();
    } else if (!container.isRunning()) {
      container.start();
    }
    return container;
  }

  public static String jdbcUrl() {
    return start().getJdbcUrl();
  }

  public static String username() {
    return USERNAME;
  }

  public static String password() {
    return PASSWORD;
  }

  public static String databaseName() {
    return DATABASE_NAME;
  }

  private static void registerShutdownHook() {
    MySQLContainer<?> running = container;
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (running.isRunning()) {
                    running.stop();
                  }
                },
                "test-database-shutdown"));
  }
}
