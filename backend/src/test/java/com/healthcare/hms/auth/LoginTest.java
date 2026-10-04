package com.healthcare.hms.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.healthcare.hms.tenant.TenantContext;
import com.healthcare.hms.tenant.TenantContextFilter;
import com.healthcare.hms.tenant.TenantStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * P5.3 — password hashing and login (FR-2.1 / FR-2.2, plan section 6).
 *
 * <p>The whole suite runs through the real {@code SecurityFilterChain}, the real D1 slug leg and
 * the real encoder; only the rows are fixtures. Two properties are asserted everywhere:
 *
 * <ul>
 *   <li>a successful login returns a token the <b>P4.2 decoder</b> accepts, with exactly the claims
 *       TDD section 7 names and a lifetime inside the PRD's 10–15 minute window;
 *   <li>every failure — unknown slug, unknown address, wrong password, unverified or suspended
 *       tenant, inactive account, MFA-enabled account — is one byte-identical 401 (decision D9).
 * </ul>
 *
 * <p>Rows this suite creates are removed again in {@code @AfterEach} (slug prefix {@code p53-}),
 * because the database is JVM-scoped and shared with every other suite (TESTING section 4).
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginTest {

  private static final String LOGIN = "/api/v1/auth/login";
  private static final String PASSWORD = "Correct-Horse-Battery-9";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JwtDecoder jwtDecoder;

  @AfterEach
  void cleanUp() {
    assertThat(TenantContext.find()).as("no ThreadLocal leak after a login request").isEmpty();
    TenantContext.clear();
    fixtures.deleteTenantsStartingWith("p53-%");
  }

  // -------------------------------------------------------------------------
  // success
  // -------------------------------------------------------------------------

  @Test
  void successReturnsATokenTheP42DecoderAcceptsWithTheRequiredClaims() throws Exception {
    UUID tenantId = fixtures.createTenant("p53-active", TenantStatus.ACTIVE);
    User user = fixtures.createUser(tenantId, "admin@p53.test", PASSWORD, UserStatus.ACTIVE, false);

    JsonNode data =
        objectMapper.readTree(attempt("p53-active", "admin@p53.test", PASSWORD, 200)).get("data");

    assertThat(data.get("tokenType").asText()).isEqualTo("Bearer");
    long expiresIn = data.get("expiresIn").asLong();
    assertThat(expiresIn).as("PRD access-token window is 10-15 minutes").isBetween(600L, 900L);

    // The decoder bean is the one SecurityConfig builds for every request, so this also proves a
    // token issued here is usable on the API (P4.2 unchanged, decision D10).
    Jwt jwt = jwtDecoder.decode(data.get("accessToken").asText());
    assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
    assertThat(jwt.getClaimAsString(TenantContextFilter.TENANT_CLAIM))
        .isEqualTo(tenantId.toString());
    assertThat(jwt.getId()).as("jti is issued, never reused").isNotBlank();

    Object roles = jwt.getClaim(JwtTokenService.ROLES_CLAIM);
    assertThat(roles).as("roles is declared, empty until Phase 6").isInstanceOf(Collection.class);
    assertThat((Collection<?>) roles).isEmpty();

    Duration lifetime = Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt());
    assertThat(lifetime.toSeconds())
        .as("exp - iat is the configured TTL, inside 600-900 s")
        .isBetween(600L, 900L)
        .isEqualTo(expiresIn);
  }

  // -------------------------------------------------------------------------
  // failures
  // -------------------------------------------------------------------------

  @Test
  void everyFailureBranchReturnsTheSame401Body() throws Exception {
    UUID active = fixtures.createTenant("p53-ok", TenantStatus.ACTIVE);
    UUID pending = fixtures.createTenant("p53-pending", TenantStatus.PENDING);
    UUID suspended = fixtures.createTenant("p53-suspended", TenantStatus.SUSPENDED);
    fixtures.createUser(active, "known@p53.test", PASSWORD, UserStatus.ACTIVE, false);
    fixtures.createUser(pending, "pending@p53.test", PASSWORD, UserStatus.ACTIVE, false);
    fixtures.createUser(suspended, "suspended@p53.test", PASSWORD, UserStatus.ACTIVE, false);
    fixtures.createUser(active, "newuser@p53.test", PASSWORD, UserStatus.PENDING, false);
    fixtures.createUser(active, "inactive@p53.test", PASSWORD, UserStatus.INACTIVE, false);
    fixtures.createUser(active, "mfa@p53.test", PASSWORD, UserStatus.ACTIVE, true);

    List<String> bodies = new ArrayList<>();
    bodies.add(failure("p53-nobody-here", "nobody@p53.test", PASSWORD)); // unknown slug
    bodies.add(failure("p53-ok", "nobody@p53.test", PASSWORD)); // unknown email
    bodies.add(failure("p53-ok", "known@p53.test", "Wrong-Password-99")); // wrong password
    bodies.add(failure("p53-pending", "pending@p53.test", PASSWORD)); // tenant PENDING
    bodies.add(failure("p53-suspended", "suspended@p53.test", PASSWORD)); // tenant SUSPENDED
    bodies.add(failure("p53-ok", "newuser@p53.test", PASSWORD)); // user PENDING
    bodies.add(failure("p53-ok", "inactive@p53.test", PASSWORD)); // user INACTIVE
    bodies.add(failure("p53-ok", "mfa@p53.test", PASSWORD)); // MFA enabled (D12)

    String first = bodies.get(0);
    assertThat(first)
        .as("every branch answers with the one uniform 401 (decision D9)")
        .contains("\"code\":\"UNAUTHENTICATED\"")
        .contains(InvalidCredentialsException.MESSAGE);
    assertThat(bodies).allSatisfy(body -> assertThat(body).isEqualTo(first));
  }

  @Test
  void anMfaEnabledAccountIsRejectedWithoutBeingGivenAToken() throws Exception {
    UUID tenantId = fixtures.createTenant("p53-mfa", TenantStatus.ACTIVE);
    fixtures.createUser(tenantId, "mfa@p53.test", PASSWORD, UserStatus.ACTIVE, true);

    mockMvc
        .perform(
            post(LOGIN)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"hospitalSlug":"p53-mfa","email":"mfa@p53.test","password":"%s"}
                    """
                        .formatted(PASSWORD)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.error.message").value(InvalidCredentialsException.MESSAGE))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  void theUserFixturesHashNeverVerifies() throws Exception {
    UUID tenantId = fixtures.createTenant("p53-fixture-hash", TenantStatus.ACTIVE);
    User fixtureOnly = UserFixtures.user("fixture-only@p53.test");
    fixtures.createUserWithHash(
        tenantId, fixtureOnly.getEmail(), fixtureOnly.getPasswordHash(), UserStatus.ACTIVE, false);

    failure("p53-fixture-hash", "fixture-only@p53.test", PASSWORD);
  }

  // -------------------------------------------------------------------------
  // input validation
  // -------------------------------------------------------------------------

  @Test
  void malformedAndUnknownLoginBodiesAre422() throws Exception {
    mockMvc
        .perform(post(LOGIN).contentType(APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.fields[?(@.field == 'hospitalSlug')]").exists())
        .andExpect(jsonPath("$.error.fields[?(@.field == 'email')]").exists())
        .andExpect(jsonPath("$.error.fields[?(@.field == 'password')]").exists());

    // API.md section 5: server-controlled properties stay structurally closed at login too.
    mockMvc
        .perform(
            post(LOGIN)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"hospitalSlug":"p53-x","email":"a@p53.test","password":"%s",
                     "tenantId":"00000000-0000-0000-0000-0000000000ff"}
                    """
                        .formatted(PASSWORD)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.fields[0].field").value("tenantId"))
        .andExpect(jsonPath("$.error.fields[0].message").value("Unknown property."));
  }

  // -------------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------------

  /** Performs one login and asserts it succeeded, returning the raw response body. */
  private String attempt(String slug, String email, String password, int expectedStatus)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(LOGIN)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"hospitalSlug":"%s","email":"%s","password":"%s"}
                        """
                            .formatted(slug, email, password)))
            .andExpect(status().is(expectedStatus))
            .andReturn();
    return result.getResponse().getContentAsString();
  }

  /**
   * The uniform 401, with the two envelope fields that legitimately differ between requests gone.
   */
  private String failure(String slug, String email, String password) throws Exception {
    return normalize(attempt(slug, email, password, 401));
  }

  private String normalize(String json) throws Exception {
    JsonNode node = objectMapper.readTree(json);
    ((ObjectNode) node).remove("timestamp");
    ((ObjectNode) node).remove("traceId");
    return objectMapper.writeValueAsString(node);
  }
}
