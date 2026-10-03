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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * P5.2 — registration and email verification (FR-1.1 / FR-1.2 / FR-1.3, plan section 6).
 *
 * <p>Everything runs through the real {@code SecurityFilterChain}, the real repositories and the
 * real D1 bootstrap, on the shared {@code hms_test} database; the only substitution is the email
 * transport, which records messages instead of sending them so the link can be picked up and
 * replayed exactly as a user would.
 *
 * <p>Rows this suite creates are removed again in {@code @AfterEach} (slug prefix {@code p52-}),
 * because the database is JVM-scoped and shared with every other suite (TESTING section 4).
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationVerificationTest {

  private static final String REGISTER = "/api/v1/auth/register-hospital";
  private static final String VERIFY = "/api/v1/auth/verify-email";
  private static final String RESEND = "/api/v1/auth/resend-verification";

  /** Any hospital this suite registers is named so its slug is removable afterwards. */
  private static final String NAME_PREFIX = "P52 ";

  private static final String STRONG_PASSWORD = "Correct-Horse-Battery-9";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private AuthProperties authProperties;
  @Autowired private RecordingEmailSender emailSender;

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
    jdbcTemplate.update(
        "DELETE FROM verification_tokens"
            + " WHERE tenant_id IN (SELECT id FROM tenants WHERE slug LIKE 'p52-%')");
    jdbcTemplate.update(
        "DELETE FROM users WHERE tenant_id IN (SELECT id FROM tenants WHERE slug LIKE 'p52-%')");
    jdbcTemplate.update("DELETE FROM tenants WHERE slug LIKE 'p52-%'");
  }

  // -------------------------------------------------------------------------
  // 1. registration
  // -------------------------------------------------------------------------

  @Test
  void registrationLeavesAPendingHospitalAndStoresOnlyTheTokenHash() throws Exception {
    String email = "p52-registration@example.test";
    String hospitalName = NAME_PREFIX + "Registration Hospital";

    ResultActions result = register(hospitalName, email, STRONG_PASSWORD);

    result
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.message").value(AuthController.REGISTRATION_ACCEPTED));

    Map<String, Object> tenant = tenantRow(hospitalName);
    assertThat(tenant.get("status")).isEqualTo("PENDING");
    assertThat(tenant.get("verified_at")).isNull();

    Map<String, Object> user = userRow(email);
    assertThat(user.get("status")).isEqualTo("PENDING");
    String storedHash = (String) user.get("password_hash");
    assertThat(storedHash)
        .as("the password is hashed with the configured encoder, never stored verbatim")
        .startsWith("{bcrypt}")
        .isNotEqualTo(STRONG_PASSWORD);

    // Only the hash is stored: the raw token appears nowhere in the row (V4 header).
    String rawToken = tokenFromLastEmail();
    Map<String, Object> token = tokenRow(tenant.get("id"));
    assertThat(token.get("token_hash")).isEqualTo(TokenValues.sha256Hex(rawToken));
    assertThat(rawToken).hasSizeGreaterThanOrEqualTo(40);
    for (Object value : token.values()) {
      assertThat(String.valueOf(value)).doesNotContain(rawToken);
    }

    assertThat(emailSender.messages()).hasSize(1);
    EmailMessage message = emailSender.messages().get(0);
    assertThat(message.to()).isEqualTo(email);
    assertThat(message.body()).contains(rawToken).contains(hospitalName);
  }

  // -------------------------------------------------------------------------
  // 3. verification
  // -------------------------------------------------------------------------

  @Test
  void verificationActivatesTheHospitalAndItsAdministrator() throws Exception {
    String email = "p52-verify@example.test";
    register(NAME_PREFIX + "Verify Hospital", email, STRONG_PASSWORD);
    String rawToken = tokenFromLastEmail();

    mockMvc
        .perform(post(VERIFY).contentType(APPLICATION_JSON).content(tokenBody(rawToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.message").value(AuthController.EMAIL_VERIFIED));

    Map<String, Object> tenant = tenantRow(NAME_PREFIX + "Verify Hospital");
    assertThat(tenant.get("status")).isEqualTo("ACTIVE");
    assertThat(tenant.get("verified_at")).isNotNull();
    assertThat(userRow(email).get("status")).isEqualTo("ACTIVE");
  }

  @Test
  void verificationTokenIsSingleUseAndExpiredTokensFailIdentically() throws Exception {
    // (a) a consumed link may not be presented again
    register(NAME_PREFIX + "Replay Hospital", "p52-replay@example.test", STRONG_PASSWORD);
    String replayToken = tokenFromLastEmail();
    mockMvc
        .perform(post(VERIFY).contentType(APPLICATION_JSON).content(tokenBody(replayToken)))
        .andExpect(status().isOk());
    String replayBody = postAndBody(VERIFY, tokenBody(replayToken), 400);

    // (b) an expired link fails the same way — TTL shortened for this registration only
    Duration configured = authProperties.getVerificationTokenTtl();
    String expiredToken;
    try {
      authProperties.setVerificationTokenTtl(Duration.ZERO);
      register(NAME_PREFIX + "Expired Hospital", "p52-expired@example.test", STRONG_PASSWORD);
      expiredToken = tokenFromLastEmail();
    } finally {
      authProperties.setVerificationTokenTtl(configured);
    }
    String expiredBody = postAndBody(VERIFY, tokenBody(expiredToken), 400);

    assertThat(normalize(expiredBody))
        .as("replay and expiry are one uniform failure (plan P5.2 assertion 4)")
        .isEqualTo(normalize(replayBody));
    assertThat(replayBody).contains("\"code\":\"VALIDATION_FAILED\"");
  }

  @Test
  void unknownTokensFailWithTheSameUniformBody() throws Exception {
    register(NAME_PREFIX + "Unknown Token Hospital", "p52-unknown@example.test", STRONG_PASSWORD);
    String consumed = tokenFromLastEmail();
    mockMvc
        .perform(post(VERIFY).contentType(APPLICATION_JSON).content(tokenBody(consumed)))
        .andExpect(status().isOk());

    String usedBody = postAndBody(VERIFY, tokenBody(consumed), 400);
    String unknownBody = postAndBody(VERIFY, tokenBody(TokenValues.newToken()), 400);

    assertThat(normalize(unknownBody)).isEqualTo(normalize(usedBody));
  }

  // -------------------------------------------------------------------------
  // 5. resend
  // -------------------------------------------------------------------------

  @Test
  void resendIsIdenticalForKnownAndUnknownAddresses() throws Exception {
    String email = "p52-resend@example.test";
    String hospitalName = NAME_PREFIX + "Resend Hospital";
    register(hospitalName, email, STRONG_PASSWORD);
    assertThat(emailSender.messages()).hasSize(1);

    String known =
        postAndBody(
            RESEND,
            """
            {"email":"%s","hospitalSlug":"%s"}
            """
                .formatted(email, slugOf(hospitalName)),
            202);
    String unknown =
        postAndBody(
            RESEND,
            """
            {"email":"nobody@example.test","hospitalSlug":"%s"}
            """
                .formatted(slugOf(hospitalName)),
            202);

    assertThat(normalize(unknown))
        .as("D9: an unknown address is indistinguishable from a known one")
        .isEqualTo(normalize(known));
    assertThat(emailSender.messages())
        .as("no email is issued for an address that does not exist")
        .hasSize(2);

    assertThat(tokenFromLastEmail()).isNotEqualTo(tokenFromFirstEmail());
  }

  // -------------------------------------------------------------------------
  // 6. conflict + validation
  // -------------------------------------------------------------------------

  @Test
  void duplicateSlugIsA409NamingHospitalName() throws Exception {
    register(NAME_PREFIX + "Duplicate Hospital", "p52-duplicate-a@example.test", STRONG_PASSWORD);

    mockMvc
        .perform(
            post(REGISTER)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"hospitalName":"%sDuplicate Hospital","email":"p52-duplicate-b@example.test",
                     "password":"%s"}
                    """
                        .formatted(NAME_PREFIX, STRONG_PASSWORD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"))
        .andExpect(jsonPath("$.error.fields[0].field").value("hospitalName"));

    assertThat(emailSender.messages()).as("a rejected registration sends nothing").hasSize(1);
  }

  @Test
  void invalidAndUnknownRequestBodiesAre422() throws Exception {
    mockMvc
        .perform(post(REGISTER).contentType(APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.fields[?(@.field == 'hospitalName')]").exists())
        .andExpect(jsonPath("$.error.fields[?(@.field == 'email')]").exists())
        .andExpect(jsonPath("$.error.fields[?(@.field == 'password')]").exists());

    // ISO-7 stays structurally closed: a tenantId in the body cannot reach a binding.
    mockMvc
        .perform(
            post(REGISTER)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"hospitalName":"%sInjection Hospital","email":"p52-inject@example.test",
                     "password":"%s","tenantId":"00000000-0000-0000-0000-0000000000ff"}
                    """
                        .formatted(NAME_PREFIX, STRONG_PASSWORD)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.fields[0].field").value("tenantId"))
        .andExpect(jsonPath("$.error.fields[0].message").value("Unknown property."));

    mockMvc
        .perform(
            post(REGISTER)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"hospitalName":"%sWeak Password Hospital","email":"p52-weak@example.test",
                     "password":"passwordpassword"}
                    """
                        .formatted(NAME_PREFIX)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.fields[0].field").value("password"));
  }

  // -------------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------------

  private ResultActions register(String hospitalName, String email, String password)
      throws Exception {
    String body =
        """
        {"hospitalName":"%s","email":"%s","password":"%s","firstName":"Ada","lastName":"Lovelace"}
        """
            .formatted(hospitalName, email, password);
    return mockMvc
        .perform(post(REGISTER).contentType(APPLICATION_JSON).content(body))
        .andExpect(status().isAccepted());
  }

  private String postAndBody(String path, String body, int expectedStatus) throws Exception {
    return mockMvc
        .perform(post(path).contentType(APPLICATION_JSON).content(body))
        .andExpect(status().is(expectedStatus))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static String tokenBody(String token) {
    return "{\"token\":\"" + token + "\"}";
  }

  /** Drops the two envelope fields that legitimately differ between otherwise equal responses. */
  private String normalize(String json) throws Exception {
    JsonNode node = objectMapper.readTree(json);
    ((ObjectNode) node).remove("timestamp");
    ((ObjectNode) node).remove("traceId");
    return objectMapper.writeValueAsString(node);
  }

  /** Reads the slug back rather than duplicating {@link SlugGenerator}'s rules here. */
  private String slugOf(String hospitalName) {
    return jdbcTemplate.queryForObject(
        "SELECT slug FROM tenants WHERE name = ?", String.class, hospitalName);
  }

  private Map<String, Object> tenantRow(String hospitalName) {
    return jdbcTemplate.queryForMap("SELECT * FROM tenants WHERE name = ?", hospitalName);
  }

  private Map<String, Object> userRow(String email) {
    return jdbcTemplate.queryForMap("SELECT * FROM users WHERE email = ? LIMIT 1", email);
  }

  private Map<String, Object> tokenRow(Object tenantId) {
    return jdbcTemplate.queryForMap(
        "SELECT * FROM verification_tokens WHERE tenant_id = ?", tenantId);
  }

  private String tokenFromFirstEmail() {
    return tokenFrom(emailSender.messages().get(0).body());
  }

  private String tokenFromLastEmail() {
    List<EmailMessage> messages = emailSender.messages();
    return tokenFrom(messages.get(messages.size() - 1).body());
  }

  private static String tokenFrom(String body) {
    int marker = body.indexOf("token=");
    assertThat(marker).as("the email carries a verification link").isGreaterThan(-1);
    int start = marker + "token=".length();
    int end = start;
    while (end < body.length() && !Character.isWhitespace(body.charAt(end))) {
      end++;
    }
    return body.substring(start, end);
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
