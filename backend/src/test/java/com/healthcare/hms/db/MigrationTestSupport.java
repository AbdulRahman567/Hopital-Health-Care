package com.healthcare.hms.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Shared support for the Phase 3 migration suites (plan section 5).
 *
 * <p>Every suite gets a **pristine, empty database** so Flyway always starts from zero — the
 * ROADMAP Phase 3 exit criterion ("migrations run on an empty DB"). To keep {@code ./mvnw test}
 * fast, the MySQL server itself is started once per JVM and each suite allocates its own empty
 * schema, which for Flyway is identical to a fresh database (no tables, no schema history). The
 * schema is dropped again in {@link #close()}.
 *
 * <p>This server is deliberately separate from {@link TestDatabase} so the schema shared by all
 * {@code @SpringBootTest} classes is never migrated, dropped or otherwise mutated by these suites.
 */
public final class MigrationTestSupport implements AutoCloseable {

  private static final AtomicInteger SCHEMA_SEQUENCE = new AtomicInteger();
  private static MySQLContainer<?> server;

  private final String schema;
  private final String jdbcUrl;
  private final JdbcTemplate jdbc;
  private final Flyway flyway;

  private MigrationTestSupport(String schema) {
    MySQLContainer<?> running = startServer();
    this.schema = schema;
    this.jdbcUrl =
        "jdbc:mysql://"
            + running.getHost()
            + ":"
            + running.getFirstMappedPort()
            + "/"
            + schema
            + "?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&useSSL=false";
    this.jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(jdbcUrl, running.getUsername(), running.getPassword()));
    this.flyway =
        Flyway.configure()
            .dataSource(jdbcUrl, running.getUsername(), running.getPassword())
            .locations("classpath:db/migration")
            .load();
  }

  /** Starts (once per JVM) the migration server and allocates a brand new empty schema. */
  public static MigrationTestSupport withEmptyDatabase() {
    MySQLContainer<?> running = startServer();
    String schema = "hms_it_" + SCHEMA_SEQUENCE.incrementAndGet();
    executeAdmin("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
    // The container image only privileges its own bootstrap database, so the schema handed to a
    // suite has to be granted explicitly — the suites then run with the ordinary app user.
    executeAdmin(
        "GRANT ALL PRIVILEGES ON `" + schema + "`.* TO '" + running.getUsername() + "'@'%'");
    return new MigrationTestSupport(schema);
  }

  private static synchronized MySQLContainer<?> startServer() {
    if (server == null) {
      server =
          new MySQLContainer<>(TestDatabase.IMAGE)
              .withDatabaseName("hms_bootstrap")
              .withUsername("hms")
              .withPassword("hms-migration-password");
      server.start();
      MySQLContainer<?> started = server;
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    if (started.isRunning()) {
                      started.stop();
                    }
                  },
                  "migration-database-shutdown"));
    } else if (!server.isRunning()) {
      server.start();
    }
    return server;
  }

  /**
   * Runs a statement without a default schema. MySQLContainer sets {@code MYSQL_ROOT_PASSWORD} to
   * the same password it hands to the app user, so root is used here — the app user only holds
   * privileges on the single database the container was created with.
   */
  private static void executeAdmin(String sql) {
    String url =
        "jdbc:mysql://"
            + server.getHost()
            + ":"
            + server.getFirstMappedPort()
            + "/?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&useSSL=false";
    try (Connection connection = DriverManager.getConnection(url, "root", server.getPassword());
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException e) {
      throw new IllegalStateException("Admin statement failed: " + sql, e);
    }
  }

  /** Applies every pending migration on the empty schema. */
  public void migrate() {
    flyway.migrate();
  }

  /** Current {@code flyway info} version, {@code null} when no migration has been applied. */
  public String currentVersion() {
    MigrationInfo current = flyway.info().current();
    return current == null || current.getVersion() == null
        ? null
        : current.getVersion().getVersion();
  }

  public Flyway flyway() {
    return flyway;
  }

  public JdbcTemplate jdbc() {
    return jdbc;
  }

  public String jdbcUrl() {
    return jdbcUrl;
  }

  public String schema() {
    return schema;
  }

  @Override
  public void close() {
    executeAdmin("DROP DATABASE IF EXISTS `" + schema + "`");
  }
}
