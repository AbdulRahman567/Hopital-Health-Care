package com.healthcare.hms.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.hms.auth.api.AuthController;
import com.healthcare.hms.tenant.TenantContext;
import com.healthcare.hms.tenant.TenantStatus;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * P5.5 &mdash; the refresh cookie, its custom-header guard and server-side logout (FR-2.2 / FR-2.6,
 * decisions D5 and D6, plan section 6).
 *
 * <p>Everything runs through the real {@code SecurityFilterChain}, the real {@link
 * RefreshCookieBuilder} and the real {@link RefreshTokenService} on the shared {@code hms_test}
 * database. Four properties are asserted:
 *
 * <ul>
 *   <li>the cookie carries exactly the flags decision D6 names, and its value is 43 characters of
 *       entropy that appears in no response body;
 *   <li>{@code SameSite} alone is not the CSRF answer &mdash; refresh and logout also demand a
 *       header a cross-origin page cannot supply;
 *   <li>rotation makes the presented cookie dead, and logout revokes the family in the database,
 *       not merely in the browser;
 *   <li>the cookie is never set on a response that is not login or refresh.
 * </ul>
 *
 * <p>Rows this suite creates are removed again in {@code @AfterEach} (slug prefix {@code p55-}),
 * because the database is JVM-scoped and shared with every other suite (TESTING section 4).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CookieLogoutTest {

  private static final String LOGIN = "/api/v1/auth/login";
  private static final String REFRESH = "/api/v1/auth/refresh";
  private static final String LOGOUT = "/api/v1/auth/logout";
  private static final String CSRF_HEADER = "X-Requested-With";
  private static final String CSRF_VALUE = "XMLHttpRequest";
  private static final String AGENT = "cookie-test-agent";
  private static final String PASSWORD = "Correct-Horse-Battery-9";
  private static final String PREFIX = "p55-";

  /** Decision D8 fixes the refresh lifetime at 7 days; the cookie advertises the same one. */
  private static final long SEVEN_DAYS_SECONDS = 7L * 24 * 60 * 60;

  @Autowired private MockMvc mockMvc;
  @Autowired private AuthFixtures fixtures;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String slug;
  private String email;

  @BeforeEach
  void createHospital() {
    slug = PREFIX + UUID.randomUUID();
    email = "admin@" + slug + ".test";
    UUID tenantId = fixtures.createTenant(slug, TenantStatus.ACTIVE);
    fixtures.createUser(tenantId, email, PASSWORD, UserStatus.ACTIVE, false);
  }

  @AfterEach
  void cleanUp() {
    TenantContext.clear();
    fixtures.deleteTenantsStartingWith(PREFIX + "%");
  }

  // -------------------------------------------------------------------------
  // flags (decision D6)
  // -------------------------------------------------------------------------

  @Test
  void loginSetsAnHttpOnlySameSiteCookieScopedToTheAuthPaths() throws Exception {
    String setCookie = loginSetCookie();
    Map<String, String> attributes = attributesOf(setCookie);

    assertThat(attributes)
        .as("decision D6: HttpOnly, Path, SameSite and the refresh lifetime")
        .containsEntry("Path", RefreshCookieBuilder.COOKIE_PATH)
        .containsEntry("SameSite", "Lax")
        .containsEntry("Max-Age", Long.toString(SEVEN_DAYS_SECONDS))
        .containsKey("HttpOnly");
    assertThat(setCookie)
        .as("dev keeps it over http://localhost; the prod assertion is the nested suite below")
        .doesNotContain("; Secure");
    assertThat(valueOf(setCookie))
        .as("32 random bytes, base64url - the secret itself, never its digest")
        .hasSize(43);
  }

  @Test
  void theRefreshCookieIsNeverSetOnAnUnrelatedResponse() throws Exception {
    MvcResult protectedRoute =
        mockMvc.perform(get("/api/v1/patients")).andExpect(status().isUnauthorized()).andReturn();
    assertThat(protectedRoute.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();

    MvcResult invalidBody =
        mockMvc
            .perform(post(LOGIN).contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();
    assertThat(invalidBody.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();

    MvcResult rejectedLogin =
        mockMvc
            .perform(
                post(LOGIN)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"hospitalSlug":"%s","email":"%s","password":"not-the-password"}
                        """
                            .formatted(slug, email)))
            .andExpect(status().isUnauthorized())
            .andReturn();
    assertThat(rejectedLogin.getResponse().getHeader(HttpHeaders.SET_COOKIE))
        .as("a failed login starts no session")
        .isNull();
  }

  // -------------------------------------------------------------------------
  // custom-header guard (decision D5)
  // -------------------------------------------------------------------------

  @Test
  void refreshWithoutTheCustomHeaderIsRejectedBeforeItReachesTheService() throws Exception {
    Cookie cookie = refreshCookieOf(loginSetCookie());

    mockMvc
        .perform(post(REFRESH).cookie(cookie))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"))
        .andExpect(jsonPath("$.error.message").value("Access denied."))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  void logoutWithoutTheCustomHeaderIsRejectedBeforeItReachesTheService() throws Exception {
    Cookie cookie = refreshCookieOf(loginSetCookie());

    mockMvc
        .perform(post(LOGOUT).cookie(cookie))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
  }

  // -------------------------------------------------------------------------
  // rotation
  // -------------------------------------------------------------------------

  @Test
  void refreshWithTheCustomHeaderRotatesTheCookieAndTheOldOneDies() throws Exception {
    String firstSetCookie = loginSetCookie();
    Cookie presented = refreshCookieOf(firstSetCookie);

    MvcResult rotation = refresh(presented);
    String secondSetCookie = rotation.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(secondSetCookie).as("refresh hands on a successor cookie").isNotNull();
    assertThat(valueOf(secondSetCookie))
        .as("a successor value, not the same one")
        .hasSize(43)
        .isNotEqualTo(valueOf(firstSetCookie));

    String body = rotation.getResponse().getContentAsString();
    assertThat(body)
        .as("the raw token appears in no body - HttpOnly would be pointless otherwise")
        .doesNotContain(valueOf(firstSetCookie))
        .doesNotContain(valueOf(secondSetCookie))
        .doesNotContain(RefreshCookieBuilder.COOKIE_NAME);

    mockMvc
        .perform(
            post(REFRESH)
                .cookie(refreshCookieOf(secondSetCookie))
                .header(CSRF_HEADER, CSRF_VALUE)
                .header(HttpHeaders.USER_AGENT, AGENT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").exists())
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.data.expiresIn").value(720))
        .andExpect(jsonPath("$.data.profile.email").value(email))
        .andExpect(jsonPath("$.data.profile.roles").isArray())
        .andExpect(jsonPath("$.data.profile.roles").isEmpty())
        .andExpect(jsonPath("$.data.profile.tenantName").value(slug));

    mockMvc
        .perform(
            post(REFRESH)
                .cookie(presented)
                .header(CSRF_HEADER, CSRF_VALUE)
                .header(HttpHeaders.USER_AGENT, AGENT))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  // -------------------------------------------------------------------------
  // logout
  // -------------------------------------------------------------------------

  @Test
  void logoutRevokesTheFamilyInDatabaseAndClearsTheCookie() throws Exception {
    String setCookie = loginSetCookie();
    Cookie cookie = refreshCookieOf(setCookie);

    MvcResult result =
        mockMvc
            .perform(
                post(LOGOUT)
                    .cookie(cookie)
                    .header(CSRF_HEADER, CSRF_VALUE)
                    .contentType(APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.message").value(AuthController.LOGGED_OUT))
            .andReturn();

    String cleared = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(attributesOf(cleared)).containsEntry("Max-Age", "0").containsKey("HttpOnly");
    assertThat(valueOf(cleared)).as("the browser drops the value it was holding").isEmpty();

    String reason =
        jdbcTemplate.queryForObject(
            "SELECT revoked_reason FROM refresh_tokens WHERE token_hash = ?",
            String.class,
            TokenValues.sha256Hex(valueOf(setCookie)));
    assertThat(reason).as("server-side, not just a dropped cookie").isEqualTo("LOGOUT");

    mockMvc
        .perform(
            post(REFRESH)
                .cookie(cookie)
                .header(CSRF_HEADER, CSRF_VALUE)
                .header(HttpHeaders.USER_AGENT, AGENT))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  @Test
  void logoutIsIdempotentSoAClickedTwiceButtonStillSucceeds() throws Exception {
    Cookie cookie = refreshCookieOf(loginSetCookie());

    mockMvc
        .perform(post(LOGOUT).cookie(cookie).header(CSRF_HEADER, CSRF_VALUE))
        .andExpect(status().isOk());
    mockMvc
        .perform(post(LOGOUT).cookie(cookie).header(CSRF_HEADER, CSRF_VALUE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.message").value(AuthController.LOGGED_OUT));
  }

  @Test
  void bothEndpointsRejectACallerWithNoCookieAtAll() throws Exception {
    mockMvc
        .perform(post(REFRESH).header(CSRF_HEADER, CSRF_VALUE))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    mockMvc
        .perform(post(LOGOUT).header(CSRF_HEADER, CSRF_VALUE))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  // -------------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------------

  private String loginSetCookie() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(LOGIN)
                    .contentType(APPLICATION_JSON)
                    .header(HttpHeaders.USER_AGENT, AGENT)
                    .content(loginBody()))
            .andExpect(status().isOk())
            .andReturn();
    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie).as("login hands out the refresh cookie").isNotNull();
    return setCookie;
  }

  private MvcResult refresh(Cookie cookie) throws Exception {
    return mockMvc
        .perform(
            post(REFRESH)
                .cookie(cookie)
                .header(CSRF_HEADER, CSRF_VALUE)
                .header(HttpHeaders.USER_AGENT, AGENT))
        .andExpect(status().isOk())
        .andReturn();
  }

  private String loginBody() {
    return """
        {"hospitalSlug":"%s","email":"%s","password":"%s"}
        """
        .formatted(slug, email, PASSWORD);
  }

  private static Cookie refreshCookieOf(String setCookie) {
    return new Cookie(RefreshCookieBuilder.COOKIE_NAME, valueOf(setCookie));
  }

  /** The {@code name=value} segment of a {@code Set-Cookie} header. */
  private static String valueOf(String setCookie) {
    String first = setCookie.split(";", 2)[0];
    int equals = first.indexOf('=');
    assertThat(first.substring(0, equals)).isEqualTo(RefreshCookieBuilder.COOKIE_NAME);
    return first.substring(equals + 1);
  }

  /** Every attribute after the first segment, as a map; bare flags map to an empty string. */
  private static Map<String, String> attributesOf(String setCookie) {
    Map<String, String> attributes = new LinkedHashMap<>();
    String[] parts = setCookie.split(";");
    for (int i = 1; i < parts.length; i++) {
      String part = parts[i].trim();
      int equals = part.indexOf('=');
      if (equals < 0) {
        attributes.put(part, "");
      } else {
        attributes.put(part.substring(0, equals), part.substring(equals + 1));
      }
    }
    return attributes;
  }

  // -------------------------------------------------------------------------
  // prod profile: Secure is on
  // -------------------------------------------------------------------------

  /** Decision D6: {@code Secure} is profile driven, so it needs the profile to be asserted. */
  @Nested
  @SpringBootTest(properties = "spring.profiles.active=prod")
  @AutoConfigureMockMvc
  class ProdProfile {

    @Autowired private MockMvc prodMockMvc;
    @Autowired private AuthFixtures prodFixtures;

    @Test
    void theCookieCarriesTheSecureFlagInProd() throws Exception {
      String prodSlug = PREFIX + "prod-" + UUID.randomUUID();
      String prodEmail = "admin@" + prodSlug + ".test";
      UUID tenantId = prodFixtures.createTenant(prodSlug, TenantStatus.ACTIVE);
      prodFixtures.createUser(tenantId, prodEmail, PASSWORD, UserStatus.ACTIVE, false);

      MvcResult result =
          prodMockMvc
              .perform(
                  post(LOGIN)
                      .contentType(APPLICATION_JSON)
                      .header(HttpHeaders.USER_AGENT, AGENT)
                      .content(
                          """
                          {"hospitalSlug":"%s","email":"%s","password":"%s"}
                          """
                              .formatted(prodSlug, prodEmail, PASSWORD)))
              .andExpect(status().isOk())
              .andReturn();

      String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
      assertThat(setCookie).isNotNull();
      assertThat(setCookie)
          .as("prod must never send this value over plaintext")
          .contains("; Secure");
      assertThat(attributesOf(setCookie)).containsKey("HttpOnly");
    }
  }
}
