package com.healthcare.hms.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.healthcare.hms.auth.AuthFixtures;
import com.healthcare.hms.auth.UserStatus;
import com.healthcare.hms.tenant.TenantStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SEC-3 &mdash; the address that a lockout or a rate-limit failure is about to log never reaches
 * the log stream.
 *
 * <p>Two halves: the masks themselves as a plain unit test, and one end-to-end proof that a real
 * lockout line goes through {@link Pii} instead of printing the address.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PiiMaskTest {

  @Test
  void anEmailAddressKeepsOnlyItsFirstLetterOnEachSide() {
    assertThat(Pii.maskEmail("jane.doe@example.com")).isEqualTo("j***@e***");
    assertThat(Pii.maskEmail("a@b")).isEqualTo("*@*");
    assertThat(Pii.maskEmail("ab@c")).isEqualTo("a***@*");
    assertThat(Pii.maskEmail(null)).isEqualTo("*");
    assertThat(Pii.maskEmail("  ")).isEqualTo("*");
    assertThat(Pii.maskEmail("not-an-address")).isEqualTo("n***");
  }

  @Test
  void anIpv4AddressKeepsTheNetworkPartAndLosesTheHost() {
    assertThat(Pii.maskAddress("192.0.2.7")).isEqualTo("192.0.2.*");
    assertThat(Pii.maskAddress("203.0.113.254")).isEqualTo("203.0.113.*");
    assertThat(Pii.maskAddress("10.0.0.1")).isEqualTo("10.0.0.*");
  }

  @Test
  void anythingThatIsNotAnIpv4AddressIsDroppedEntirely() {
    assertThat(Pii.maskAddress("2001:db8::1")).isEqualTo("2***");
    assertThat(Pii.maskAddress("unknown")).isEqualTo("u***");
    assertThat(Pii.maskAddress(null)).isEqualTo("*");
    assertThat(Pii.maskAddress("")).isEqualTo("*");
  }

  @Test
  void aKeyKeepsItsScopeAndTenantAndLosesOnlyTheIdentifier() {
    assertThat(Pii.maskKey("rl:ip:192.0.2.7")).isEqualTo("rl:ip:192.0.2.*");
    assertThat(Pii.maskKey("rl:ip:2001:db8::1")).isEqualTo("rl:ip:2***");
    assertThat(Pii.maskKey("rl:ip:unknown")).isEqualTo("rl:ip:u***");
    assertThat(Pii.maskKey("rl:email:jane@example.com")).isEqualTo("rl:email:j***@e***");
    assertThat(Pii.maskKey("t:6c1f:rl:login:jane@example.com"))
        .isEqualTo("t:6c1f:rl:login:j***@e***");
    assertThat(Pii.maskKey("t:6c1f:lockout:jane@example.com"))
        .isEqualTo("t:6c1f:lockout:j***@e***");
    assertThat(Pii.maskKey(null)).isEqualTo("*");
  }

  /**
   * The call site itself: five sign-in failures produce exactly one lockout line, and no {@code
   * com.healthcare.hms} event in the run carries the address.
   */
  @Nested
  @SpringBootTest
  @AutoConfigureMockMvc
  class LockoutLine {

    private static final String SLUG = "p511-masked-lockout";
    private static final String EMAIL = "masked-lockout-probe@p511.example.com";
    private static final String WRONG_PASSWORD = "Correct-Horse-Battery-9";

    @Autowired private MockMvc mvc;
    @Autowired private AuthFixtures fixtures;

    private Logger logger;
    private JsonLoggingTest.ListAppenderHolder holder;

    @BeforeEach
    void attachAppender() {
      LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
      logger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
      holder = new JsonLoggingTest.ListAppenderHolder();
      holder.appender.start();
      logger.addAppender(holder.appender);
      fixtures.deleteTenantsStartingWith(SLUG);
      UUID tenantId = fixtures.createTenant(SLUG, TenantStatus.ACTIVE);
      fixtures.createUser(tenantId, EMAIL, WRONG_PASSWORD + "-stored", UserStatus.ACTIVE, false);
    }

    @AfterEach
    void detachAppender() {
      logger.detachAppender(holder.appender);
      org.slf4j.MDC.clear();
      fixtures.deleteTenantsStartingWith(SLUG);
    }

    @Test
    void theAddressIsMaskedAndTheEventIsStillRecognisable() throws Exception {
      for (int attempt = 0; attempt < 5; attempt++) {
        mvc.perform(loginWithWrongPassword()).andExpect(status().isUnauthorized());
      }

      List<ILoggingEvent> events =
          holder.appender.list.stream()
              .filter(event -> event.getLoggerName().startsWith("com.healthcare.hms"))
              .filter(event -> event.getFormattedMessage() != null)
              .toList();

      assertThat(events).isNotEmpty();
      assertThat(events)
          .as("no log event may carry the address itself")
          .noneMatch(event -> event.getFormattedMessage().contains(EMAIL));

      List<ILoggingEvent> lockoutLines =
          events.stream()
              .filter(event -> event.getFormattedMessage().contains("Locking account after"))
              .toList();
      assertThat(lockoutLines).hasSize(1);
      assertThat(lockoutLines.get(0).getFormattedMessage())
          .as("the operator still sees that an address was locked, just not which one")
          .contains("m***@p***");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
        loginWithWrongPassword() {
      return post("/api/v1/auth/login")
          .contentType(APPLICATION_JSON)
          .content(
              """
              {"hospitalSlug":"%s","email":"%s","password":"%s"}
              """
                  .formatted(SLUG, EMAIL, "Not-The-Stored-Password-1"));
    }
  }
}
