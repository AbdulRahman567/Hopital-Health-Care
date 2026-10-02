package com.healthcare.hms.db;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Applies the JVM-scoped Testcontainers datasource to contexts created outside the TestContext
 * framework — notably {@code new SpringApplication(...).run(...)} style startup used by
 * SecretValidationTest. Registered in {@code src/test/resources/META-INF/spring.factories}.
 */
public class TestDatabaseContextInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    TestDatabaseProperties.apply(context.getEnvironment());
  }
}
