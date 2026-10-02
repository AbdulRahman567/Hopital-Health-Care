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
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource(TestDatabase.PROPERTY_SOURCE_NAME, properties));
  }
}
