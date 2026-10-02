package com.healthcare.hms.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

/**
 * P4.2 — the tenant can only come from a verified bearer token (TDD section 6.2.2, ROADMAP P4.2).
 *
 * <p>Tokens are signed in-test with the same HS256 secret surefire injects, so the real {@code
 * JwtDecoder} in {@code SecurityConfig} verifies them: this suite proves the wiring, not a stub.
 *
 * <p>The 401 → 401 → 403 ladder is the point: 401 means "rejected before authorization", 403 means
 * "authenticated, tenant resolved, still denied by the deny-by-default rule".
 */
@SpringBootTest
@AutoConfigureMockMvc
class TenantContextFilterTest {

  private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");

  /** Never the surefire secret, so the signature check has something to reject. */
  private static final String WRONG_SECRET = "another-signing-key-0123456789abcdef0123456789abcdef";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Value("${hms.security.jwt-secret}")
  private String jwtSecret;

  @AfterEach
  void reset() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Nested
  class WithoutToken {

    @Test
    void apiRequestsStillGetThePhase2UnauthenticatedEnvelope() throws Exception {
      mockMvc
          .perform(get("/api/v1/patients"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
          .andExpect(jsonPath("$.error.message").value("Authentication required."));
    }

    @Test
    void healthStaysPublicWithoutAToken() throws Exception {
      mockMvc
          .perform(get("/actuator/health"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("UP"));
    }
  }

  @Nested
  class TokenWithoutTenantClaim {

    @Test
    void validTokenMissingTheClaimIsRejectedWith401AndTheTenantClaimMessage() throws Exception {
      mockMvc
          .perform(get("/api/v1/patients").header("Authorization", "Bearer " + signed(Map.of())))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
          .andExpect(jsonPath("$.error.message").value("Token does not carry a tenant claim."));
    }

    @Test
    void validTokenWithANonUuidClaimIsRejectedTheSameWay() throws Exception {
      mockMvc
          .perform(
              get("/api/v1/patients")
                  .header("Authorization", "Bearer " + signed(Map.of("tenantId", "hospital-a"))))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
          .andExpect(jsonPath("$.error.message").value("Token does not carry a tenant claim."));
    }
  }

  @Nested
  class TokenWithTenantClaim {

    @Test
    void tenantIsResolvedAndAuthorizationStillDeniesByDefault() throws Exception {
      mockMvc
          .perform(get("/api/v1/patients").header("Authorization", "Bearer " + signedFor(TENANT_A)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void healthWithATokenStillSucceeds() throws Exception {
      mockMvc
          .perform(get("/actuator/health").header("Authorization", "Bearer " + signedFor(TENANT_A)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("UP"));
    }
  }

  @Nested
  class InvalidToken {

    @Test
    void expiredTokenIsRejectedWithTheStandardEnvelopeNotAnEmptyBody() throws Exception {
      String expired =
          signed(
              Map.of("tenantId", TENANT_A.toString()),
              Instant.now().minusSeconds(7200),
              Instant.now().minusSeconds(3600),
              jwtSecret);

      mockMvc
          .perform(get("/api/v1/patients").header("Authorization", "Bearer " + expired))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
          .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejectedWithTheStandardEnvelope() throws Exception {
      String forged = signed(Map.of("tenantId", TENANT_A.toString()), WRONG_SECRET);

      mockMvc
          .perform(get("/api/v1/patients").header("Authorization", "Bearer " + forged))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
          .andExpect(jsonPath("$.error.message").exists());
    }
  }

  @Nested
  class FilterUnitBehaviour {

    @Test
    void contextEqualsTheClaimInsideTheChainAndIsEmptyAfterItReturns() throws Exception {
      TenantContextFilter filter = new TenantContextFilter(objectMapper);
      AtomicReference<UUID> insideChain = new AtomicReference<>();
      authenticateAs(TENANT_A);

      filter.doFilter(request(), new MockHttpServletResponse(), chainedContext(insideChain));

      assertThat(insideChain.get()).isEqualTo(TENANT_A);
      assertThat(TenantContext.find()).isEmpty();
    }

    @Test
    void contextIsClearedWhenTheChainThrows() {
      TenantContextFilter filter = new TenantContextFilter(objectMapper);
      FilterChain throwing =
          (req, res) -> {
            throw new ServletException("boom");
          };
      authenticateAs(TENANT_A);

      assertThatThrownBy(() -> filter.doFilter(request(), new MockHttpServletResponse(), throwing))
          .isInstanceOf(ServletException.class);

      assertThat(TenantContext.find()).isEmpty();
    }

    @Test
    void nothingIsBoundWhenTheRequestCarriesNoAuthentication() throws Exception {
      TenantContextFilter filter = new TenantContextFilter(objectMapper);
      AtomicReference<UUID> insideChain = new AtomicReference<>();

      SecurityContextHolder.clearContext();
      filter.doFilter(request(), new MockHttpServletResponse(), chainedContext(insideChain));

      assertThat(insideChain.get()).isNull();
      assertThat(TenantContext.find()).isEmpty();
    }

    private MockHttpServletRequest request() {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/patients");
      request.addHeader("Authorization", "Bearer unsigned-in-this-unit-test");
      return request;
    }

    private FilterChain chainedContext(AtomicReference<UUID> observed) {
      return (req, res) -> observed.set(TenantContext.find().orElse(null));
    }
  }

  // -------------------------------------------------------------------------
  // token helpers — real HS256 signatures, no stubbing of the decoder
  // -------------------------------------------------------------------------

  private String signedFor(UUID tenantId) {
    return signed(Map.of(TenantContextFilter.TENANT_CLAIM, tenantId.toString()));
  }

  private String signed(Map<String, Object> claims) {
    return signed(claims, jwtSecret);
  }

  private String signed(Map<String, Object> claims, String secret) {
    Instant issuedAt = Instant.now().minusSeconds(60);
    return signed(claims, issuedAt, issuedAt.plusSeconds(900), secret);
  }

  private String signed(
      Map<String, Object> claims, Instant issuedAt, Instant expiresAt, String secret) {
    JWTClaimsSet.Builder builder =
        new JWTClaimsSet.Builder()
            .subject("user-00000000-0000-0000-0000-000000000042")
            .issuer("healthcare-hms")
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt));
    claims.forEach(builder::claim);
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), builder.build());
    try {
      jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
    } catch (JOSEException ex) {
      throw new IllegalStateException("test token could not be signed", ex);
    }
    return jwt.serialize();
  }

  private void authenticateAs(UUID tenantId) {
    Jwt jwt =
        Jwt.withTokenValue("unit-test-token")
            .header("alg", "HS256")
            .claims(claim -> claim.put(TenantContextFilter.TENANT_CLAIM, tenantId.toString()))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
  }
}
