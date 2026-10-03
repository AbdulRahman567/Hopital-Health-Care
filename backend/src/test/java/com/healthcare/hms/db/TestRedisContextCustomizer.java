package com.healthcare.hms.db;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Applies the JVM-scoped Testcontainers Redis to every {@code @SpringBootTest} context without
 * touching any pre-existing test class (decision D7, the same shape as {@link
 * TestDatabaseContextCustomizer}).
 */
final class TestRedisContextCustomizer implements ContextCustomizer {

  static final TestRedisContextCustomizer INSTANCE = new TestRedisContextCustomizer();

  private TestRedisContextCustomizer() {}

  @Override
  public void customizeContext(
      ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
    TestRedisProperties.apply(context.getEnvironment());
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof TestRedisContextCustomizer;
  }

  @Override
  public int hashCode() {
    return TestRedisContextCustomizer.class.hashCode();
  }
}
