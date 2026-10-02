package com.healthcare.hms.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * P3.1 — Flyway integration with {@code ddl-auto=validate}.
 *
 * <p>The context must boot against an empty Testcontainers MySQL, report no pending migrations
 * (there are no migration files yet in P3.1, and none left pending later), honour {@code
 * ddl-auto=validate} in every profile, and expose a reachable database through {@code
 * /actuator/health}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FlywayStartupTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private DataSource dataSource;
  @Autowired private Flyway flyway;
  @Autowired private Environment environment;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void contextConnectsToTheJunitScopedTestContainer() throws Exception {
    assertThat(environment.getProperty("spring.datasource.url")).isEqualTo(TestDatabase.jdbcUrl());

    try (Connection connection = dataSource.getConnection()) {
      assertThat(connection.getCatalog()).isEqualTo(TestDatabase.databaseName());
      assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
    }
  }

  @Test
  void flywayHasNoPendingMigrations() {
    assertThat(flyway.info().pending()).isEmpty();
  }

  @Test
  void ddlAutoValidateIsEnabledForTheActiveProfile() {
    assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
  }

  @Test
  void everyProfileDeclaresDdlAutoValidate() throws IOException {
    YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    for (String file : List.of("application-dev.yml", "application-prod.yml")) {
      List<org.springframework.core.env.PropertySource<?>> loaded =
          loader.load(file, new ClassPathResource(file));
      assertThat(loaded).as("%s must pin ddl-auto=validate (ROADMAP P3.1)", file).isNotEmpty();
      for (org.springframework.core.env.PropertySource<?> propertySource : loaded) {
        assertThat(propertySource.getProperty("spring.jpa.hibernate.ddl-auto"))
            .as("%s", file)
            .isEqualTo("validate");
      }
    }
  }

  @Test
  void healthEndpointReportsUpWithAReachableDatabase() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));

    Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    assertThat(one).isEqualTo(1);
  }
}
