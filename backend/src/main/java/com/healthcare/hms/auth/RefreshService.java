package com.healthcare.hms.auth;

import com.healthcare.hms.auth.api.LoginResponse;
import com.healthcare.hms.auth.api.RefreshResponse;
import com.healthcare.hms.auth.api.SessionProfile;
import com.healthcare.hms.auth.repository.RefreshTokenRepository;
import com.healthcare.hms.auth.repository.UserRepository;
import com.healthcare.hms.tenant.Tenant;
import com.healthcare.hms.tenant.TenantContext;
import com.healthcare.hms.tenant.TenantRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The HTTP-facing half of the refresh family (FR-2.2 / FR-2.6, plan P5.5): rotate on presentation,
 * revoke on logout, both reached only through decision D1's secret leg.
 *
 * <p><b>Why the bootstrap lookup comes first.</b> A refresh carries no bearer token, so {@link
 * TenantContext} is empty when the request arrives — and Hibernate will not open a session with an
 * empty context, not even to read the row that would tell it which tenant to use. The digest is
 * therefore resolved to a tenant id with a single {@code JdbcTemplate} statement (a key directory,
 * documented exactly like ISO-1's native guardrail), and only then is the scope bound for the
 * business read through the ordinary tenant-filtered repository.
 *
 * <p><b>Both endpoints answer 401 for everything.</b> Missing cookie, unknown digest, expired
 * token, replayed token, over-age family, deactivated account — one {@link
 * InvalidRefreshTokenException} each (decision D9), so a caller cannot tell a garbage cookie from a
 * family somebody else already poisoned. Logout adds idempotency on top: a family that is already
 * revoked is revoked again (a no-op) and still answers 200, because a user who clicks logout twice
 * has not made a mistake.
 */
@Service
public class RefreshService {

  private static final Logger log = LoggerFactory.getLogger(RefreshService.class);

  private final TokenBootstrapLookup tokenBootstrapLookup;
  private final RefreshTokenRepository refreshTokenRepository;
  private final RefreshTokenService refreshTokenService;
  private final UserRepository userRepository;
  private final TenantRepository tenantRepository;
  private final JwtTokenService jwtTokenService;

  public RefreshService(
      TokenBootstrapLookup tokenBootstrapLookup,
      RefreshTokenRepository refreshTokenRepository,
      RefreshTokenService refreshTokenService,
      UserRepository userRepository,
      TenantRepository tenantRepository,
      JwtTokenService jwtTokenService) {
    this.tokenBootstrapLookup = tokenBootstrapLookup;
    this.refreshTokenRepository = refreshTokenRepository;
    this.refreshTokenService = refreshTokenService;
    this.userRepository = userRepository;
    this.tenantRepository = tenantRepository;
    this.jwtTokenService = jwtTokenService;
  }

  /**
   * Presents the cookie's token: rotates it and answers with a fresh access token plus the profile
   * decision D10 asks for.
   *
   * @param rawToken value of the {@code hms_refresh} cookie; {@code null} when the browser sent
   *     none
   * @param userAgent device metadata recorded on the successor row, never used for a decision
   * @return the body for this response and the successor token for its cookie
   * @throws InvalidRefreshTokenException 401 for every way this can fail
   */
  public RefreshSession refresh(String rawToken, String userAgent) {
    UUID tenantId = tenantIdOf(rawToken);
    return TenantContext.call(
        tenantId,
        () -> {
          IssuedRefreshToken rotated = refreshTokenService.rotate(rawToken, userAgent);
          User user =
              userRepository
                  .findById(rotated.userId())
                  .orElseThrow(InvalidRefreshTokenException::new);
          log.info(
              "Refresh token rotated: userId={}, tenantId={}, familyId={}",
              user.getId(),
              tenantId,
              rotated.familyId());
          LoginResponse tokens = jwtTokenService.issue(user, tenantId);
          RefreshResponse body =
              new RefreshResponse(
                  tokens.accessToken(),
                  tokens.tokenType(),
                  tokens.expiresIn(),
                  profile(user, tenantId));
          return new RefreshSession(body, rotated);
        });
  }

  /**
   * Ends the login session the cookie belongs to and drops the cookie.
   *
   * <p>Idempotent by design: rows already revoked keep the reason they were first given, so a
   * second logout of the same token finds nothing to do and still succeeds. That is what lets the
   * client treat logout as "make sure it is gone" rather than a request that must not be repeated.
   *
   * @param rawToken value of the {@code hms_refresh} cookie
   * @throws InvalidRefreshTokenException 401 when there is no such session to end
   */
  public void logout(String rawToken) {
    UUID tenantId = tenantIdOf(rawToken);
    TenantContext.run(
        tenantId,
        () -> {
          RefreshToken presented =
              refreshTokenRepository
                  .findByTokenHash(TokenValues.sha256Hex(rawToken))
                  .orElseThrow(InvalidRefreshTokenException::new);
          int revoked =
              refreshTokenService.revokeFamily(
                  presented.getFamilyId(), RefreshTokenRevokedReason.LOGOUT);
          log.info(
              "Refresh family revoked on logout: familyId={}, tenantId={}, rowsRevoked={}",
              presented.getFamilyId(),
              tenantId,
              revoked);
        });
  }

  /**
   * Decision D1's secret leg, applied to refresh tokens.
   *
   * @param rawToken the presented secret, or {@code null}/blank when the cookie was absent
   * @return the tenant that minted it
   * @throws InvalidRefreshTokenException 401 when there is no cookie or no such token
   */
  private UUID tenantIdOf(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new InvalidRefreshTokenException();
    }
    return tokenBootstrapLookup
        .findTenantIdByRefreshTokenHash(TokenValues.sha256Hex(rawToken))
        .orElseThrow(InvalidRefreshTokenException::new);
  }

  private SessionProfile profile(User user, UUID tenantId) {
    String tenantName = tenantRepository.findById(tenantId).map(Tenant::getName).orElse(null);
    return new SessionProfile(
        user.getId().toString(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        List.of(),
        tenantName);
  }
}
