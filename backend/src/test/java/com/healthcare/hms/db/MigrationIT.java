package com.healthcare.hms.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.healthcare.hms.HealthcareHmsApplication;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * P3.7 — the ROADMAP exit criterion for Phase 3: migrations run from a clean database in one
 * command ({@code ./mvnw verify -Dtest=MigrationIT}).
 *
 * <p>Each test starts from its own empty schema, so the whole path — empty database → Flyway →
 * {@code flyway info} → JPA {@code ddl-auto=validate} → idempotent re-run — is proven without
 * depending on method order. The application is started with {@link
 * TestDatabaseProperties#OVERRIDE_DATASOURCE_PROPERTY} so the D1 initializer leaves the datasource
 * alone and the context really boots against the schema this test migrated.
 */
class MigrationIT {

  /** V1 + V2 + V3 + V4 — bump when a migration is added and keep the version assertions in sync. */
  private static final int MIGRATION_COUNT = 4;

  private static final String EXPECTED_CURRENT_VERSION = "4";

  private MigrationTestSupport db;

  @BeforeEach
  void createEmptyDatabase() {
    db = MigrationTestSupport.withEmptyDatabase();
  }

  @AfterEach
  void dropDatabase() {
    if (db != null) {
      db.close();
      db = null;
    }
  }

  @Test
  void migrationsApplyInOrderOnAnEmptyDatabase() {
    assertThat(userTables())
        .as("the schema must start empty (ROADMAP: migrations run on an empty DB)")
        .isEmpty();

    MigrateResult result = db.flyway().migrate();

    assertThat(result.migrationsExecuted).isEqualTo(MIGRATION_COUNT);
    assertThat(appliedVersions())
        .as("migrations apply strictly in file order")
        .containsExactly("1", "2", "3", "4");
    assertThat(db.currentVersion()).isEqualTo(EXPECTED_CURRENT_VERSION);
    assertThat(db.flyway().info().pending()).isEmpty();
  }

  @Test
  void jpaValidatePassesAgainstTheMigratedSchema() {
    db.migrate();

    try (ConfigurableApplicationContext context = startApplication()) {
      Environment environment = context.getEnvironment();

      assertThat(environment.getProperty("spring.datasource.url"))
          .as("the application must boot against the schema this test migrated (D1 guard)")
          .isEqualTo(db.jdbcUrl());
      assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
          .as("ddl-auto=validate is mandatory in every environment (DATABASE section 3.6)")
          .isEqualTo("validate");

      // Reaching this point means Hibernate built the persistence unit against the migrated
      // schema with ddl-auto=validate: a missing table, column or type would have aborted
      // context startup. The property check below pins the mode that was actually applied.
      EntityManagerFactory entityManagerFactory = context.getBean(EntityManagerFactory.class);
      assertThat(entityManagerFactory.getProperties().get("hibernate.hbm2ddl.auto"))
          .as("the running persistence unit must be in validate mode")
          .isEqualTo("validate");
    }
  }

  @Test
  void reRunningFlywayIsANoOpOnAnAlreadyMigratedDatabase() {
    db.migrate();

    MigrateResult secondRun = db.flyway().migrate();

    assertThat(secondRun.migrationsExecuted)
        .as("DATABASE section 3.2: idempotent on a clean database")
        .isZero();
    assertThat(db.flyway().info().pending()).isEmpty();
    assertThat(db.currentVersion()).isEqualTo(EXPECTED_CURRENT_VERSION);
  }

  // -------------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------------

  private ConfigurableApplicationContext startApplication() {
    return new SpringApplication(HealthcareHmsApplication.class)
        .run(
            "--" + TestDatabaseProperties.OVERRIDE_DATASOURCE_PROPERTY + "=true",
            "--server.port=0",
            "--spring.datasource.url=" + db.jdbcUrl(),
            "--spring.datasource.username=" + db.username(),
            "--spring.datasource.password=" + db.password(),
            "--hms.security.jwt-secret=migration-it-jwt-secret-" + UUID.randomUUID());
  }

  private List<String> userTables() {
    return db.jdbc()
        .queryForList(
            "SELECT table_name FROM information_schema.tables"
                + " WHERE table_schema = ? AND table_type = 'BASE TABLE'",
            String.class,
            db.schema());
  }

  private List<String> appliedVersions() {
    return db.jdbc()
        .queryForList(
            "SELECT version FROM flyway_schema_history"
                + " WHERE version IS NOT NULL AND success = 1 ORDER BY installed_rank",
            String.class);
  }
}
