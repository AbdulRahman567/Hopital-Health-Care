package com.healthcare.hms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.hms.common.api.ApiErrorWriter;
import com.healthcare.hms.common.ratelimit.RateLimitKeys;
import com.healthcare.hms.common.ratelimit.RateLimitProperties;
import com.healthcare.hms.common.ratelimit.RateLimitedException;
import com.healthcare.hms.common.ratelimit.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * TDD section 4.1's first stage: charge the <i>per-address</i> budget before a request is allowed
 * to reach authentication (decision <b>D7</b>).
 *
 * <p>Scope &mdash; SECURITY section 12's IP rows, and only those:
 *
 * <ul>
 *   <li>{@code POST /api/v1/auth/login} &rarr; 20/minute;
 *   <li>{@code register-hospital}, {@code resend-verification} and {@code verify-email} &rarr;
 *       10/hour, one shared bucket, because they are three steps of the same emailed flow;
 *   <li>everything else under {@code /api/v1/auth} (refresh, logout) &rarr; nothing: the table does
 *       not give them an IP scope, they are protected by the custom-header guard instead, and
 *       inventing a limit here would silently change the documented policy.
 * </ul>
 *
 * <p>Only {@code /api/v1/auth/**} is examined; every other request passes straight through, so a
 * future public endpoint does not inherit a limit nobody chose.
 *
 * <p><b>Why a filter and not a service call.</b> The IP budget must be charged before the work it
 * protects &mdash; before the password is hashed &mdash; so an attacker cannot use BCrypt's cost to
 * turn rate limiting into a denial of service, and so an unknown address costs exactly what a known
 * one does. The account-scoped budgets live inside the services for the same reason; this filter
 * owns the half that needs no account at all.
 *
 * <p><b>Constructed inline</b> in {@link SecurityConfig}, never exposed as a {@code @Bean} filter:
 * Spring Boot would otherwise register it in the servlet container at {@code /*} and run it twice,
 * exactly as for {@link TenantContextFilter} and {@link CustomHeaderCsrfFilter}.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  /** The only prefix this filter acts on. */
  static final String AUTH_PREFIX = "/api/v1/auth/";

  private static final String LOGIN = AUTH_PREFIX + "login";
  private static final String REGISTER = AUTH_PREFIX + "register-hospital";
  private static final String RESEND = AUTH_PREFIX + "resend-verification";
  private static final String VERIFY = AUTH_PREFIX + "verify-email";

  private final RateLimiterService rateLimiter;
  private final RateLimitProperties properties;
  private final ObjectMapper objectMapper;

  public RateLimitFilter(
      RateLimiterService rateLimiter, RateLimitProperties properties, ObjectMapper objectMapper) {
    this.rateLimiter = rateLimiter;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Bucket bucket = bucketFor(request);
    if (bucket != null) {
      try {
        rateLimiter.consume(
            RateLimitKeys.ip(request.getRemoteAddr()), bucket.limit(), bucket.window());
      } catch (RateLimitedException ex) {
        // Outside the DispatcherServlet, so @RestControllerAdvice never sees this: the envelope
        // and the Retry-After header are written here, byte-identical to the in-controller path.
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        ApiErrorWriter.write(
            response, objectMapper, ex.getStatus().value(), ex.getCode(), ex.getMessage());
        return;
      }
    }
    filterChain.doFilter(request, response);
  }

  /** The IP budget this request costs, or {@code null} when the table gives it none. */
  private Bucket bucketFor(HttpServletRequest request) {
    String path = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
      path = path.substring(contextPath.length());
    }
    if (!path.startsWith(AUTH_PREFIX)) {
      return null;
    }
    return switch (path) {
      case LOGIN -> new Bucket(properties.getLoginIpLimit(), properties.getLoginIpWindow());
      case REGISTER, RESEND, VERIFY ->
          new Bucket(properties.getEmailIpLimit(), properties.getEmailIpWindow());
      default -> null;
    };
  }

  private record Bucket(int limit, Duration window) {}
}
