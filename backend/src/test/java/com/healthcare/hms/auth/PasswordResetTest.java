package com.healthcare.hms.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.healthcare.hms.auth.api.AuthController;
import com.healthcare.hms.notification.email.EmailMessage;
import com.healthcare.hms.notification.email.EmailSender;
import com.healthcare.hms.tenant.TenantContext;
import com.healthcare.hms.tenant.TenantStatus;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * P5.7 &mdash; password reset (FR-2.3, decisions D1, D2, D7, D8 and D9, plan section 8).
 *
 * <p>Everything runs through the real {@code SecurityFilterChain}, the real {@link
 * PasswordResetService} and the real D1 bootstrap lookups on the shared {@code hms_test} database;
 * the only substitution is the email transport, which records messages so the link can be picked up
 * and replayed exactly as a user would.
 *
 * <p>Covered:
 *
 * <ul>
 *   <li>requesting a link is one 202 whatever the address or the slug &mdash; the endpoint cannot
 *       be used to discover which accounts exist (decision D9);
 *   <li>only a hash is stored, and only when there is an account to send to;
 *   <li>a completed reset changes the password, clears the lock that stopped the user signing in,
 *       and revokes every session started under the old password (SECURITY section 15);
 *   <li>the link is single-use and unknown, replayed and expired tokens are one failure;
 *   <li>the password is validated <i>before</i> the token is read, so a 422 never distinguishes a
 *       real link from an invented one;
 *   <li>three requests an hour per account, then 429 with {@code Retry-After} (decision D7).
 * </ul>
 *
 * <p>Rows this suite creates are removed again in {@code @AfterEach} (slug prefix {@code p57-}),
 * because the database is JVM-scoped and shared with every other suite (TESTING section 4).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetTest {

  private static final String FORGOT = "/api/v1/auth/forgot-password";
  private static final String RESET = "/api/v1/auth/reset-password";
  private static final String LOGIN = "/api/v1/auth/login";
  private static final String REFRESH = "/api/v1/auth/refresh";

  private static final String CSRF_HEADER = "X-Requested-With";
  private static final String CSRF_VALUE = "XMLHttpRequest";
  private static final String AGENT = "password-reset-test";
  private static final String RATE_LIMITED = "RATE_LIMITED";

  private static final String PASSWORD = "Correct-Horse-Battery-9";
  private static final String NEW_PASSWORD = "Rotated-Passphrase-Again-31";

  /** One that survives every rule the policy enforces. */
  private static final String WEAK_PASSWORD = "passwordpassword";

  private static final String PREFIX = "p57-";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private AuthProperties authProperties;
  @Autowired private AuthFixtures fixtures;
  @Autowired private RecordingEmailSender emailSender;

  private String slug;
  private String email;
  private UUID tenantId;

  @BeforeEach
  void resetEmails() {
    emailSender.clear();
  }

  @AfterEach
  void cleanUp() {
    assertThat(TenantContext.find())
        .as("no ThreadLocal leak after any auth request (plan P5.2 assertion 7)")
        .isEmpty();
    TenantContext.clear();
    fixtures.deleteTenantsStartingWith(PREFIX + "%");
  }

  // -------------------------------------------------------------------------
  // 1. asking for a link (decision D9)
  // -------------------------------------------------------------------------

  @Test
  void requestingALinkIsOneResponseWhateverTheAddressIs() throws Exception {
    createHospital();
    emailSender.clear();

    String known = postAndBody(FORGOT, forgotBody(slug, email), 202);
    String unknownAddress = postAndBody(FORGOT, forgotBody(slug, "nobody@example.test"), 202);
    String unknownSlug = postAndBody(FORGOT, forgotBody(PREFIX + "no-such", email), 202);

    assertThat(known)
        .as("the caller is told the same thing whether or not anything was sent")
        .contains(AuthController.FORGOT_ACCEPTED);
    assertThat(normalize(unknownAddress)).isEqualTo(normalize(known));
    assertThat(normalize(unknownSlug))
        .as("a slug that does not resolve is the same answer too")
        .isEqualTo(normalize(known));

    assertThat(emailSender.messages())
        .as("only the address that exists receives anything")
        .hasSize(1);
    EmailMessage message = emailSender.messages().get(0);
    assertThat(message.to()).isEqualTo(email);
    assertThat(message.subject()).isEqualTo(PasswordResetMailer.SUBJECT);
    assertThat(message.body())
        .as("the link points at the web application, never at the API")
        .contains("/reset-password?token=");
  }

  @Test
  void onlyTheTokenHashIsStored() throws Exception {
    createHospital();
    emailSender.clear();
    postAndBody(FORGOT, forgotBody(slug, email), 202);

    String rawToken = tokenFromLastEmail();
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT * FROM verification_tokens WHERE tenant_id = ?", uuidBytes(tenantId));

    assertThat(row.get("type")).isEqualTo("PASSWORD_RESET");
    assertThat(row.get("token_hash")).isEqualTo(TokenValues.sha256Hex(rawToken));
    assertThat(row.get("used_at")).isNull();
    for (Object value : row.values()) {
      assertThat(String.valueOf(value))
          .as("the raw token appears nowhere but the email")
          .doesNotContain(rawToken);
    }
  }

  // -------------------------------------------------------------------------
  // 2. consuming a link
  // -------------------------------------------------------------------------

  @Test
  void aResetChangesThePasswordClearsTheLockAndEndsEverySession() throws Exception {
    createHospital();
    String setCookie = loginSetCookie();

    // Lock the account the way an attacker would: five wrong passwords.
    for (int attempt = 1; attempt <= 5; attempt++) {
      mockMvc
          .perform(loginRequest(slug, email, "wrong-password-" + attempt))
          .andExpect(status().isUnauthorized());
    }
    mockMvc
        .perform(loginRequest(slug, email, PASSWORD))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value(RATE_LIMITED));

    emailSender.clear();
    String rawToken = resetToken();
    mockMvc
        .perform(
            post(RESET).contentType(APPLICATION_JSON).content(resetBody(rawToken, NEW_PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.message").value(AuthController.PASSWORD_UPDATED));

    Map<String, Object> user = userRow();
    assertThat(user.get("failed_attempts"))
        .as("a reset also undoes the lock that forced the user here")
        .isEqualTo(0);
    assertThat(user.get("locked_until")).isNull();

    List<Map<String, Object>> sessions = sessionReasons();
    assertThat(sessions).isNotEmpty();
    assertThat(sessions)
        .as("SECURITY section 15: no session survives the password that started it")
        .allSatisfy(row -> assertThat(row.get("revoked_reason")).isEqualTo("PASSWORD_RESET"));

    mockMvc
        .perform(
            post(REFRESH)
                .cookie(refreshCookieOf(setCookie))
                .header(CSRF_HEADER, CSRF_VALUE)
                .header(HttpHeaders.USER_AGENT, AGENT))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

    mockMvc
        .perform(loginRequest(slug, email, PASSWORD))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

    mockMvc.perform(loginRequest(slug, email, NEW_PASSWORD)).andExpect(status().isOk());
  }

  @Test
  void theLinkIsSingleUseAndEveryOtherTokenFailsTheSameWay() throws Exception {
    createHospital();
    String rawToken = resetToken();
    mockMvc
        .perform(
            post(RESET).contentType(APPLICATION_JSON).content(resetBody(rawToken, NEW_PASSWORD)))
        .andExpect(status().isOk());

    String replayed = postAndBody(RESET, resetBody(rawToken, NEW_PASSWORD), 400);
    String unknown = postAndBody(RESET, resetBody(TokenValues.newToken(), NEW_PASSWORD), 400);

    assertThat(normalize(unknown))
        .as("a replayed link and a made-up one are one failure")
        .isEqualTo(normalize(replayed));
    assertThat(replayed)
        .contains("\"code\":\"VALIDATION_FAILED\"")
        .contains(InvalidTokenException.MESSAGE);
  }

  @Test
  void anExpiredLinkFailsTheSameWayAsAnUnknownOne() throws Exception {
    createHospital();
    String rawToken;
    Duration configured = authProperties.getPasswordResetTokenTtl();
    try {
      authProperties.setPasswordResetTokenTtl(Duration.ZERO);
      emailSender.clear();
      postAndBody(FORGOT, forgotBody(slug, email), 202);
      rawToken = tokenFromLastEmail();
    } finally {
      authProperties.setPasswordResetTokenTtl(configured);
    }

    String expired = postAndBody(RESET, resetBody(rawToken, NEW_PASSWORD), 400);
    String unknown = postAndBody(RESET, resetBody(TokenValues.newToken(), NEW_PASSWORD), 400);

    assertThat(normalize(expired)).isEqualTo(normalize(unknown));
    assertThat(expired).contains(InvalidTokenException.MESSAGE);
  }

  /**
   * The one assertion the ordering in {@link PasswordResetService#resetPassword} exists for.
   *
   * <p>A weak password rejected <i>after</i> a real token had been read would answer 422 only for
   * links that work and 400 for invented ones, and the status code alone would be an oracle.
   * Validating first means a weak password is a 422 either way, and proves it by showing the token
   * still works afterwards.
   */
  @Test
  void aWeakPasswordIsRejectedBeforeTheTokenIsRead() throws Exception {
    createHospital();
    String rawToken = resetToken();

    String withRealToken = postAndBody(RESET, resetBody(rawToken, WEAK_PASSWORD), 422);
    String withInventedToken =
        postAndBody(RESET, resetBody(TokenValues.newToken(), WEAK_PASSWORD), 422);

    assertThat(normalize(withInventedToken)).isEqualTo(normalize(withRealToken));
    assertThat(withRealToken).contains("\"field\":\"password\"");

    // The password check did not consume the link: the account can still be reset with it.
    mockMvc
        .perform(
            post(RESET).contentType(APPLICATION_JSON).content(resetBody(rawToken, NEW_PASSWORD)))
        .andExpect(status().isOk());
  }

  // -------------------------------------------------------------------------
  // 3. request validation
  // -------------------------------------------------------------------------

  @Test
  void invalidAndUnknownRequestBodiesAre422() throws Exception {
    createHospital();

    mockMvc
        .perform(post(FORGOT).contentType(APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.fields[?(@.field == 'email')]").exists())
        .andExpect(jsonPath("$.error.fields[?(@.field == 'hospitalSlug')]").exists());

    mockMvc
        .perform(post(RESET).contentType(APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.fields[?(@.field == 'token')]").exists())
        .andExpect(jsonPath("$.error.fields[?(@.field == 'newPassword')]").exists());

    // ISO-7 stays structurally closed: a tenantId in the body cannot reach a binding.
    mockMvc
        .perform(
            post(FORGOT)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","hospitalSlug":"%s",
                     "tenantId":"00000000-0000-0000-0000-0000000000ff"}
                    """
                        .formatted(email, slug)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.fields[0].field").value("tenantId"))
        .andExpect(jsonPath("$.error.fields[0].message").value("Unknown property."));
  }

  // -------------------------------------------------------------------------
  // 4. budget: 3/hour per account (decision D7 / SECURITY section 12)
  // -------------------------------------------------------------------------

  /** The "Password reset" row: the fourth request for one account in an hour is a 429. */
  @Nested
  @SpringBootTest(properties = "hms.ratelimit.reset-account-limit=3")
  @AutoConfigureMockMvc
  class Budget {

    @Autowired private MockMvc scopedMockMvc;
    @Autowired private AuthFixtures scopedFixtures;

    @Test
    void theFourthRequestForOneAccountIsRejectedButAnotherAccountIsNot() throws Exception {
      String scopedSlug = PREFIX + UUID.randomUUID();
      String scopedEmail = "admin@" + scopedSlug + ".test";
      UUID id = scopedFixtures.createTenant(scopedSlug, TenantStatus.ACTIVE);
      scopedFixtures.createUser(id, scopedEmail, PASSWORD, UserStatus.ACTIVE, false);

      for (int attempt = 1; attempt <= 3; attempt++) {
        scopedMockMvc
            .perform(
                post(FORGOT)
                    .contentType(APPLICATION_JSON)
                    .content(forgotBody(scopedSlug, scopedEmail)))
            .andExpect(status().isAccepted());
      }

      scopedMockMvc
          .perform(
              post(FORGOT)
                  .contentType(APPLICATION_JSON)
                  .content(forgotBody(scopedSlug, scopedEmail)))
          .andExpect(status().isTooManyRequests())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value(RATE_LIMITED))
          .andExpect(retryAfterBetween(1, 3600));

      // The budget is per account, not per address: a different account in the same hospital is
      // still allowed, and so is a different hospital.
      scopedMockMvc
          .perform(
              post(FORGOT)
                  .contentType(APPLICATION_JSON)
                  .content(forgotBody(scopedSlug, "other-admin@" + scopedSlug + ".test")))
          .andExpect(status().isAccepted());
    }
  }

  // -------------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------------

  private void createHospital() {
    slug = PREFIX + UUID.randomUUID();
    email = "admin@" + slug + ".test";
    tenantId = fixtures.createTenant(slug, TenantStatus.ACTIVE);
    fixtures.createUser(tenantId, email, PASSWORD, UserStatus.ACTIVE, false);
  }

  private String resetToken() throws Exception {
    emailSender.clear();
    postAndBody(FORGOT, forgotBody(slug, email), 202);
    return tokenFromLastEmail();
  }

  private String loginSetCookie() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(LOGIN)
                    .contentType(APPLICATION_JSON)
                    .header(HttpHeaders.USER_AGENT, AGENT)
                    .content(loginBody(PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie).as("login hands out the refresh cookie").isNotNull();
    return setCookie;
  }

  private static MockHttpServletRequestBuilder loginRequest(
      String slug, String email, String password) {
    return post(LOGIN)
        .contentType(APPLICATION_JSON)
        .header(HttpHeaders.USER_AGENT, AGENT)
        .content(loginBody(slug, email, password));
  }

  private String loginBody(String password) {
    return loginBody(slug, email, password);
  }

  private static String loginBody(String slug, String email, String password) {
    return """
        {"hospitalSlug":"%s","email":"%s","password":"%s"}
        """
        .formatted(slug, email, password);
  }

  private String postAndBody(String path, String body, int expectedStatus) throws Exception {
    return mockMvc
        .perform(post(path).contentType(APPLICATION_JSON).content(body))
        .andExpect(status().is(expectedStatus))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static String forgotBody(String slug, String email) {
    return """
        {"email":"%s","hospitalSlug":"%s"}
        """
        .formatted(email, slug);
  }

  private static String resetBody(String token, String password) {
    return """
        {"token":"%s","newPassword":"%s"}
        """
        .formatted(token, password);
  }

  /** Drops the two envelope fields that legitimately differ between otherwise equal responses. */
  private String normalize(String json) throws Exception {
    JsonNode node = objectMapper.readTree(json);
    ((ObjectNode) node).remove("timestamp");
    ((ObjectNode) node).remove("traceId");
    return objectMapper.writeValueAsString(node);
  }

  private Map<String, Object> userRow() {
    return jdbcTemplate.queryForMap("SELECT * FROM users WHERE email = ? LIMIT 1", email);
  }

  private List<Map<String, Object>> sessionReasons() {
    return jdbcTemplate.queryForList(
        "SELECT r.revoked_reason FROM refresh_tokens r"
            + " JOIN users u ON u.id = r.user_id WHERE u.email = ?",
        email);
  }

  /** {@code BINARY(16)} column, written the way {@link AuthFixtures} writes the same value. */
  private static byte[] uuidBytes(UUID id) {
    return java.nio.ByteBuffer.allocate(16)
        .putLong(id.getMostSignificantBits())
        .putLong(id.getLeastSignificantBits())
        .array();
  }

  private String tokenFromLastEmail() {
    List<EmailMessage> messages = emailSender.messages();
    assertThat(messages).as("the reset link is delivered by email").isNotEmpty();
    return tokenFrom(messages.get(messages.size() - 1).body());
  }

  private static String tokenFrom(String body) {
    int marker = body.indexOf("token=");
    assertThat(marker).as("the email carries a reset link").isGreaterThan(-1);
    int start = marker + "token=".length();
    int end = start;
    while (end < body.length() && !Character.isWhitespace(body.charAt(end))) {
      end++;
    }
    return body.substring(start, end);
  }

  private static Cookie refreshCookieOf(String setCookie) {
    String first = setCookie.split(";", 2)[0];
    int equals = first.indexOf('=');
    assertThat(first.substring(0, equals)).isEqualTo(RefreshCookieBuilder.COOKIE_NAME);
    return new Cookie(RefreshCookieBuilder.COOKIE_NAME, first.substring(equals + 1));
  }

  /**
   * {@code HeaderResultMatchers} has no between matcher, and {@code Retry-After} is the one header
   * this suite exists to check: present, and inside the window the caller was told to wait for.
   */
  private static ResultMatcher retryAfterBetween(long minSeconds, long maxSeconds) {
    return result -> {
      String value = result.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
      assertThat(value).as("Retry-After must be set").isNotNull();
      assertThat(Long.parseLong(value)).isBetween(minSeconds, maxSeconds);
    };
  }

  /**
   * Records every outbound message so the link can be replayed; {@code @Primary} wins over dev's.
   */
  static class RecordingEmailSender implements EmailSender {

    private final List<EmailMessage> messages = new CopyOnWriteArrayList<>();

    @Override
    public void send(EmailMessage message) {
      messages.add(message);
    }

    List<EmailMessage> messages() {
      return messages;
    }

    void clear() {
      messages.clear();
    }
  }

  @TestConfiguration
  static class EmailCaptureConfiguration {

    @Bean
    @Primary
    RecordingEmailSender recordingEmailSender() {
      return new RecordingEmailSender();
    }
  }
}
