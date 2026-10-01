package com.healthcare.hms.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.hms.common.logging.TraceIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** P2.3 — centralized {@code @RestControllerAdvice}: 422 with {@code error.fields[]}. */
class ApiExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    // Mirrors application.yml: fail-on-unknown-properties = true.
    com.fasterxml.jackson.databind.ObjectMapper mapper =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .enable(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestValidationController())
            .setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(
                new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                    mapper))
            .build();
  }

  @AfterEach
  void clearTraceId() {
    MDC.clear();
  }

  @Test
  void invalidBodyReturns422WithExactFieldNames() throws Exception {
    mockMvc
        .perform(
            post("/test/things")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ok\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.message").value("One or more fields are invalid."))
        .andExpect(jsonPath("$.error.fields[0].field").value("phone"))
        .andExpect(jsonPath("$.error.fields[0].message").value("Phone number is required."));
  }

  @Test
  void multipleInvalidFieldsAllListed() throws Exception {
    mockMvc
        .perform(post("/test/things").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.error.fields[?(@.field == 'phone')].message")
                .value(org.hamcrest.Matchers.hasItem("Phone number is required.")))
        .andExpect(
            jsonPath("$.error.fields[?(@.field == 'name')].message")
                .value(org.hamcrest.Matchers.hasItem("Name must not be blank.")));
  }

  @Test
  void unknownJsonPropertyReturns422NamingTheProperty() throws Exception {
    mockMvc
        .perform(
            post("/test/things")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"0501234567\",\"bogus\":1}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.fields[0].field").value("bogus"))
        .andExpect(jsonPath("$.error.fields[0].message").value("Unknown property."));
  }

  @Test
  void malformedJsonReturns400() throws Exception {
    mockMvc
        .perform(post("/test/things").contentType(MediaType.APPLICATION_JSON).content("not-json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.message").value("Malformed request."));
  }

  @Test
  void typeMismatchReturns400NamingTheParameter() throws Exception {
    mockMvc
        .perform(get("/test/things/lookup").param("id", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.fields[0].field").value("id"))
        .andExpect(jsonPath("$.error.fields[0].message").value("must be a valid UUID."));
  }

  @Test
  void missingRequiredParameterReturns422() throws Exception {
    mockMvc
        .perform(get("/test/things"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.fields[0].field").value("q"))
        .andExpect(jsonPath("$.error.fields[0].message").value("is required."));
  }

  @Test
  void notFoundExceptionReturns404Envelope() throws Exception {
    mockMvc
        .perform(get("/test/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").value("Patient not found."));
  }

  @Test
  void conflictExceptionReturns409WithStandardCode() throws Exception {
    mockMvc
        .perform(get("/test/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
  }

  @Test
  void unexpectedExceptionReturns500WithoutInternals() throws Exception {
    mockMvc
        .perform(get("/test/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.error.message").value("An unexpected error occurred."))
        .andExpect(
            jsonPath("$.error.message")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
  }

  @Test
  void traceIdFromMdcSurfacesInErrorEnvelope() throws Exception {
    MDC.put(TraceIds.TRACE_ID_KEY, "trace-abc");

    mockMvc
        .perform(get("/test/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.traceId").value("trace-abc"));
  }

  @RestController
  @RequestMapping("/test")
  static class TestValidationController {

    @PostMapping("/things")
    public void create(@Valid @RequestBody TestRequest request) {
      // P2.3 only exercises validation and error mapping.
    }

    @GetMapping("/things/lookup")
    public String lookup(@RequestParam UUID id) {
      return id.toString();
    }

    @GetMapping("/things")
    public String search(@RequestParam String q) {
      return q;
    }

    @GetMapping("/not-found")
    public String notFound() {
      throw new NotFoundException("Patient not found.");
    }

    @GetMapping("/conflict")
    public String conflict() {
      throw new ConflictException(ErrorCodes.DUPLICATE_RESOURCE, "Patient already exists.");
    }

    @GetMapping("/boom")
    public String boom() {
      throw new IllegalStateException("secret db detail");
    }
  }

  record TestRequest(
      @NotBlank(message = "Phone number is required.") String phone,
      @NotBlank(message = "Name must not be blank.") @Size(max = 50) String name) {}
}
