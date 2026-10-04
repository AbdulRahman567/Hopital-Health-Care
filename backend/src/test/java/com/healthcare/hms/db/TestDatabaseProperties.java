package com.healthcare.hms.db;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Installs the JVM-scoped test datasource into any Spring {@code Environment} (D1). */
public final class TestDatabaseProperties {

  /**
   * Set to {@code true} by a caller that supplies its own datasource — P3.7 {@code MigrationIT}
   * boots the application against the schema it just migrated. The initializer is installed at
   * highest precedence (it must win over {@code application.yml} for every other test), so without
   * this guard it would silently win there too.
   */
  public static final String OVERRIDE_DATASOURCE_PROPERTY = "hms.test.datasource.override";

  private TestDatabaseProperties() {}

  /** Puts the Testcontainer datasource at the highest precedence, idempotently. */
  public static void apply(ConfigurableEnvironment environment) {
    if (environment.getProperty(OVERRIDE_DATASOURCE_PROPERTY, Boolean.class, false)) {
      return;
    }
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("spring.datasource.url", TestDatabase.jdbcUrl());
    properties.put("spring.datasource.username", TestDatabase.username());
    properties.put("spring.datasource.password", TestDatabase.password());
    // One JVM-scoped MySQL for every context in the suite, and MySQL allows 151 connections.
    // Hikari would otherwise give each context a pool of ten and keep those connections open for
    // as long as the context lives, so roughly twenty contexts are already at the ceiling and the
    // next test class to ask for a pool gets "Too many connections" - which surfaces as a random
    // failure in whatever class happens to start next, not in the class that filled the pool.
    // Tests are single-threaded and never hold more than a connection or two, so four is room to
    // spare; the cost of the cap is nil and the suite can keep growing.
    properties.put("spring.datasource.hikari.maximum-pool-size", 4);
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource(TestDatabase.PROPERTY_SOURCE_NAME, properties));
  }
}
