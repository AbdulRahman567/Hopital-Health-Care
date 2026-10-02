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

  /** Client tenant hint (TDD section 6.2.3). Never honored — it can only trigger a rejection. */
  public static final String TENANT_HINT_HEADER = "X-Tenant-ID";

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
        if (!tenantHintAccepted(request, tenantId)) {
          ApiErrorWriter.write(
              response, objectMapper, 404, ErrorCodes.NOT_FOUND, "Resource not found.");
          return;
        }
      }
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  /**
   * P4.3 — client tenant hints are never honored (TDD section 6.2.3, ENGINEERING_RULES section
   * 1.2). The header may be absent, may repeat the authenticated tenant (harmless) or may claim
   * another tenant — only the first two are allowed through, and the third answers 404 so the
   * caller cannot tell whether the other tenant exists (ADR-006). The header therefore has exactly
   * one possible effect on the outcome: rejection.
   *
   * <p>Evaluated only when a tenant was actually resolved, so a stray header on a public route
   * stays harmless. The path and body legs cannot be exercised yet: no endpoint accepts a tenant id
   * (API.md section 5) and P2.3 already rejects an unknown {@code tenantId} body property with 422,
   * so both vectors are structurally closed until controllers exist.
   *
   * @param request current request
   * @param tenantId tenant resolved from the verified token
   * @return {@code true} when the request may continue
   */
  private boolean tenantHintAccepted(HttpServletRequest request, UUID tenantId) {
    String hint = request.getHeader(TENANT_HINT_HEADER);
    if (hint == null || hint.isBlank()) {
      return true;
    }
    try {
      return tenantId.equals(UUID.fromString(hint.trim()));
    } catch (IllegalArgumentException ex) {
      return false;
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
