package com.healthcare.hms.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes the request correlation id: reuses a safe incoming {@code X-Request-Id} or generates
 * one, exposes it in the MDC (surfaces as {@code traceId} in JSON logs and response envelopes) and
 * echoes it back on the response.
 *
 * <p>Logs one structured completion line per request containing method, path, status and duration
 * only — never the query string, headers or bodies, so no PHI or secrets can leak into logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

  /** Request/response correlation header. */
  public static final String HEADER = "X-Request-Id";

  private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9\\-]{1,64}");
  private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String incoming = request.getHeader(HEADER);
    String traceId =
        incoming != null && SAFE_TRACE_ID.matcher(incoming).matches()
            ? incoming
            : UUID.randomUUID().toString();
    MDC.put(TraceIds.TRACE_ID_KEY, traceId);
    response.setHeader(HEADER, traceId);
    long startNanos = System.nanoTime();
    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
      log.debug(
          "request_completed method={} path={} status={} durationMs={}",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          durationMs);
      MDC.remove(TraceIds.TRACE_ID_KEY);
    }
  }
}
