package com.healthcare.hms.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.healthcare.hms.tenant.TenantContext;
import com.healthcare.hms.tenant.TenantStatus;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * P5.6 &mdash; rate limiting and progressive lockout (decision <b>D7</b>, SECURITY sections 3 and
 * 12, plan section 7).
 *
 * <p>Every assertion runs through the real {@code RateLimitFilter}, the real {@code
 * RateLimiterService} and the real Redis, because the point of the suite is that the limiter is
 * actually wired into the security chain and not merely constructible.
 *
 * <p><b>Why every case is a nested context.</b> The shared suite runs against limits raised to a
 * value no test can reach (see {@code TestRateLimitProperties}), so enforcement cannot be observed
 * from it. Each case therefore inlines the one limit it is about, which gives it a context whose
 * counters no other test can spend; the address and the account are unique per case as well, so
 * nothing leaks between them.
 *
 * <p>Covered:
 *
 * <ul>
 *   <li>the account budget (5/minute) rejects the 6th attempt with 429 + {@code Retry-After};
 *   <li>the address budget (20/minute) rejects the 4th attempt from one address and not from
 *       another &mdash; the key really is per address;
 *   <li>registration and resend share one 3/hour budget per address;
 *   <li>five consecutive failures lock the account for fifteen minutes, mirrored onto {@code
 *       users}, and a success clears them &mdash; for a real account and for an address with no row
 *       behind it, identically;
 *   <li>an unreachable Redis rejects the request instead of admitting it (fail closed).
 * </ul>
 *
 * <p>Rows this suite creates are removed in {@code @AfterEach} (slug prefix {@code p56-}); Redis
 * keys are unique per case and self-expiring (TESTING section 4).
 */
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitLockoutTest {

  private static final String LOGIN = "/api/v1/auth/login";
  private static final String REGISTER = "/api/v1/auth/register-hospital";
  private static final String RESEND = "/api/v1/auth/resend-verification";

  private static final String PASSWORD = "Correct-Horse-Battery-9";
  private static final String PREFIX = "p56-";
  private static final String RATE_LIMITED = "RATE_LIMITED";
  private static final String AGENT = "rate-limit-test";

  /** One address per case, so a shared Redis never couples two tests. */
  private static final String ADDRESS_ONE = "203.0.113.7";

  private static final String ADDRESS_TWO = "203.0.113.8";

  @Autowired private AuthFixtures fixtures;

  @AfterEach
  void cleanUp() {
    TenantContext.clear();
    fixtures.deleteTenantsStartingWith(PREFIX + "%");
  }

  // -------------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------------

  private static String emailOf(String slug) {
    return "admin@" + slug + ".test";
  }

  /** An ACTIVE hospital with one usable administrator, keyed off the {@code p56-} prefix. */
  private static String hospital(AuthFixtures fixtures) {
    String slug = PREFIX + UUID.randomUUID();
    UUID tenantId = fixtures.createTenant(slug, TenantStatus.ACTIVE);
    fixtures.createUser(tenantId, emailOf(slug), PASSWORD, UserStatus.ACTIVE, false);
    return slug;
  }

  private static MockHttpServletRequestBuilder loginReq(
      String slug, String email, String password) {
    return post(LOGIN)
        .contentType(APPLICATION_JSON)
        .header(HttpHeaders.USER_AGENT, AGENT)
        .content(
            """
            {"hospitalSlug":"%s","email":"%s","password":"%s"}
            """
                .formatted(slug, email, password));
  }

  private static MockHttpServletRequestBuilder resendReq(String slug, String email) {
    return post(RESEND)
        .contentType(APPLICATION_JSON)
        .content(
            """
            {"email":"%s","hospitalSlug":"%s"}
            """
                .formatted(email, slug));
  }

  private static MockHttpServletRequestBuilder registerReq(String hospitalName, String email) {
    return post(REGISTER)
        .contentType(APPLICATION_JSON)
        .content(
            """
            {"hospitalName":"%s","email":"%s","password":"%s","firstName":"Ada","lastName":"Lovelace"}
            """
                .formatted(hospitalName, email, PASSWORD));
  }

  /**
   * Pins the peer address the limiter will key on.
   *
   * <p>MockMvc answers {@code 127.0.0.1} to every request otherwise, which would put the whole
   * suite in one bucket.
   */
  private static MockHttpServletRequestBuilder from(
      String address, MockHttpServletRequestBuilder request) {
    return request.with(
        mock -> {
          mock.setRemoteAddr(address);
          return mock;
        });
  }

  private static void expectLimited(MockMvc mvc, MockHttpServletRequestBuilder request)
      throws Exception {
    mvc.perform(request)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value(RATE_LIMITED))
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
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

  // -------------------------------------------------------------------------
  // account scope: 5/minute per account
  // -------------------------------------------------------------------------

  /** SECURITY section 12, login row: the sixth attempt inside one window is a 429. */
  @Nested
  @SpringBootTest(properties = "hms.ratelimit.login-account-limit=5")
  @AutoConfigureMockMvc
  class AccountScope {

    @Autowired private MockMvc scopedMockMvc;
    @Autowired private AuthFixtures scopedFixtures;

    @Test
    void theSixthAttemptWithinTheWindowIsRejected() throws Exception {
      String slug = hospital(scopedFixtures);
      String email = emailOf(slug);

      for (int attempt = 1; attempt <= 5; attempt++) {
        scopedMockMvc.perform(loginReq(slug, email, PASSWORD)).andExpect(status().isOk());
      }

      scopedMockMvc
          .perform(loginReq(slug, email, PASSWORD))
          .andExpect(status().isTooManyRequests())
          .andExpect(jsonPath("$.error.code").value(RATE_LIMITED))
          // the window is a minute, so the caller is told to come back inside it
          .andExpect(retryAfterBetween(1, 60));
    }
  }

  // -------------------------------------------------------------------------
  // address scope: 20/minute per IP
  // -------------------------------------------------------------------------

  /** The IP row, and the proof that the key really is the peer address. */
  @Nested
  @SpringBootTest(properties = "hms.ratelimit.login-ip-limit=3")
  @AutoConfigureMockMvc
  class IpScope {

    @Autowired private MockMvc scopedMockMvc;
    @Autowired private AuthFixtures scopedFixtures;

    @Test
    void theFourthAttemptFromOneAddressIsRejectedButAnotherAddressIsNot() throws Exception {
      String slug = hospital(scopedFixtures);
      String email = emailOf(slug);

      for (int attempt = 1; attempt <= 3; attempt++) {
        scopedMockMvc
            .perform(from(ADDRESS_ONE, loginReq(slug, email, PASSWORD)))
            .andExpect(status().isOk());
      }

      expectLimited(scopedMockMvc, from(ADDRESS_ONE, loginReq(slug, email, PASSWORD)));

      scopedMockMvc
          .perform(from(ADDRESS_TWO, loginReq(slug, email, PASSWORD)))
          .andExpect(status().isOk());
    }
  }

  // -------------------------------------------------------------------------
  // registration + resend: 3/hour per address, one shared budget
  // -------------------------------------------------------------------------

  /** The "Registration / resend verification" row, and that the two share one bucket. */
  @Nested
  @SpringBootTest(properties = "hms.ratelimit.email-account-limit=3")
  @AutoConfigureMockMvc
  class EmailScope {

    @Autowired private MockMvc scopedMockMvc;

    @Test
    void registrationAndResendShareOneBudgetOfThreePerHour() throws Exception {
      // Already slug-shaped, so the resend can name the hospital without re-deriving it.
      String slug = "p56-shared-bucket-hospital";
      String email = PREFIX + "shared@example.test";

      scopedMockMvc.perform(registerReq(slug, email)).andExpect(status().isAccepted());
      scopedMockMvc.perform(resendReq(slug, email)).andExpect(status().isAccepted());
      scopedMockMvc.perform(resendReq(slug, email)).andExpect(status().isAccepted());

      expectLimited(scopedMockMvc, resendReq(slug, email));
    }
  }

  // -------------------------------------------------------------------------
  // progressive lockout
  // -------------------------------------------------------------------------

  /** Five failures lock for fifteen minutes; the decision and its database mirror. */
  @Nested
  @SpringBootTest(properties = "hms.ratelimit.login-account-limit=1000000")
  @AutoConfigureMockMvc
  class Lockout {

    @Autowired private MockMvc scopedMockMvc;
    @Autowired private AuthFixtures scopedFixtures;
    @Autowired private JdbcTemplate scopedJdbcTemplate;

    @Test
    void fiveConsecutiveFailuresLockTheAccountForFifteenMinutes() throws Exception {
      String slug = hospital(scopedFixtures);
      String email = emailOf(slug);

      for (int attempt = 1; attempt <= 5; attempt++) {
        scopedMockMvc
            .perform(loginReq(slug, email, "wrong-password-" + attempt))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
      }

      // The correct password no longer matters: the lock is checked before the account is read.
      scopedMockMvc
          .perform(loginReq(slug, email, PASSWORD))
          .andExpect(status().isTooManyRequests())
          .andExpect(jsonPath("$.error.code").value(RATE_LIMITED))
          .andExpect(retryAfterBetween(800, 900));

      var row =
          scopedJdbcTemplate.queryForMap(
              "SELECT failed_attempts, locked_until FROM users"
                  + " WHERE email = ? AND tenant_id IN (SELECT id FROM tenants WHERE slug = ?)",
              email,
              slug);
      assertThat(row.get("failed_attempts")).isEqualTo(5);
      assertThat(row.get("locked_until")).isNotNull();
    }

    /**
     * Decision D7: the counter is keyed on the slug and address the caller supplied, never on a
     * user id, so an address with no row behind it accrues exactly the same history.
     */
    @Test
    void anAddressWithNoAccountIsTreatedExactlyLikeARealOne() throws Exception {
      String slug = hospital(scopedFixtures);
      String realEmail = emailOf(slug);
      String ghostEmail = "ghost-" + slug + "@example.test";

      for (int attempt = 1; attempt <= 5; attempt++) {
        String real = attemptBody(scopedMockMvc, loginReq(slug, realEmail, "wrong-" + attempt));
        String ghost = attemptBody(scopedMockMvc, loginReq(slug, ghostEmail, "wrong-" + attempt));
        assertThat(ghost).isEqualTo(real);
      }

      String realLocked = attemptBody(scopedMockMvc, loginReq(slug, realEmail, PASSWORD));
      String ghostLocked = attemptBody(scopedMockMvc, loginReq(slug, ghostEmail, PASSWORD));
      assertThat(ghostLocked).isEqualTo(realLocked);
      assertThat(realLocked)
          .as("the sixth attempt is the uniform lockout, not a 401")
          .startsWith("429|" + RATE_LIMITED + "|");
    }

    /** Status, code and message &mdash; {@code traceId} is per request and cannot be compared. */
    private String attemptBody(MockMvc mvc, MockHttpServletRequestBuilder request)
        throws Exception {
      MvcResult result = mvc.perform(request).andReturn();
      String body = result.getResponse().getContentAsString();
      int status = result.getResponse().getStatus();
      return status + "|" + extract(body, "\"code\":\"") + "|" + extract(body, "\"message\":\"");
    }

    private String extract(String body, String marker) {
      int start = body.indexOf(marker);
      if (start < 0) {
        return "";
      }
      int from = start + marker.length();
      int to = body.indexOf('"', from);
      return to < 0 ? body.substring(from) : body.substring(from, to);
    }
  }

  /**
   * A success must clear the count, which cannot be observed at the documented fifteen minutes. The
   * thresholds are properties, so this context shortens them instead of sleeping for a quarter of
   * an hour; the documented defaults are asserted in {@code RateLimitPolicyTest}.
   */
  @Nested
  @SpringBootTest(
      properties = {
        "hms.ratelimit.login-account-limit=1000000",
        "hms.ratelimit.lockout-base=PT2S",
        "hms.ratelimit.lockout-max=PT4S"
      })
  @AutoConfigureMockMvc
  class ShortLock {

    @Autowired private MockMvc scopedMockMvc;
    @Autowired private AuthFixtures scopedFixtures;
    @Autowired private JdbcTemplate scopedJdbcTemplate;

    @Test
    void aSuccessfulSignInClearsTheFailureCount() throws Exception {
      String slug = hospital(scopedFixtures);
      String email = emailOf(slug);

      for (int attempt = 1; attempt <= 5; attempt++) {
        scopedMockMvc
            .perform(loginReq(slug, email, "wrong-password-" + attempt))
            .andExpect(status().isUnauthorized());
      }
      scopedMockMvc
          .perform(loginReq(slug, email, PASSWORD))
          .andExpect(status().isTooManyRequests());

      Thread.sleep(3_000);

      scopedMockMvc.perform(loginReq(slug, email, PASSWORD)).andExpect(status().isOk());

      Integer failedAttempts =
          scopedJdbcTemplate.queryForObject(
              "SELECT failed_attempts FROM users"
                  + " WHERE email = ? AND tenant_id IN (SELECT id FROM tenants WHERE slug = ?)",
              Integer.class,
              email,
              slug);
      assertThat(failedAttempts).isZero();
    }
  }

  // -------------------------------------------------------------------------
  // fail closed (decision D7 / SECURITY section 12)
  // -------------------------------------------------------------------------

  /**
   * "Auth endpoints fail closed if the limiter is unavailable."
   *
   * <p>The context points the client at a port nothing listens on, which is the same failure
   * production sees when Redis is down. The answer must be a rejection, never an admission &mdash;
   * an outage that let logins through would be an outage that let password guessing through.
   */
  @Nested
  @SpringBootTest(properties = "spring.data.redis.port=1")
  @AutoConfigureMockMvc
  class FailClosed {

    @Autowired private MockMvc scopedMockMvc;

    @Test
    void anUnreachableLimiterRejectsTheRequestInsteadOfAdmittingIt() throws Exception {
      expectLimited(scopedMockMvc, loginReq("p56-no-such-hospital", "nobody@example.test", "x"));
    }
  }
}
