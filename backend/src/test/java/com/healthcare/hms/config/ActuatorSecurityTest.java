package com.healthcare.hms.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** P2.5 — actuator restricted: health public (200), every other endpoint 401/404. */
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorSecurityTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void healthEndpointIsPublicAndReturnsUp() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void otherActuatorEndpointsAreNotPubliclyExposed() throws Exception {
    mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/actuator/beans")).andExpect(status().isUnauthorized());
  }

  @Test
  void apiEndpointsAreDeniedByDefaultWith401Envelope() throws Exception {
    mockMvc
        .perform(get("/api/v1/patients"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.error.message").value("Authentication required."));
  }

  @Test
  void healthResponseBodyDoesNotLeakComponentDetails() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components").doesNotExist());
  }
}
