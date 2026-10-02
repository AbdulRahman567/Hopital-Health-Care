package com.healthcare.hms.db;

import java.util.List;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;

/**
 * TestContext SPI entry point for the shared test datasource. Registered under {@code
 * org.springframework.test.context.ContextCustomizerFactory} in {@code
 * src/test/resources/META-INF/spring.factories}, so it applies to every test class in the module.
 */
public class TestDatabaseContextCustomizerFactory implements ContextCustomizerFactory {

  @Override
  public ContextCustomizer createContextCustomizer(
      Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
    return TestDatabaseContextCustomizer.INSTANCE;
  }
}
