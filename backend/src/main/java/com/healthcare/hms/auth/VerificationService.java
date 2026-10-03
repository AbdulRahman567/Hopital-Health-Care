package com.healthcare.hms.auth;

import com.healthcare.hms.auth.repository.UserRepository;
import com.healthcare.hms.auth.repository.VerificationTokenRepository;
import com.healthcare.hms.common.ratelimit.RateLimitKeys;
import com.healthcare.hms.common.ratelimit.RateLimitProperties;
import com.healthcare.hms.common.ratelimit.RateLimiterService;
import com.healthcare.hms.tenant.Tenant;
import com.healthcare.hms.tenant.TenantBootstrapLookup;
import com.healthcare.hms.tenant.TenantContext;
import com.healthcare.hms.tenant.TenantRepository;
import com.healthcare.hms.tenant.TenantStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Consuming and re-issuing emailed verification links (FR-1.2 / FR-1.3, plan P5.2).
 *
 * <p><b>Verification</b> follows the D1 secret leg exactly: hash the presented token, ask the
 * directory which tenant owns it, bind that tenant, and only then read the row through the normal
 * tenant-filtered repository. Unknown, expired and already-used tokens are one failure — see {@link
 * InvalidTokenException} — so the endpoint cannot be used to probe which tokens exist.
 *
 * <p>Activation is automatic (OQ-1 / TQ-4): verifying flips the tenant and its administrator to
 * {@code ACTIVE} in the same transaction. No manual database edit is ever part of the flow.
 *
 * <p><b>Resend</b> is the anti-enumeration half of FR-1.3: known and unknown addresses produce the
 * same 202, and the email — the only observable difference — is sent solely when there is an
 * unverified account to send it to. The account lookup happens before anything is issued, so a
 * request for a nonexistent address does no work and reveals nothing.
 */
@Service
public class VerificationService {

  private final TokenBootstrapLookup tokenBootstrapLookup;
  private final TenantBootstrapLookup tenantBootstrapLookup;
  private final VerificationTokenRepository verificationTokenRepository;
  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final VerificationMailer verificationMailer;
  private final TransactionTemplate transactionTemplate;
  private final AuthProperties properties;
  private final RateLimiterService rateLimiter;
  private final RateLimitProperties rateLimitProperties;

  public VerificationService(
      TokenBootstrapLookup tokenBootstrapLookup,
      TenantBootstrapLookup tenantBootstrapLookup,
      VerificationTokenRepository verificationTokenRepository,
      TenantRepository tenantRepository,
      UserRepository userRepository,
      VerificationMailer verificationMailer,
      TransactionTemplate transactionTemplate,
      AuthProperties properties,
      RateLimiterService rateLimiter,
      RateLimitProperties rateLimitProperties) {
    this.tokenBootstrapLookup = tokenBootstrapLookup;
    this.tenantBootstrapLookup = tenantBootstrapLookup;
    this.verificationTokenRepository = verificationTokenRepository;
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.verificationMailer = verificationMailer;
    this.transactionTemplate = transactionTemplate;
    this.properties = properties;
    this.rateLimiter = rateLimiter;
    this.rateLimitProperties = rateLimitProperties;
  }

  /**
   * Verifies an emailed link and activates the hospital.
   *
   * @param rawToken the token exactly as the client presented it
   * @throws InvalidTokenException 400 for unknown, expired, replayed or wrong-type tokens
   */
  public void verifyEmail(String rawToken) {
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
                          .filter(VerificationService::isVerifiable)
                          .orElseThrow(InvalidTokenException::new);

                  token.setUsedAt(Instant.now());
                  verificationTokenRepository.save(token);

                  Tenant tenant =
                      tenantRepository.findById(tenantId).orElseThrow(InvalidTokenException::new);
                  tenant.setStatus(TenantStatus.ACTIVE);
                  tenant.setVerifiedAt(Instant.now());
                  tenantRepository.save(tenant);

                  User admin =
                      userRepository
                          .findById(token.getUserId())
                          .orElseThrow(InvalidTokenException::new);
                  admin.setStatus(UserStatus.ACTIVE);
                  userRepository.save(admin);
                }));
  }

  /**
   * Re-issues a verification link — always answered with the same 202.
   *
   * <p>A new token is issued only for an existing, still-unverified account; an already-active or
   * unknown address simply produces no side effect at all. Prior links stay valid until they
   * expire: each is single-use, so none of them can be replayed, and invalidating in-flight links
   * would strand a user who has the first email open while asking for a resend.
   *
   * @param email the account address
   * @param hospitalSlug the hospital slug from the request (D2), never a tenant id
   * @throws com.healthcare.hms.common.ratelimit.RateLimitedException 429 when this address has
   *     asked three times in an hour (decision D7)
   */
  public void resendVerification(String email, String hospitalSlug) {
    // The very first statement, ahead of the slug lookup: resend shares registration's per-address
    // bucket, and it has to be charged before anything that could reveal whether the address exists
    // (decision D7). An unknown slug therefore still spends budget, exactly like a real one.
    rateLimiter.consume(
        RateLimitKeys.email(email),
        rateLimitProperties.getEmailAccountLimit(),
        rateLimitProperties.getEmailAccountWindow());

    UUID tenantId = tenantBootstrapLookup.findTenantIdBySlug(hospitalSlug).orElse(null);
    if (tenantId == null) {
      return;
    }

    String rawToken = TokenValues.newToken();
    String tokenHash = TokenValues.sha256Hex(rawToken);

    String hospitalName =
        TenantContext.call(
            tenantId,
            () ->
                transactionTemplate.execute(
                    status ->
                        userRepository
                            .findByEmail(email)
                            .filter(admin -> admin.getStatus() == UserStatus.PENDING)
                            .map(
                                admin -> {
                                  verificationTokenRepository.save(
                                      newVerificationToken(admin.getId(), tokenHash));
                                  return tenantRepository
                                      .findById(tenantId)
                                      .map(Tenant::getName)
                                      .orElse(null);
                                })
                            .orElse(null)));

    if (hospitalName != null) {
      verificationMailer.send(email, hospitalName, rawToken);
    }
  }

  private VerificationToken newVerificationToken(UUID userId, String tokenHash) {
    VerificationToken token = new VerificationToken();
    token.setUserId(userId);
    token.setType(VerificationTokenType.VERIFY_EMAIL);
    token.setTokenHash(tokenHash);
    token.setExpiresAt(Instant.now().plus(properties.getVerificationTokenTtl()));
    return token;
  }

  /** The row must be a live verification link: right type, unused, and not yet expired. */
  private static boolean isVerifiable(VerificationToken token) {
    return token.getType() == VerificationTokenType.VERIFY_EMAIL
        && token.getUsedAt() == null
        && token.getExpiresAt().isAfter(Instant.now());
  }
}
