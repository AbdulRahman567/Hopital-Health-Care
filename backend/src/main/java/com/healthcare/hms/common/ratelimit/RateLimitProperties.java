package com.healthcare.hms.common.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The SECURITY section 12 table, externalised per ENGINEERING_RULES section 4.
 *
 * <p>The <i>defaults</i> are the documented production limits and a unit test asserts them against
 * the table, so a change to one has to be a deliberate change to the other:
 *
 * <pre>
 * Login                    5/min per account, 20/min per IP
 * Registration / resend    3/hour per email,  10/hour per IP
 * Password reset           3/hour per account
 * </pre>
 *
 * <p>They are properties rather than constants because the suite is one JVM with one Redis and one
 * peer address: {@code src/test/resources/...} raises every limit so that the ~190 tests that were
 * written before the limiter existed keep exercising their own behaviour, while {@code
 * RateLimitLockoutTest} lowers individual limits in a nested context to prove the enforcement is
 * real. Production never reads those overrides.
 */
@ConfigurationProperties(prefix = "hms.ratelimit")
public class RateLimitProperties {

  /** Login, per IP: {@code hms.ratelimit.login-ip-limit}. */
  private int loginIpLimit = 20;

  private Duration loginIpWindow = Duration.ofMinutes(1);

  /** Login, per account: {@code hms.ratelimit.login-account-limit}. */
  private int loginAccountLimit = 5;

  private Duration loginAccountWindow = Duration.ofMinutes(1);

  /** Registration, resend and verification, per IP: {@code hms.ratelimit.email-ip-limit}. */
  private int emailIpLimit = 10;

  private Duration emailIpWindow = Duration.ofHours(1);

  /** Registration and resend, per address: {@code hms.ratelimit.email-account-limit}. */
  private int emailAccountLimit = 3;

  private Duration emailAccountWindow = Duration.ofHours(1);

  /** Password reset, per account: {@code hms.ratelimit.reset-account-limit} (wired in P5.7). */
  private int resetAccountLimit = 3;

  private Duration resetAccountWindow = Duration.ofHours(1);

  /**
   * Consecutive failures before the first lockout: {@code hms.ratelimit.lockout-threshold}.
   *
   * <p>The lockout is part of the same decision D7 as the counters above, so it lives here too;
   * SECURITY section 3 fixes "lockout after repeated failures with progressive delay".
   */
  private long lockoutThreshold = 5;

  /** Length of the first lock: {@code hms.ratelimit.lockout-base}. */
  private Duration lockoutBase = Duration.ofMinutes(15);

  /** No lock longer than this, however many failures: {@code hms.ratelimit.lockout-max}. */
  private Duration lockoutMax = Duration.ofHours(1);

  public int getLoginIpLimit() {
    return loginIpLimit;
  }

  public void setLoginIpLimit(int loginIpLimit) {
    this.loginIpLimit = loginIpLimit;
  }

  public Duration getLoginIpWindow() {
    return loginIpWindow;
  }

  public void setLoginIpWindow(Duration loginIpWindow) {
    this.loginIpWindow = loginIpWindow;
  }

  public int getLoginAccountLimit() {
    return loginAccountLimit;
  }

  public void setLoginAccountLimit(int loginAccountLimit) {
    this.loginAccountLimit = loginAccountLimit;
  }

  public Duration getLoginAccountWindow() {
    return loginAccountWindow;
  }

  public void setLoginAccountWindow(Duration loginAccountWindow) {
    this.loginAccountWindow = loginAccountWindow;
  }

  public int getEmailIpLimit() {
    return emailIpLimit;
  }

  public void setEmailIpLimit(int emailIpLimit) {
    this.emailIpLimit = emailIpLimit;
  }

  public Duration getEmailIpWindow() {
    return emailIpWindow;
  }

  public void setEmailIpWindow(Duration emailIpWindow) {
    this.emailIpWindow = emailIpWindow;
  }

  public int getEmailAccountLimit() {
    return emailAccountLimit;
  }

  public void setEmailAccountLimit(int emailAccountLimit) {
    this.emailAccountLimit = emailAccountLimit;
  }

  public Duration getEmailAccountWindow() {
    return emailAccountWindow;
  }

  public void setEmailAccountWindow(Duration emailAccountWindow) {
    this.emailAccountWindow = emailAccountWindow;
  }

  public int getResetAccountLimit() {
    return resetAccountLimit;
  }

  public void setResetAccountLimit(int resetAccountLimit) {
    this.resetAccountLimit = resetAccountLimit;
  }

  public Duration getResetAccountWindow() {
    return resetAccountWindow;
  }

  public void setResetAccountWindow(Duration resetAccountWindow) {
    this.resetAccountWindow = resetAccountWindow;
  }

  public long getLockoutThreshold() {
    return lockoutThreshold;
  }

  public void setLockoutThreshold(long lockoutThreshold) {
    this.lockoutThreshold = lockoutThreshold;
  }

  public Duration getLockoutBase() {
    return lockoutBase;
  }

  public void setLockoutBase(Duration lockoutBase) {
    this.lockoutBase = lockoutBase;
  }

  public Duration getLockoutMax() {
    return lockoutMax;
  }

  public void setLockoutMax(Duration lockoutMax) {
    this.lockoutMax = lockoutMax;
  }
}
