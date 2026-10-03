package com.healthcare.hms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.hms.common.api.ApiErrorWriter;
import com.healthcare.hms.common.exception.ErrorCodes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Decision D5's CSRF answer for the two endpoints that are authenticated by a cookie rather than a
 * bearer token: {@code POST /api/v1/auth/refresh} and {@code POST /api/v1/auth/logout}.
 *
 * <p>Spring's own CSRF protection was switched off in P5.2 because it is session-bound and this app
 * is stateless — but that left the refresh cookie travelling on its own. A cookie is sent
 * automatically, including on a cross-site POST, so {@code SameSite=Lax} alone is a browser policy
 * the server does not control. Requiring an extra request header costs nothing for a same-origin
 * client and cannot be satisfied from another origin: a cross-origin request carrying a header that
 * is not CORS-safelisted triggers a preflight, which fails for a route that never opts in. TDD
 * section 7 names this pattern explicitly — "SameSite + custom header check".
 *
 * <p>The header must be present and non-blank; its value is not interpreted, because any value
 * already proves the request was composed by code that could read the response.
 *
 * <p>Registered immediately before the authorization filter, which is TDD section 4.1's position
 * for it: rate limit → authentication → tenant resolution → <b>csrf guard</b> → authorization.
 * Failures answer with the same 403 {@code ACCESS_DENIED} envelope the rest of the chain uses, so a
 * missing header is indistinguishable from any other denial.
 */
public class CustomHeaderCsrfFilter extends OncePerRequestFilter {

  /** The header a client must send, per decision D5. */
  public static final String HEADER = "X-Requested-With";

  /** Exactly the cookie-authenticated routes — no bearer endpoint is asked for this. */
  private static final Set<String> PROTECTED_PATHS =
      Set.of("/api/v1/auth/refresh", "/api/v1/auth/logout");

  private final ObjectMapper objectMapper;

  public CustomHeaderCsrfFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return !PROTECTED_PATHS.contains(path);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader(HEADER);
    if (header == null || header.isBlank()) {
      ApiErrorWriter.write(response, objectMapper, 403, ErrorCodes.ACCESS_DENIED, "Access denied.");
      return;
    }
    filterChain.doFilter(request, response);
  }
}
