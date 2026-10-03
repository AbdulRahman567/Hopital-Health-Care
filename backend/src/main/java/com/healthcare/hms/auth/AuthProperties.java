package com.healthcare.hms.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Authentication settings externalised per ENGINEERING_RULES section 4 (decision D8 fixes the
 * values, this class only exposes them).
 *
 * <p>Every lifetime here is asserted in tests as a <i>range</i>, never as the literal constant, so
 * tuning a value cannot silently change what the suite proves.
 */
@ConfigurationProperties(prefix = "hms.auth")
public class AuthProperties {

  /**
   * Lifetime of an email-verification token ({@code hms.auth.verification-token-ttl}; decision D8:
   * 24 hours). Long enough for a mailbox that is read the next morning, short enough that a leaked
   * link stops working the same day.
   */
  private Duration verificationTokenTtl = Duration.ofHours(24);

  /**
   * Lifetime of an access token ({@code hms.auth.access-token-ttl}; decision D8: 12 minutes).
   *
   * <p>Inside the PRD's 10–15 minute window on purpose: long enough that a user is not re-prompted
   * mid-interaction, short enough that a stolen bearer token stops working before the working day
   * ends. The refresh cookie (P5.4/P5.5) is what actually keeps a session alive, so this value can
   * stay aggressive.
   */
  private Duration accessTokenTtl = Duration.ofMinutes(12);

  /**
   * Base URL of the web application ({@code hms.auth.frontend-base-url}). Emailed verification
   * links point <i>there</i>, never at the API: the browser opens the link with a GET, and the SPA
   * re-presents the token in a POST body (API.md section 5 hygiene — the API itself never accepts a
   * token as a query parameter).
   */
  private String frontendBaseUrl = "http://localhost:3000";

  public Duration getVerificationTokenTtl() {
    return verificationTokenTtl;
  }

  public void setVerificationTokenTtl(Duration verificationTokenTtl) {
    this.verificationTokenTtl = verificationTokenTtl;
  }

  public Duration getAccessTokenTtl() {
    return accessTokenTtl;
  }

  public void setAccessTokenTtl(Duration accessTokenTtl) {
    this.accessTokenTtl = accessTokenTtl;
  }

  public String getFrontendBaseUrl() {
    return frontendBaseUrl;
  }

  public void setFrontendBaseUrl(String frontendBaseUrl) {
    this.frontendBaseUrl = frontendBaseUrl;
  }
}
