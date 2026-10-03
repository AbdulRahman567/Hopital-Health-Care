package com.healthcare.hms.auth;

import com.healthcare.hms.auth.repository.RefreshTokenRepository;
import com.healthcare.hms.auth.repository.UserRepository;
import com.healthcare.hms.tenant.TenantContext;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque refresh-token family management: issue, rotate, revoke (FR-2.2, plan P5.4, decisions D8
 * and D10).
 *
 * <p><b>The shape of a session.</b> One login mints one {@code familyId} and its first token; every
 * rotation revokes the presented row as {@link RefreshTokenRevokedReason#ROTATED} and inserts a
 * successor into the same family. A session is therefore a chain of rows, which is what lets reuse
 * be a <i>fact</i> rather than a guess: a presented token that is already revoked cannot be the
 * result of an ordinary rotation, so somebody is replaying a value they should no longer have — and
 * the only safe response is to assume the value leaked and kill every descendant with it.
 *
 * <p><b>Transport is deliberately absent.</b> The HTTP surface (the {@code hms_refresh} cookie,
 * {@code /auth/refresh}, {@code /auth/logout}) is P5.5; this task delivers the one seam those
 * endpoints stand on. {@link #issue} is likewise not yet called from {@code LoginService} — issuing
 * a token no caller could ever present would mint rows nobody can use. P5.5 wires both together.
 *
 * <p>Requires {@link TenantContext} to be bound before the call: the entity carries
 * {@code @TenantId}, so an unbound call is a programming error (an {@link IllegalStateException})
 * rather than a client-facing failure.
 */
@Service
public class RefreshTokenService {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

  /** {@code user_agent VARCHAR(512)}; a long client string is truncated, never rejected. */
  private static final int USER_AGENT_MAX = 512;

  private final RefreshTokenRepository refreshTokenRepository;
  private final UserRepository userRepository;
  private final AuthProperties properties;

  public RefreshTokenService(
      RefreshTokenRepository refreshTokenRepository,
      UserRepository userRepository,
      AuthProperties properties) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.userRepository = userRepository;
    this.properties = properties;
  }

  /**
   * Starts a new login session: a fresh family and its first token.
   *
   * @param user the account the session belongs to
   * @param userAgent device metadata for the audit trail; never used to make a decision
   * @return the raw value to hand the client, plus the family and expiry
   */
  @Transactional
  public IssuedRefreshToken issue(User user, String userAgent) {
    UUID tenantId = TenantContext.require();
    Instant now = Instant.now();
    UUID familyId = UUID.randomUUID();
    String rawToken = TokenValues.newToken();

    RefreshToken token = new RefreshToken();
    token.setUserId(user.getId());
    token.setFamilyId(familyId);
    token.setTokenHash(TokenValues.sha256Hex(rawToken));
    token.setExpiresAt(expiryFor(now, now));
    token.setUserAgent(trim(userAgent));
    refreshTokenRepository.save(token);

    log.debug("Refresh token issued: familyId={}, tenantId={}", familyId, tenantId);
    return new IssuedRefreshToken(user.getId(), rawToken, familyId, token.getExpiresAt());
  }

  /**
   * Presents a token: rotates it when it is live, poisons the family when it is not.
   *
   * <p>The two outcomes are deliberately indistinguishable from outside — see {@link
   * InvalidRefreshTokenException} — but they differ in what happens to the family, which is the
   * whole point of the exercise.
   *
   * <p>{@code noRollbackFor} is load-bearing: reuse detection <i>ends</i> with a rejection, and
   * rolling the rejection back would also roll back the family-wide revocation that makes it
   * meaningful. The other rejection reasons write nothing, so committing them is a no-op.
   *
   * @param rawToken the value the client presented
   * @param userAgent device metadata for the successor row
   * @return the successor's raw value and family
   * @throws InvalidRefreshTokenException for unknown, expired, replayed, capped and deactivated
   *     cases alike
   */
  @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
  public IssuedRefreshToken rotate(String rawToken, String userAgent) {
    UUID tenantId = TenantContext.require();
    Instant now = Instant.now();

    RefreshToken presented =
        refreshTokenRepository
            .lockByTokenHash(TokenValues.sha256Hex(rawToken))
            .orElseThrow(InvalidRefreshTokenException::new);

    if (presented.isRevoked()) {
      // Only a token that has not yet expired is evidence of a replay: an expired one simply lapsed
      // and tells us nothing about whether anyone else holds it.
      if (presented.getExpiresAt().isAfter(now)) {
        int revoked = revokeFamily(presented.getFamilyId(), RefreshTokenRevokedReason.REUSED);
        log.warn(
            "event=refresh_token_reuse_detected tenantId={} userId={} familyId={} rowsRevoked={}",
            tenantId,
            presented.getUserId(),
            presented.getFamilyId(),
            revoked);
      }
      throw new InvalidRefreshTokenException();
    }

    if (!presented.getExpiresAt().isAfter(now)) {
      throw new InvalidRefreshTokenException();
    }

    User user =
        userRepository
            .findById(presented.getUserId())
            .orElseThrow(InvalidRefreshTokenException::new);
    if (user.getStatus() != UserStatus.ACTIVE) {
      // The schema's CHECK offers ROTATED/REUSED/LOGOUT/PASSWORD_RESET and no free text; a session
      // ended because the account stopped being usable is recorded as LOGOUT, the closest of the
      // four, so the row still says "the session was stopped deliberately".
      revokeFamily(presented.getFamilyId(), RefreshTokenRevokedReason.LOGOUT);
      log.debug(
          "Refresh family revoked: account is not active userId={} tenantId={}",
          user.getId(),
          tenantId);
      throw new InvalidRefreshTokenException();
    }

    RefreshToken root =
        refreshTokenRepository
            .findFirstByFamilyIdOrderByCreatedAtAsc(presented.getFamilyId())
            .orElse(presented);
    Instant familyStartedAt = root.getCreatedAt();
    if (!familyStartedAt.plus(properties.getRefreshFamilyMaxAge()).isAfter(now)) {
      // Decision D8's absolute cap: the sliding window may not be extended past 30 days from the
      // family's first token, so this session stops producing successors here. The rows are left
      // to expire on their own — they are not evidence of anything.
      throw new InvalidRefreshTokenException();
    }

    presented.setRevokedAt(now);
    presented.setRevokedReason(RefreshTokenRevokedReason.ROTATED);
    refreshTokenRepository.save(presented);

    return insert(presented.getFamilyId(), user, userAgent, expiryFor(now, familyStartedAt));
  }

  /**
   * Revokes every row of one login session, leaving the rows and their reason behind as evidence.
   *
   * <p>Used by logout (P5.5), by password reset (P5.7) and internally by reuse detection. Rows that
   * are already revoked keep the reason they were first given — overwriting {@code ROTATED} with
   * {@code REUSED} would erase the chain that shows how the session got here.
   *
   * @param familyId family to end
   * @param reason why, recorded on every row this call actually revokes
   * @return how many rows were still live when the call ran
   */
  @Transactional
  public int revokeFamily(UUID familyId, RefreshTokenRevokedReason reason) {
    TenantContext.require();
    Instant now = Instant.now();
    int revoked = 0;
    for (RefreshToken token : refreshTokenRepository.findAllByFamilyId(familyId)) {
      if (token.isRevoked()) {
        continue;
      }
      token.setRevokedAt(now);
      token.setRevokedReason(reason);
      refreshTokenRepository.save(token);
      revoked++;
    }
    return revoked;
  }

  private IssuedRefreshToken insert(UUID familyId, User user, String userAgent, Instant expiresAt) {
    String rawToken = TokenValues.newToken();
    RefreshToken successor = new RefreshToken();
    successor.setUserId(user.getId());
    successor.setFamilyId(familyId);
    successor.setTokenHash(TokenValues.sha256Hex(rawToken));
    successor.setExpiresAt(expiresAt);
    successor.setUserAgent(trim(userAgent));
    refreshTokenRepository.save(successor);
    return new IssuedRefreshToken(user.getId(), rawToken, familyId, expiresAt);
  }

  /**
   * Sliding lifetime, never past the family's absolute deadline — the smaller of the two.
   *
   * <p>For a brand-new family both windows start now, so this reduces to the sliding TTL.
   */
  private Instant expiryFor(Instant now, Instant familyStartedAt) {
    Instant sliding = now.plus(properties.getRefreshTokenTtl());
    Instant absolute = familyStartedAt.plus(properties.getRefreshFamilyMaxAge());
    return sliding.isBefore(absolute) ? sliding : absolute;
  }

  private static String trim(String userAgent) {
    if (userAgent == null || userAgent.isEmpty()) {
      return null;
    }
    return userAgent.length() <= USER_AGENT_MAX
        ? userAgent
        : userAgent.substring(0, USER_AGENT_MAX);
  }
}
