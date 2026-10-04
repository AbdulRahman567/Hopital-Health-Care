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
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
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

  private static final PathPatternRequestMatcher LOGIN =
      PathPatternRequestMatcher.withDefaults().matcher(AUTH_PREFIX + "login");
  private static final PathPatternRequestMatcher REGISTER =
      PathPatternRequestMatcher.withDefaults().matcher(AUTH_PREFIX + "register-hospital");
  private static final PathPatternRequestMatcher RESEND =
      PathPatternRequestMatcher.withDefaults().matcher(AUTH_PREFIX + "resend-verification");
  private static final PathPatternRequestMatcher VERIFY =
      PathPatternRequestMatcher.withDefaults().matcher(AUTH_PREFIX + "verify-email");

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

  /**
   * The IP budget this request costs, or {@code null} when the table gives it none.
   *
   * <p>Matched with {@link PathPatternRequestMatcher} — the very type {@code
   * SecurityConfig.requestMatchers(...)} uses — so this filter, the authorisation layer and Spring
   * MVC can no longer disagree about which route a request is (SEC-2). Matching a {@code
   * PathPattern} ignores path parameters and does not treat a trailing separator as a match, which
   * is exactly what the handler mapping does; comparing {@code getRequestURI()} by string equality
   * did neither.
   */
  private Bucket bucketFor(HttpServletRequest request) {
    if (LOGIN.matches(request)) {
      return new Bucket(properties.getLoginIpLimit(), properties.getLoginIpWindow());
    }
    if (REGISTER.matches(request) || RESEND.matches(request) || VERIFY.matches(request)) {
      return new Bucket(properties.getEmailIpLimit(), properties.getEmailIpWindow());
    }
    return null;
  }

  private record Bucket(int limit, Duration window) {}
}
