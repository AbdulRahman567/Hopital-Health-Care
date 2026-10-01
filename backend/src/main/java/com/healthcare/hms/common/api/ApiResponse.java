package com.healthcare.hms.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.healthcare.hms.common.logging.TraceIds;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Standard success envelope per API.md section 3: {@code {success, data, meta?, timestamp,
 * traceId}}.
 *
 * @param <T> payload type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success, T data, PageMeta meta, Instant timestamp, String traceId) {

  private static final Clock CLOCK = Clock.systemUTC();

  /** Success envelope for a non-paginated payload. */
  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null, Instant.now(CLOCK), TraceIds.current());
  }

  /** Success envelope for a paginated payload with explicit pagination metadata. */
  public static <T> ApiResponse<List<T>> paginated(List<T> data, PageMeta meta) {
    return new ApiResponse<>(true, data, meta, Instant.now(CLOCK), TraceIds.current());
  }

  /** Success envelope for a paginated payload, deriving metadata from a Spring Data page. */
  public static <T> ApiResponse<List<T>> paginated(Page<T> page) {
    return paginated(page.getContent(), PaginationMapper.from(page));
  }
}
