package com.healthcare.hms.db;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Applies the JVM-scoped Testcontainers Redis to contexts created outside the TestContext framework
 * &mdash; notably {@code new SpringApplication(...).run(...)} style startup used by {@code
 * SecretValidationTest}. Registered in {@code src/test/resources/META-INF/spring.factories}.
 *
 * <p>Companion to {@link TestDatabaseContextInitializer}; see {@link TestRedis} for why the module
 * needs a shared Redis at all.
 */
public class TestRedisContextInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    TestRedisProperties.apply(context.getEnvironment());
  }
}
