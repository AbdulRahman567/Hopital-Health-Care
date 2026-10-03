package com.healthcare.hms.db;

import java.util.List;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;

/**
 * TestContext SPI entry point for the shared test Redis. Registered under {@code
 * org.springframework.test.context.ContextCustomizerFactory} in {@code
 * src/test/resources/META-INF/spring.factories}, so it applies to every test class in the module.
 *
 * <p>Kept separate from {@code TestDatabaseContextCustomizerFactory} so the datasource and the
 * Redis wiring can be reasoned about independently, and so neither one has to know about the other.
 */
public class TestRedisContextCustomizerFactory implements ContextCustomizerFactory {

  @Override
  public ContextCustomizer createContextCustomizer(
      Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
    return TestRedisContextCustomizer.INSTANCE;
  }
}
