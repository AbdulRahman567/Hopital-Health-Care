package com.healthcare.hms.db;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Applies the JVM-scoped Testcontainers datasource to every {@code @SpringBootTest} context without
 * touching any pre-existing test class (decision D1, option B).
 */
final class TestDatabaseContextCustomizer implements ContextCustomizer {

  static final TestDatabaseContextCustomizer INSTANCE = new TestDatabaseContextCustomizer();

  private TestDatabaseContextCustomizer() {}

  @Override
  public void customizeContext(
      ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
    TestDatabaseProperties.apply(context.getEnvironment());
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof TestDatabaseContextCustomizer;
  }

  @Override
  public int hashCode() {
    return TestDatabaseContextCustomizer.class.hashCode();
  }
}
