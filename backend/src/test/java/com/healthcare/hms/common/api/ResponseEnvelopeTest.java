package com.healthcare.hms.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.healthcare.hms.common.logging.TraceIds;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** P2.2 — response envelope and pagination helpers per API.md section 3/4. */
class ResponseEnvelopeTest {

  private final ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @AfterEach
  void clearTraceId() {
    org.slf4j.MDC.clear();
  }

  @Test
  void okEnvelopeCarriesSuccessDataTimestampAndTraceId() {
    org.slf4j.MDC.put(TraceIds.TRACE_ID_KEY, "trace-123");

    ApiResponse<String> response = ApiResponse.ok("payload");

    assertThat(response.success()).isTrue();
    assertThat(response.data()).isEqualTo("payload");
    assertThat(response.meta()).isNull();
    assertThat(response.timestamp()).isNotNull();
    assertThat(response.traceId()).isEqualTo("trace-123");
  }

  @Test
  void okEnvelopeSerializesPerSpecWithoutMeta() throws Exception {
    org.slf4j.MDC.put(TraceIds.TRACE_ID_KEY, "trace-123");

    JsonNode json =
        mapper.readTree(mapper.writeValueAsString(ApiResponse.ok(java.util.Map.of("k", "v"))));

    assertThat(json.get("success").asBoolean()).isTrue();
    assertThat(json.get("data").get("k").asText()).isEqualTo("v");
    assertThat(json.has("meta")).isFalse();
    assertThat(json.get("timestamp").isTextual()).isTrue();
    assertThat(json.get("timestamp").asText()).isNotBlank();
    assertThat(json.get("traceId").asText()).isEqualTo("trace-123");
  }

  @Test
  void paginatedEnvelopeDerivesMetaFromSpringDataPage() {
    org.slf4j.MDC.put(TraceIds.TRACE_ID_KEY, "trace-9");
    PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 20), 134);

    ApiResponse<List<String>> response = ApiResponse.paginated(page);

    assertThat(response.success()).isTrue();
    assertThat(response.data()).containsExactly("a", "b");
    assertThat(response.meta()).isEqualTo(new PageMeta(0, 20, 134, 7));
    assertThat(response.traceId()).isEqualTo("trace-9");
  }

  @Test
  void paginatedMetaSerializesWithSpecFieldNames() throws Exception {
    PageImpl<String> page = new PageImpl<>(List.of("a"), PageRequest.of(1, 10), 35);

    JsonNode json = mapper.readTree(mapper.writeValueAsString(ApiResponse.paginated(page)));

    JsonNode meta = json.get("meta");
    assertThat(meta.get("page").asInt()).isEqualTo(1);
    assertThat(meta.get("size").asInt()).isEqualTo(10);
    assertThat(meta.get("totalElements").asLong()).isEqualTo(35);
    assertThat(meta.get("totalPages").asInt()).isEqualTo(4);
  }

  @Test
  void errorEnvelopeSerializesPerSpec() throws Exception {
    org.slf4j.MDC.put(TraceIds.TRACE_ID_KEY, "trace-err");
    ApiErrorResponse response =
        ApiErrorResponse.of(
            ErrorDetail.of(
                "VALIDATION_FAILED",
                "One or more fields are invalid.",
                List.of(new FieldViolation("phone", "Phone number is required."))));

    JsonNode json = mapper.readTree(mapper.writeValueAsString(response));

    assertThat(json.get("success").asBoolean()).isFalse();
    assertThat(json.get("error").get("code").asText()).isEqualTo("VALIDATION_FAILED");
    assertThat(json.get("error").get("message").asText())
        .isEqualTo("One or more fields are invalid.");
    JsonNode field = json.get("error").get("fields").get(0);
    assertThat(field.get("field").asText()).isEqualTo("phone");
    assertThat(field.get("message").asText()).isEqualTo("Phone number is required.");
    assertThat(json.get("traceId").asText()).isEqualTo("trace-err");
  }

  @Test
  void pageParamsApplyDefaultSizeMaxSizeAndFloorRules() {
    assertThat(PageParams.of(null, null)).isEqualTo(new PageParams(0, 20));
    assertThat(PageParams.of(3, 50)).isEqualTo(new PageParams(3, 50));
    assertThat(PageParams.of(-1, 0)).isEqualTo(new PageParams(0, 20));
    assertThat(PageParams.of(2, 1000)).isEqualTo(new PageParams(2, 100));
  }

  @Test
  void pageParamsConvertToPageRequest() {
    PageRequest request = PageParams.of(1, 25).toPageRequest(Sort.by("lastName"));

    assertThat(request.getPageNumber()).isEqualTo(1);
    assertThat(request.getPageSize()).isEqualTo(25);
    assertThat(request.getSort().getOrderFor("lastName")).isNotNull();
  }

  @Test
  void paginationMapperMapsAllMetaFields() {
    PageMeta meta = PaginationMapper.from(new PageImpl<>(List.of("x"), PageRequest.of(2, 10), 35));

    assertThat(meta).isEqualTo(new PageMeta(2, 10, 35, 4));
  }
}
