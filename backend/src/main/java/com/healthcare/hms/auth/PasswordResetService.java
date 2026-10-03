package com.healthcare.hms.auth;

import com.healthcare.hms.auth.repository.UserRepository;
import com.healthcare.hms.auth.repository.VerificationTokenRepository;
import com.healthcare.hms.common.ratelimit.RateLimitKeys;
import com.healthcare.hms.common.ratelimit.RateLimitProperties;
import com.healthcare.hms.common.ratelimit.RateLimiterService;
import com.healthcare.hms.tenant.Tenant;
import com.healthcare.hms.tenant.TenantBootstrapLookup;
import com.healthcare.hms.tenant.TenantContext;
import com.healthcare.hms.tenant.TenantKeys;
import com.healthcare.hms.tenant.TenantRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Password reset (FR-2.3, plan P5.7, decisions D1, D2, D8, D9).
 *
 * <p><b>Requesting a link never reveals whether an account exists.</b> {@link #forgotPassword}
 * charges its budget first, looks the address up second and mails only when there is something to
 * mail &mdash; the caller gets the same 202 either way, because the only observable difference is
 * the email, which is exactly how D9 reads SECURITY section 3. The budget is charged on the slug
 * the caller supplied before the address is read, so a real address and an invented one exhaust it
 * at the same attempt; when the slug itself is unknown the anonymous address budget is charged
 * instead with the same limit and window, so the two paths stay indistinguishable and neither one
 * is unbounded.
 *
 * <p><b>Consuming a link.</b> The token is looked up through the D1 secret-leg directory, which
 * turns the digest into the tenant to bind; only then does the tenant-filtered repository see the
 * row. Unknown, expired and replayed tokens are one failure ({@link InvalidTokenException}), so the
 * endpoint cannot be used to discover which tokens exist.
 *
 * <p><b>The password is validated before the token is read.</b> The order matters: a weak password
 * rejected <i>after</i> a successful token lookup would answer 422 only for real tokens and 400 for
 * invented ones, and the status code alone would be an oracle. Validating first means a weak
 * password is a 422 no matter what was sent with it, and only a well-formed request ever reaches
 * the token.
 *
 * <p><b>Every session dies with the old password</b> (SECURITY section 15): changing the one secret
 * that guards the account must not leave the sessions started under the old one alive, so all of
 * the user's refresh families are revoked with {@link RefreshTokenRevokedReason#PASSWORD_RESET} in
 * the same transaction that writes the new hash. The row is left in place as evidence.
 */
@Service
public class PasswordResetService {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

  private final TenantBootstrapLookup tenantBootstrapLookup;
  private final TokenBootstrapLookup tokenBootstrapLookup;
  private final VerificationTokenRepository verificationTokenRepository;
  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final PasswordPolicy passwordPolicy;
  private final PasswordEncoder passwordEncoder;
  private final PasswordResetMailer passwordResetMailer;
  private final RefreshTokenService refreshTokenService;
  private final LockoutService lockoutService;
  private final TransactionTemplate transactionTemplate;
  private final AuthProperties authProperties;
  private final RateLimiterService rateLimiter;
  private final RateLimitProperties rateLimitProperties;

  public PasswordResetService(
      TenantBootstrapLookup tenantBootstrapLookup,
      TokenBootstrapLookup tokenBootstrapLookup,
      VerificationTokenRepository verificationTokenRepository,
      TenantRepository tenantRepository,
      UserRepository userRepository,
      PasswordPolicy passwordPolicy,
      PasswordEncoder passwordEncoder,
      PasswordResetMailer passwordResetMailer,
      RefreshTokenService refreshTokenService,
      LockoutService lockoutService,
      TransactionTemplate transactionTemplate,
      AuthProperties authProperties,
      RateLimiterService rateLimiter,
      RateLimitProperties rateLimitProperties) {
    this.tenantBootstrapLookup = tenantBootstrapLookup;
    this.tokenBootstrapLookup = tokenBootstrapLookup;
    this.verificationTokenRepository = verificationTokenRepository;
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.passwordPolicy = passwordPolicy;
    this.passwordEncoder = passwordEncoder;
    this.passwordResetMailer = passwordResetMailer;
    this.refreshTokenService = refreshTokenService;
    this.lockoutService = lockoutService;
    this.transactionTemplate = transactionTemplate;
    this.authProperties = authProperties;
    this.rateLimiter = rateLimiter;
    this.rateLimitProperties = rateLimitProperties;
  }

  /**
   * Issues a reset link when there is an account to issue it for, and answers the same 202 whether
   * there is or not (decision D9).
   *
   * @throws com.healthcare.hms.common.ratelimit.RateLimitedException 429 after three requests for
   *     one account in an hour (decision D7 / SECURITY section 12)
   */
  public void forgotPassword(String email, String hospitalSlug) {
    UUID tenantId = tenantBootstrapLookup.findTenantIdBySlug(hospitalSlug).orElse(null);
    chargeBudget(email, tenantId);
    if (tenantId == null) {
      // No tenant to bind, so nothing can be read and nothing is sent. The budget above has
      // already been spent, so this branch looks the same as one that found nothing to send to.
      return;
    }

    String rawToken = TokenValues.newToken();
    String tokenHash = TokenValues.sha256Hex(rawToken);

    String hospitalName =
        TenantContext.call(
            tenantId,
            () ->
                transactionTemplate.execute(
                    status -> {
                      Optional<User> user = userRepository.findByEmail(email);
                      if (user.isEmpty()) {
                        return null;
                      }
                      verificationTokenRepository.save(resetToken(user.get().getId(), tokenHash));
                      return tenantRepository.findById(tenantId).map(Tenant::getName).orElse(null);
                    }));

    if (hospitalName != null) {
      passwordResetMailer.send(email, hospitalName, rawToken);
      log.info("Password reset link issued: tenantId={}", tenantId);
    }
  }

  /**
   * Consumes a reset link and writes the new password.
   *
   * @param rawToken the value from the emailed link
   * @param newPassword replacement password, already proven to satisfy the policy
   * @throws com.healthcare.hms.common.exception.FieldValidationException 422 when the password
   *     breaks the policy
   * @throws InvalidTokenException 400 for unknown, expired or replayed tokens alike
   */
  public void resetPassword(String rawToken, String newPassword) {
    // Before the token is read, and deliberately so - see the class javadoc.
    passwordPolicy.validate(newPassword);

    String tokenHash = TokenValues.sha256Hex(rawToken);
    UUID tenantId =
        tokenBootstrapLookup
            .findTenantIdByTokenHash(tokenHash)
            .orElseThrow(InvalidTokenException::new);

    TenantContext.run(
        tenantId,
        () ->
            transactionTemplate.executeWithoutResult(
                status -> {
                  VerificationToken token =
                      verificationTokenRepository
                          .findByTokenHash(tokenHash)
                          .filter(PasswordResetService::isResettable)
                          .orElseThrow(InvalidTokenException::new);

                  token.setUsedAt(Instant.now());
                  verificationTokenRepository.save(token);

                  User user =
                      userRepository
                          .findById(token.getUserId())
                          .orElseThrow(InvalidTokenException::new);
                  user.setPasswordHash(passwordEncoder.encode(newPassword));
                  // A reset is also the answer to "I am locked out": the count that produced the
                  // lock goes with the old password, in the database and in Redis alike. Both,
                  // because Redis is what decides and the row is only the mirror an operator reads.
                  user.setFailedAttempts(0);
                  user.setLockedUntil(null);
                  userRepository.save(user);
                  lockoutService.recordSuccess(tenantId, user.getEmail(), user);

                  int revoked =
                      refreshTokenService.revokeAllFamiliesForUser(
                          user.getId(), RefreshTokenRevokedReason.PASSWORD_RESET);
                  log.info(
                      "Password reset completed: tenantId={} userId={} sessionsRevoked={}",
                      tenantId,
                      user.getId(),
                      revoked);
                }));
  }

  /**
   * Charges the 3/hour-per-account budget of SECURITY section 12 (decision D7).
   *
   * <p>Two keys, one behaviour: the tenant-scoped one when the slug is known, the anonymous address
   * one when it is not. Both allow the same number in the same window, so exhausting either looks
   * identical from outside and neither path is left without a budget.
   */
  private void chargeBudget(String email, UUID tenantId) {
    int limit = rateLimitProperties.getResetAccountLimit();
    Duration window = rateLimitProperties.getResetAccountWindow();
    if (tenantId == null) {
      rateLimiter.consume(RateLimitKeys.email(email), limit, window);
    } else {
      rateLimiter.consume(TenantKeys.redis(tenantId, "rl", "reset", email), limit, window);
    }
  }

  private VerificationToken resetToken(UUID userId, String tokenHash) {
    VerificationToken token = new VerificationToken();
    token.setUserId(userId);
    token.setType(VerificationTokenType.PASSWORD_RESET);
    token.setTokenHash(tokenHash);
    token.setExpiresAt(Instant.now().plus(authProperties.getPasswordResetTokenTtl()));
    return token;
  }

  private static boolean isResettable(VerificationToken token) {
    return token.getType() == VerificationTokenType.PASSWORD_RESET
        && token.getUsedAt() == null
        && token.getExpiresAt().isAfter(Instant.now());
  }
}
