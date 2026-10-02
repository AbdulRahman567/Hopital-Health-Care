package com.healthcare.hms.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.hms.common.api.ApiErrorWriter;
import com.healthcare.hms.common.exception.ErrorCodes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * P4.2 — tenant resolution (TDD section 6.2.2): the tenant comes from the {@code tenantId} claim of
 * an already-verified bearer token, never from the request.
 *
 * <p>Registered after {@code BearerTokenAuthenticationFilter}, which is exactly TDD section 4.1's
 * order — Rate limit → Authentication → Tenant Resolution → Authorization. Reading the claim from
 * the authenticated principal (rather than decoding the token a second time) means there is one
 * verification path and one place that can say what the tenant is.
 *
 * <p>Rejections:
 *
 * <ul>
 *   <li>no bearer token → nothing happens here; the chain keeps its normal 401 and public routes
 *       stay public;
 *   <li>authenticated but the token carries no usable {@code tenantId} claim → <b>401
 *       UNAUTHENTICATED</b> (decision D4: a missing tenant claim is an authentication defect) and
 *       the chain never continues;
 * </ul>
 *
 * <p>The context is always cleared in {@code finally}, including when the chain throws, so a pooled
 * thread can never inherit another tenant's scope (ENGINEERING_RULES section 1.2).
 */
public class TenantContextFilter extends OncePerRequestFilter {

  /** Claim name fixed by TDD section 7; Phase 5 issues it at login. */
  public static final String TENANT_CLAIM = "tenantId";

  private static final Logger logger = LoggerFactory.getLogger(TenantContextFilter.class);

  private final ObjectMapper objectMapper;

  public TenantContextFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    try {
      if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
        UUID tenantId = tenantIdClaim(jwtAuthentication.getToken());
        if (tenantId == null) {
          ApiErrorWriter.write(
              response,
              objectMapper,
              401,
              ErrorCodes.UNAUTHENTICATED,
              "Token does not carry a tenant claim.");
          return;
        }
        TenantContext.set(tenantId);
      }
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  /**
   * Reads the {@code tenantId} claim.
   *
   * @return the claim as a UUID, or {@code null} when it is absent or not a UUID — both are treated
   *     as "no tenant claim" and rejected upstream
   */
  private UUID tenantIdClaim(Jwt jwt) {
    Object claim = jwt.getClaims().get(TENANT_CLAIM);
    if (claim == null) {
      return null;
    }
    try {
      return UUID.fromString(claim.toString());
    } catch (IllegalArgumentException ex) {
      logger.debug("Bearer token carries a non-UUID '{}' claim; rejecting.", TENANT_CLAIM);
      return null;
    }
  }
}
