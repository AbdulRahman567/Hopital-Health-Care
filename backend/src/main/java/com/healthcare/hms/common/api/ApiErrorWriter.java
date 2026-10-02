package com.healthcare.hms.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;

/**
 * Renders an API.md section 3 error envelope straight onto the response.
 *
 * <p>Security and tenant filters run outside the {@code DispatcherServlet}, so
 * {@code @RestControllerAdvice} never sees their failures; without this writer they would answer
 * with an empty body plus a {@code WWW-Authenticate} header instead of the documented envelope.
 */
public final class ApiErrorWriter {

  private ApiErrorWriter() {}

  /**
   * Writes {@code {"success":false,"error":{code,message},"traceId":...}} with the given status.
   *
   * @param response response to write to
   * @param objectMapper application mapper (same envelope settings as the controllers)
   * @param status HTTP status
   * @param code stable error code from {@code ErrorCodes}
   * @param message human-readable message, safe to show to the caller
   * @throws IOException when the response stream cannot be written
   */
  public static void write(
      HttpServletResponse response,
      ObjectMapper objectMapper,
      int status,
      String code,
      String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(), ApiErrorResponse.of(ErrorDetail.of(code, message)));
  }
}
