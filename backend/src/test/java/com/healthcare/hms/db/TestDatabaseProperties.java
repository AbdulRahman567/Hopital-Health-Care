package com.healthcare.hms.db;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Installs the JVM-scoped test datasource into any Spring {@code Environment} (D1). */
public final class TestDatabaseProperties {

  private TestDatabaseProperties() {}

  /** Puts the Testcontainer datasource at the highest precedence, idempotently. */
  public static void apply(ConfigurableEnvironment environment) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("spring.datasource.url", TestDatabase.jdbcUrl());
    properties.put("spring.datasource.username", TestDatabase.username());
    properties.put("spring.datasource.password", TestDatabase.password());
    environment
        .getPropertySources()
        .addFirst(new MapPropertySource(TestDatabase.PROPERTY_SOURCE_NAME, properties));
  }
}
