package com.healthcare.hms.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** P2.6 — OpenAPI/Swagger visible in dev (200), disabled in prod (404). */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiVisibilityTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void devProfileExposesSwaggerUiAndApiDocs() throws Exception {
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());

    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").exists())
        .andExpect(jsonPath("$.paths").exists());
  }

  @Nested
  @SpringBootTest(properties = "spring.profiles.active=prod")
  @AutoConfigureMockMvc
  class ProdProfile {

    @Autowired private MockMvc mockMvc;

    @Test
    void prodProfileServes404ForSwaggerUiAndApiDocs() throws Exception {
      mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());

      mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }
  }
}
