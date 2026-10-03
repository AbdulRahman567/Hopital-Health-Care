package com.healthcare.hms.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.healthcare.hms.auth.repository.UserRepository;
import com.healthcare.hms.tenant.TenantContext;
import com.healthcare.hms.tenant.TenantContextFilter;
import jakarta.servlet.http.Cookie;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * P5.8 &mdash; authorization and tenant isolation on the surface Phase 5 actually has (ROADMAP
 * P5.8, plan section 4).
 *
 * <p>Phase 5 ships anonymous auth endpoints only: there is no {@code GET /users} to attack yet, so
 * this suite proves the four properties the pipeline guarantees and the two the endpoints can
 * already be caught violating:
 *
 * <ol>
 *   <li>unauthenticated &rarr; 401 on every non-public route, and authenticated-but-undecided
 *       &rarr; 403 &mdash; deny-by-default is not merely "no token";
 *   <li>tenant A's credentials submitted under tenant B's slug never mint a session (decision D9);
 *   <li>the {@code tenantId} claim always equals the <i>user's own</i> tenant;
 *   <li>the {@code X-Tenant-ID} hint only ever rejects; it can never switch the bound context;
 *   <li>the claim &mdash; not the row a {@code sub} happens to name &mdash; decides what a read can
 *       see.
 * </ol>
 *
 * <p><b>The probe handler.</b> Assertions 3&ndash;5 need something that reports the bound {@link
 * TenantContext} over HTTP, and production configuration offers no such route: {@code
 * SecurityConfig} is {@code denyAll()} for everything outside its list, and every path on that list
 * is a POST to a handler that exists for another purpose. The one path left open without a method
 * restriction is the Swagger UI's {@code /swagger-ui/**}, which springdoc serves from a
 * <i>resource</i> handler &mdash; a different mapping registry, so an MVC handler registered there
 * by this class's {@link ProbeConfiguration} wins outright and never clashes. Test-only: nothing
 * under {@code src/test} reaches a packaged application.
 *
 * <p><b>Recorded limitation</b> (the plan records the same one): "tenant B user cannot read tenant
 * A's <i>user list</i>" needs a resource endpoint, and none exists until Phase 6/9 &mdash; the
 * per-endpoint proof stays at P6.7 {@code WrongTenantTest}. What is proved here instead is that a
 * single-row read is scoped by the claim, end to end, which is the mechanism that list will rest
 * on.
 *
 * <p>Fixtures are TESTING section 4's {@code hospital-a} / {@code hospital-b} (fixed ids, {@code
 * ACTIVE}); every row this suite adds to them is removed again in {@code @AfterEach}, in the order
 * the V4 foreign keys require, because {@code TenantIsolationIT} counts rows in exactly these two
 * tenants.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthTenantIsolationTest {

  /** TESTING.md section 4 fixtures &mdash; fixed ids so both suites and the docs agree. */
  private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");

  private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
  private static final String SLUG_A = "hospital-a";
  private static final String SLUG_B = "hospital-b";
  private static final String ADMIN_A = "admin@hospital-a.test";
  private static final String ADMIN_B = "admin@hospital-b.test";

  private static final String LOGIN = "/api/v1/auth/login";
  private static final String REFRESH = "/api/v1/auth/refresh";
  private static final String LOGOUT = "/api/v1/auth/logout";
  private static final String PROBE = "/swagger-ui/tenant-probe";

  private static final String CSRF_HEADER = "X-Requested-With";
  private static final String CSRF_VALUE = "XMLHttpRequest";
  private static final String PASSWORD = "Correct-Horse-Battery-9";

  /** The two hospitals, each with the account and tenant it owns. */
  static Stream<Arguments> hospitals() {
    return Stream.of(
        Arguments.of(SLUG_A, TENANT_A, ADMIN_A), Arguments.of(SLUG_B, TENANT_B, ADMIN_B));
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private AuthFixtures fixtures;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtDecoder jwtDecoder;
  @Autowired private JwtEncoder jwtEncoder;

  private User adminA;
  private User adminB;

  @BeforeEach
  void createFixtures() {
    ensureTenant(TENANT_A, "Hospital A", SLUG_A);
    ensureTenant(TENANT_B, "Hospital B", SLUG_B);
    clearFixtureRows();
    adminA = fixtures.createUser(TENANT_A, ADMIN_A, PASSWORD, UserStatus.ACTIVE, false);
    adminB = fixtures.createUser(TENANT_B, ADMIN_B, PASSWORD, UserStatus.ACTIVE, false);
  }

  @AfterEach
  void cleanUp() {
    assertThat(TenantContext.find())
        .as("no ThreadLocal leak after any request (plan P5.2 assertion 7)")
        .isEmpty();
    TenantContext.clear();
    clearFixtureRows();
  }

  // -------------------------------------------------------------------------
  // 1. the unauthenticated ladder
  // -------------------------------------------------------------------------

  @Test
  void everyNonPublicRouteAnswers401ToAnUnauthenticatedCaller() throws Exception {
    for (String path : List.of("/api/v1/patients", "/api/v1/hospital", "/actuator/env")) {
      mockMvc
          .perform(get(path))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
          .andExpect(jsonPath("$.data").doesNotExist());
    }

    // refresh/logout are on the permit list, so the guard ahead of them is the only thing that
    // can say no. With the header present the request falls through to the missing cookie.
    for (String path : List.of(REFRESH, LOGOUT)) {
      mockMvc
          .perform(post(path).header(CSRF_HEADER, CSRF_VALUE))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    // ...and without it the custom-header guard answers first, which is decision D5, not a 401.
    mockMvc
        .perform(post(REFRESH))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
  }

  /**
   * The other half of deny-by-default: {@code anyRequest().denyAll()} must refuse a caller who
   * <i>did</i> authenticate, or the whole policy would amount to "unauthenticated requests are
   * rejected" and nothing more.
   */
  @Test
  void anAuthenticatedCallerIsStillDeniedEveryNonPublicRoute() throws Exception {
    String token = login(SLUG_A, ADMIN_A);

    mockMvc
        .perform(get("/api/v1/patients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  // -------------------------------------------------------------------------
  // 2. cross-tenant credentials never mint a session
  // -------------------------------------------------------------------------

  @Test
  void credentialsFromOneHospitalAreRejectedUnderTheOthersSlug() throws Exception {
    String aUnderB = failure(SLUG_B, ADMIN_A, PASSWORD);
    String bUnderA = failure(SLUG_A, ADMIN_B, PASSWORD);

    assertThat(aUnderB)
        .as("decision D9: which of the two it was must not be recoverable from the response")
        .isEqualTo(bUnderA)
        .contains("\"code\":\"UNAUTHENTICATED\"")
        .contains(InvalidCredentialsException.MESSAGE);

    for (String body : List.of(aUnderB, bUnderA)) {
      assertThat(body)
          .as("no token is handed to a caller who cannot name a tenant")
          .doesNotContain("accessToken");
    }
    assertThat(crossTenantLoginSetCookie(SLUG_B, ADMIN_A))
        .as("a rejected login starts no session")
        .isNull();
    assertThat(crossTenantLoginSetCookie(SLUG_A, ADMIN_B)).isNull();
  }

  // -------------------------------------------------------------------------
  // 3. the claim is always the user's own tenant
  // -------------------------------------------------------------------------

  @ParameterizedTest(name = "{0}")
  @MethodSource("hospitals")
  void theTenantClaimAlwaysEqualsTheUsersOwnTenant(String slug, UUID tenantId, String email)
      throws Exception {
    String token = login(slug, email);

    Jwt jwt = jwtDecoder.decode(token);
    assertThat(jwt.getClaimAsString(TenantContextFilter.TENANT_CLAIM))
        .as("the claim is derived from the account, never from the request")
        .isEqualTo(tenantId.toString());
    UUID accountId =
        TenantContext.call(tenantId, () -> userRepository.findByEmail(email).orElseThrow().getId());
    assertThat(jwt.getSubject())
        .as("sub is the account that signed in, in the tenant it belongs to")
        .isEqualTo(accountId.toString());
  }

  // -------------------------------------------------------------------------
  // 4. the hint only ever rejects
  // -------------------------------------------------------------------------

  @Test
  void theHintOnlyEverRejectsAndNeverSwitchesTheBoundContext() throws Exception {
    String token = login(SLUG_A, ADMIN_A);

    // Absent or matching: the request proceeds, and the probe reports the claim's tenant.
    assertThat(probeTenant(token, null)).isEqualTo(TENANT_A);
    assertThat(probeTenant(token, TENANT_A)).isEqualTo(TENANT_A);

    // Foreign: 404 before the handler, whatever route it is pointed at - including one the chain
    // would otherwise have answered 403, which proves the rejection happens ahead of authorization.
    mockMvc
        .perform(
            get(PROBE)
                .param("userId", adminA.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(TenantContextFilter.TENANT_HINT_HEADER, TENANT_B.toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

    mockMvc
        .perform(
            get("/api/v1/patients")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(TenantContextFilter.TENANT_HINT_HEADER, TENANT_B.toString()))
        .andExpect(status().isNotFound());

    // Had the header been able to rebind the context, the matching case below would have answered
    // 404 with B and this one would not have answered 404 at all.
    assertThat(probeTenant(token, TENANT_B)).isNull();
    assertThat(TenantContext.find())
        .as("whatever the answer, the context does not outlive the request")
        .isEmpty();
  }

  // -------------------------------------------------------------------------
  // 5. the claim decides what a read can see
  // -------------------------------------------------------------------------

  /**
   * Defense in depth, and the reason {@code @TenantId} is worth having.
   *
   * <p>A hand-crafted token &mdash; signed with the real test secret, so the P4.2 decoder accepts
   * it &mdash; names tenant B in its {@code tenantId} claim while {@code sub} is a tenant-A
   * account. The row and the claim now disagree, and only the claim may win: the probe must report
   * B, and a read of that very account under B must come back empty.
   */
  @Test
  void theClaimNotTheRowDecidesWhatAReadCanSee() throws Exception {
    String forged = forgedToken(adminA.getId(), TENANT_B);

    // The bound context is the claim: B, not the tenant the named account belongs to.
    JsonNode underClaim = probe(forged, TENANT_B, adminA.getId());
    assertThat(underClaim.get("tenantId").asText()).isEqualTo(TENANT_B.toString());
    assertThat(underClaim.get("visible").asBoolean())
        .as("tenant A's row is invisible to a read bound to tenant B")
        .isFalse();

    // The other hint is rejected, because the bound tenant is B and not A.
    mockMvc
        .perform(
            get(PROBE)
                .param("userId", adminA.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged)
                .header(TenantContextFilter.TENANT_HINT_HEADER, TENANT_A.toString()))
        .andExpect(status().isNotFound());

    // The same row, read under its own tenant, is there - so the empty result above is scoping and
    // not a missing fixture.
    String genuine = login(SLUG_A, ADMIN_A);
    assertThat(probe(genuine, TENANT_A, adminA.getId()).get("visible").asBoolean()).isTrue();
  }

  // -------------------------------------------------------------------------
  // 6. refresh isolation between tenants
  // -------------------------------------------------------------------------

  @Test
  void tenantBRotationsNeverTouchTenantARows() throws Exception {
    String cookieA = loginReturningCookie(SLUG_A, ADMIN_A);
    String cookieB = loginReturningCookie(SLUG_B, ADMIN_B);

    List<Map<String, Object>> before = tenantARows();
    assertThat(before).as("the fixture started a session").isNotEmpty();

    mockMvc
        .perform(
            post(LOGOUT)
                .cookie(refreshCookieOf(cookieA))
                .header(CSRF_HEADER, CSRF_VALUE)
                .contentType(APPLICATION_JSON))
        .andExpect(status().isOk());

    mockMvc
        .perform(post(REFRESH).cookie(refreshCookieOf(cookieA)).header(CSRF_HEADER, CSRF_VALUE))
        .andExpect(status().isUnauthorized());

    List<Map<String, Object>> afterLogout = tenantARows();
    assertThat(afterLogout)
        .extracting(row -> row.get("revoked_reason"))
        .as("logout revoked every row of A's family")
        .isNotEmpty()
        .containsOnly("LOGOUT");

    // Two rotations of B's family.
    for (int round = 1; round <= 2; round++) {
      String successor = rotate(cookieB);
      cookieB = successor;
    }

    assertThat(tenantARows())
        .as("another tenant's rotations leave A's rows byte-for-byte alone")
        .isEqualTo(afterLogout);

    Integer liveB =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refresh_tokens WHERE tenant_id = ? AND revoked_at IS NULL",
            Integer.class,
            bytesOf(TENANT_B));
    assertThat(liveB).as("B's own family is the one that rotated").isGreaterThan(0);

    Integer rowsA =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refresh_tokens WHERE tenant_id = ?",
            Integer.class,
            bytesOf(TENANT_A));
    assertThat(rowsA).isEqualTo(afterLogout.size());
  }

  // -------------------------------------------------------------------------
  // 7. nothing leaks across requests
  // -------------------------------------------------------------------------

  @Test
  void tenantContextIsEmptyAfterEveryKindOfRequest() throws Exception {
    String token = login(SLUG_A, ADMIN_A);

    mockMvc.perform(get("/api/v1/patients")).andExpect(status().isUnauthorized());
    assertThat(TenantContext.find()).isEmpty();

    mockMvc
        .perform(
            get(PROBE)
                .param("userId", adminA.getId().toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk());
    assertThat(TenantContext.find())
        .as("the probe itself must not leave a tenant behind for the next request")
        .isEmpty();

    mockMvc
        .perform(
            post(LOGIN)
                .contentType(APPLICATION_JSON)
                .content(loginBody(SLUG_A, ADMIN_A, "Wrong-Password-99")))
        .andExpect(status().isUnauthorized());
    assertThat(TenantContext.find()).isEmpty();

    mockMvc
        .perform(post(REFRESH).header(CSRF_HEADER, CSRF_VALUE))
        .andExpect(status().isUnauthorized());
    assertThat(TenantContext.find()).isEmpty();
  }

  // -------------------------------------------------------------------------
  // prod: the same ladder, and the docs still closed
  // -------------------------------------------------------------------------

  @Nested
  @SpringBootTest(properties = "spring.profiles.active=prod")
  @AutoConfigureMockMvc
  class ProdProfile {

    @Autowired private MockMvc prodMockMvc;

    @Test
    void theLadderHoldsAndTheApiDocsAreNotReachableAnonymously() throws Exception {
      prodMockMvc
          .perform(get("/api/v1/patients"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

      // P2.6: springdoc is switched off in prod, so there is nothing here to be unauthenticated
      // against - 404 rather than 401 is the point.
      prodMockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
      prodMockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }
  }

  // -------------------------------------------------------------------------
  // the probe handler
  // -------------------------------------------------------------------------

  /**
   * Registers the probe described in the class javadoc.
   *
   * <p>The bean is produced here rather than declared with {@code @Component} so it exists only in
   * this suite's application context, and nowhere in a packaged application.
   */
  @TestConfiguration
  static class ProbeConfiguration {

    @Bean
    TenantProbeController tenantProbeController(UserRepository userRepository) {
      return new TenantProbeController(userRepository);
    }
  }

  /**
   * Reports the tenant this request is bound to, and whether one given account is visible from it.
   *
   * <p>Two facts in one answer, because the interesting case is the disagreement between them: the
   * claim may say B while {@code sub} names a tenant-A account, and only the first may decide.
   * {@code visible} is read through the ordinary tenant-filtered repository, so it is the same code
   * path a real resource endpoint will use.
   */
  @RestController
  static class TenantProbeController {

    private final UserRepository userRepository;

    TenantProbeController(UserRepository userRepository) {
      this.userRepository = userRepository;
    }

    @GetMapping(PROBE)
    Map<String, Object> probe(@RequestParam("userId") String userId) {
      Map<String, Object> body = new LinkedHashMap<>();
      var tenant = TenantContext.find();
      body.put("tenantId", tenant.map(UUID::toString).orElse(null));
      // No tenant bound means no read is possible at all - Hibernate refuses to open a session,
      // which is why the guard below is a fact about the pipeline and not a convenience.
      body.put(
          "visible",
          tenant
              .map(t -> userRepository.findById(UUID.fromString(userId)).isPresent())
              .orElse(null));
      return body;
    }
  }

  // -------------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------------

  private String login(String slug, String email) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(LOGIN).contentType(APPLICATION_JSON).content(loginBody(slug, email, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    assertThat(data.get("tokenType").asText()).isEqualTo(JwtTokenService.TOKEN_TYPE);
    return data.get("accessToken").asText();
  }

  private String loginReturningCookie(String slug, String email) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(LOGIN).contentType(APPLICATION_JSON).content(loginBody(slug, email, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie).as("login hands out the refresh cookie").isNotNull();
    return valueOf(setCookie);
  }

  private String rotate(String cookieValue) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(REFRESH)
                    .cookie(refreshCookieOf(cookieValue))
                    .header(CSRF_HEADER, CSRF_VALUE)
                    .header(HttpHeaders.USER_AGENT, "isolation-test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").exists())
            .andReturn();
    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie).isNotNull();
    return valueOf(setCookie);
  }

  private String failure(String slug, String email, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(LOGIN).contentType(APPLICATION_JSON).content(loginBody(slug, email, password)))
            .andExpect(status().isUnauthorized())
            .andReturn();
    JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
    ((ObjectNode) node).remove("timestamp");
    ((ObjectNode) node).remove("traceId");
    return objectMapper.writeValueAsString(node);
  }

  private String crossTenantLoginSetCookie(String slug, String email) throws Exception {
    return mockMvc
        .perform(
            post(LOGIN).contentType(APPLICATION_JSON).content(loginBody(slug, email, PASSWORD)))
        .andExpect(status().isUnauthorized())
        .andReturn()
        .getResponse()
        .getHeader(HttpHeaders.SET_COOKIE);
  }

  private static String loginBody(String slug, String email, String password) {
    return """
        {"hospitalSlug":"%s","email":"%s","password":"%s"}
        """
        .formatted(slug, email, password);
  }

  /** The probe's answer as a tenant id, or {@code null} when the request was rejected first. */
  private UUID probeTenant(String token, UUID hint) throws Exception {
    MockHttpServletRequestBuilder request =
        get(PROBE)
            .param("userId", adminA.getId().toString())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    if (hint != null) {
      request = request.header(TenantContextFilter.TENANT_HINT_HEADER, hint.toString());
    }
    MvcResult result = mockMvc.perform(request).andReturn();
    if (result.getResponse().getStatus() != 200) {
      assertThat(result.getResponse().getStatus())
          .as("the probe is either 200 or the hint's 404")
          .isEqualTo(404);
      return null;
    }
    return UUID.fromString(
        objectMapper.readTree(result.getResponse().getContentAsString()).get("tenantId").asText());
  }

  private JsonNode probe(String token, UUID hint, UUID userId) throws Exception {
    MockHttpServletRequestBuilder request =
        get(PROBE)
            .param("userId", userId.toString())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    if (hint != null) {
      request = request.header(TenantContextFilter.TENANT_HINT_HEADER, hint.toString());
    }
    return objectMapper.readTree(
        mockMvc
            .perform(request)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  /**
   * A token signed with the real secret but naming a tenant of the caller's choosing.
   *
   * <p>The decoder cannot tell it from one {@code JwtTokenService} issued, and that is the point:
   * signature says "who signed it", the claim says "which tenant", and isolation has to hold for a
   * token nobody at this table would have written.
   */
  private String forgedToken(UUID subjectUserId, UUID claimedTenant) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(subjectUserId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofMinutes(10)))
            .id(UUID.randomUUID().toString())
            .claim(TenantContextFilter.TENANT_CLAIM, claimedTenant.toString())
            .claim(JwtTokenService.ROLES_CLAIM, List.of())
            .build();
    return jwtEncoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  /** A's rows, in a form {@code equals} can compare: {@code id} is {@code BINARY(16)}. */
  private List<Map<String, Object>> tenantARows() {
    return jdbcTemplate.queryForList(
        "SELECT HEX(id) AS id, revoked_at, revoked_reason FROM refresh_tokens"
            + " WHERE tenant_id = ? ORDER BY created_at",
        bytesOf(TENANT_A));
  }

  private void ensureTenant(UUID id, String name, String slug) {
    Integer existing =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tenants WHERE id = ?", Integer.class, bytesOf(id));
    if (existing != null && existing > 0) {
      return;
    }
    jdbcTemplate.update(
        "INSERT INTO tenants (id, name, slug, status, timezone, created_at, updated_at, version)"
            + " VALUES (?, ?, ?, 'ACTIVE', 'UTC', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)",
        bytesOf(id),
        name,
        slug);
  }

  /** V4 declares no ON DELETE CASCADE, so children go before parents, in every direction. */
  private void clearFixtureRows() {
    jdbcTemplate.update(
        "DELETE FROM refresh_tokens WHERE tenant_id IN (?, ?)",
        bytesOf(TENANT_A),
        bytesOf(TENANT_B));
    jdbcTemplate.update(
        "DELETE FROM verification_tokens WHERE tenant_id IN (?, ?)",
        bytesOf(TENANT_A),
        bytesOf(TENANT_B));
    jdbcTemplate.update(
        "DELETE FROM users WHERE tenant_id IN (?, ?)", bytesOf(TENANT_A), bytesOf(TENANT_B));
  }

  private static byte[] bytesOf(UUID id) {
    return ByteBuffer.allocate(16)
        .putLong(id.getMostSignificantBits())
        .putLong(id.getLeastSignificantBits())
        .array();
  }

  private static Cookie refreshCookieOf(String value) {
    return new Cookie(RefreshCookieBuilder.COOKIE_NAME, value);
  }

  private static String valueOf(String setCookie) {
    String first = setCookie.split(";", 2)[0];
    int equals = first.indexOf('=');
    assertThat(first.substring(0, equals)).isEqualTo(RefreshCookieBuilder.COOKIE_NAME);
    return first.substring(equals + 1);
  }
}
