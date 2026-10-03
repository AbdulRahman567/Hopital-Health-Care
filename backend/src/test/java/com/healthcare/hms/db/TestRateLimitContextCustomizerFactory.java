package com.healthcare.hms.db;

import java.util.List;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Applies {@link TestRateLimitProperties} to every {@code @SpringBootTest} context without touching
 * any pre-existing test class &mdash; the third {@code ContextCustomizerFactory} entry in the
 * {@code spring.factories} D1/D7 wiring.
 *
 * <p>Only the TestContext path is registered: the raw {@code SpringApplication} runs covered by
 * {@code TestDatabaseContextInitializer} start the app for a startup assertion and never issue an
 * auth request, so they have no budget to raise.
 */
public class TestRateLimitContextCustomizerFactory implements ContextCustomizerFactory {

  @Override
  public ContextCustomizer createContextCustomizer(
      Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
    return TestRateLimitContextCustomizer.INSTANCE;
  }

  private static final class TestRateLimitContextCustomizer implements ContextCustomizer {

    private static final TestRateLimitContextCustomizer INSTANCE =
        new TestRateLimitContextCustomizer();

    private TestRateLimitContextCustomizer() {}

    @Override
    public void customizeContext(
        ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
      TestRateLimitProperties.apply(context.getEnvironment());
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof TestRateLimitContextCustomizer;
    }

    @Override
    public int hashCode() {
      return TestRateLimitContextCustomizer.class.hashCode();
    }
  }
}
