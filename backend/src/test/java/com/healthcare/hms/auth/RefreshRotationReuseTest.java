package com.healthcare.hms.auth;

import static com.healthcare.hms.auth.RefreshTokenRevokedReason.LOGOUT;
import static com.healthcare.hms.auth.RefreshTokenRevokedReason.REUSED;
import static com.healthcare.hms.auth.RefreshTokenRevokedReason.ROTATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.healthcare.hms.auth.repository.RefreshTokenRepository;
import com.healthcare.hms.auth.repository.UserRepository;
import com.healthcare.hms.tenant.TenantContext;
import com.healthcare.hms.tenant.TenantStatus;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * P5.4 &mdash; refresh-token families, rotation and reuse detection (FR-2.2, plan section 6,
 * decisions D8 and D10).
 *
 * <p>Service level on purpose: P5.4 delivers the seam, P5.5 puts it behind {@code POST
 * /api/v1/auth/refresh}, and every case below is a property of the seam rather than of HTTP. The
 * real {@link RefreshTokenService}, the real {@code @TenantId} filter and the real schema are in
 * play; only the rows are fixtures.
 *
 * <p>Five things are proved here, in decreasing order of how bad it would be to get them wrong:
 *
 * <ul>
 *   <li>a token that is already dead, presented again, kills the whole family &mdash; the single
 *       security claim the design makes;
 *   <li>a family cannot outlive its 30-day cap no matter how often it is rotated;
 *   <li>an <i>expired</i> token is rejected <b>without</b> that revocation, so a slow network is
 *       not mistaken for an attack;
 *   <li>the secret is only ever stored as a digest;
 *   <li>a digest is invisible outside the tenant that minted it, and an account that is no longer
 *       active cannot keep a session alive it already had.
 * </ul>
 *
 * <p>Rows this suite creates are removed again in {@code @AfterEach} (slug prefix {@code p54-}),
 * because the database is JVM-scoped and shared with every other suite (TESTING section 4).
 */
@SpringBootTest
class RefreshRotationReuseTest {

  private static final String PREFIX = "p54-";
  private static final String AGENT = "p54-test-agent";

  @Autowired private RefreshTokenService service;
  @Autowired private AuthFixtures fixtures;
  @Autowired private AuthProperties properties;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private UserRepository userRepository;

  private UUID tenantId;
  private User user;

  @BeforeEach
  void createHospital() {
    tenantId = fixtures.createTenant(PREFIX + UUID.randomUUID(), TenantStatus.ACTIVE);
    user =
        fixtures.createUser(
            tenantId, "admin@p54.test", "Correct-Horse-Battery-9", UserStatus.ACTIVE, false);
  }

  @AfterEach
  void cleanUp() {
    TenantContext.clear();
    fixtures.deleteTenantsStartingWith(PREFIX + "%");
  }

  // -------------------------------------------------------------------------
  // rotation
  // -------------------------------------------------------------------------

  @Test
  void rotationHandsOutANewSecretEachTimeAndRetiresEachPredecessor() {
    IssuedRefreshToken first = issue();

    assertThat(first.rawToken()).as("a fresh 256-bit value, base64url without padding").hasSize(43);

    IssuedRefreshToken second = rotate(first.rawToken());
    IssuedRefreshToken third = rotate(second.rawToken());

    assertThat(second.rawToken()).isNotEqualTo(first.rawToken());
    assertThat(third.rawToken()).isNotEqualTo(second.rawToken());
    assertThat(second.familyId())
        .as("rotation continues the same login session")
        .isEqualTo(first.familyId())
        .isEqualTo(third.familyId());

    List<RefreshToken> family = family(first.familyId());
    assertThat(family).hasSize(3);

    assertThat(family)
        .filteredOn(RefreshToken::isRevoked)
        .hasSize(2)
        .allSatisfy(
            row -> {
              assertThat(row.getRevokedReason()).isEqualTo(ROTATED);
              assertThat(row.getRevokedAt()).isNotNull();
            });

    assertThat(family)
        .filteredOn(row -> !row.isRevoked())
        .as("exactly one live descendant, holding the value just handed back")
        .singleElement()
        .satisfies(
            row ->
                assertThat(row.getTokenHash()).isEqualTo(TokenValues.sha256Hex(third.rawToken())));
  }

  // -------------------------------------------------------------------------
  // reuse
  // -------------------------------------------------------------------------

  @Test
  void replayingAnAlreadyRotatedTokenRevokesTheEntireFamily() {
    IssuedRefreshToken first = issue();
    IssuedRefreshToken second = rotate(first.rawToken());
    IssuedRefreshToken third = rotate(second.rawToken());

    assertThatThrownBy(
            () -> TenantContext.run(tenantId, () -> service.rotate(first.rawToken(), AGENT)))
        .isInstanceOf(InvalidRefreshTokenException.class);

    List<RefreshToken> family = family(first.familyId());
    assertThat(family)
        .as("no survivor is left in a poisoned family")
        .allMatch(RefreshToken::isRevoked);

    assertThat(family)
        .filteredOn(row -> row.getRevokedReason() == ROTATED)
        .as("rows retired by ordinary rotation keep the reason that explains their chain")
        .hasSize(2);
    assertThat(family)
        .filteredOn(row -> row.getRevokedReason() == REUSED)
        .as("only the still-live descendant is rewritten")
        .hasSize(1);

    assertThatThrownBy(
            () -> TenantContext.run(tenantId, () -> service.rotate(third.rawToken(), AGENT)))
        .as("the successor the attacker never had is dead as well")
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  // -------------------------------------------------------------------------
  // expiry
  // -------------------------------------------------------------------------

  @Test
  void anExpiredTokenIsRejectedWithoutPoisoningTheFamily() {
    Duration originalTtl = properties.getRefreshTokenTtl();
    properties.setRefreshTokenTtl(Duration.ZERO);
    try {
      IssuedRefreshToken expired = issue();

      assertThatThrownBy(
              () -> TenantContext.run(tenantId, () -> service.rotate(expired.rawToken(), AGENT)))
          .isInstanceOf(InvalidRefreshTokenException.class);

      assertThat(family(expired.familyId()))
          .as("a lapsed token is not evidence of a replay, so nothing is revoked")
          .noneMatch(RefreshToken::isRevoked);
    } finally {
      properties.setRefreshTokenTtl(originalTtl);
    }
  }

  // -------------------------------------------------------------------------
  // family age cap
  // -------------------------------------------------------------------------

  @Test
  void aFamilyPastTheThirtyDayCapStopsRotating() {
    IssuedRefreshToken first = issue();
    jdbcTemplate.update(
        "UPDATE refresh_tokens SET created_at = ? WHERE token_hash = ?",
        Timestamp.from(Instant.now().minus(Duration.ofDays(31))),
        TokenValues.sha256Hex(first.rawToken()));

    assertThatThrownBy(
            () -> TenantContext.run(tenantId, () -> service.rotate(first.rawToken(), AGENT)))
        .as("the sliding window may not be extended past the family's absolute deadline")
        .isInstanceOf(InvalidRefreshTokenException.class);

    assertThat(family(first.familyId()))
        .as("an over-age family is left to expire, not treated as compromised")
        .noneMatch(RefreshToken::isRevoked);
  }

  // -------------------------------------------------------------------------
  // storage
  // -------------------------------------------------------------------------

  @Test
  void theRawTokenIsNeverWrittenToTheDatabase() {
    IssuedRefreshToken first = issue();
    rotate(first.rawToken());

    String stored =
        jdbcTemplate.queryForObject(
            "SELECT token_hash FROM refresh_tokens WHERE token_hash = ?",
            String.class,
            TokenValues.sha256Hex(first.rawToken()));
    assertThat(stored)
        .as("the digest is what is persisted")
        .isEqualTo(TokenValues.sha256Hex(first.rawToken()))
        .hasSize(64);
    assertThat(stored).isNotEqualTo(first.rawToken());

    List<String> storedRaw =
        jdbcTemplate.queryForList(
            "SELECT token_hash FROM refresh_tokens WHERE token_hash = ?",
            String.class,
            first.rawToken());
    assertThat(storedRaw)
        .as("no column of the row ever equals the value the client holds")
        .isEmpty();
  }

  // -------------------------------------------------------------------------
  // tenancy
  // -------------------------------------------------------------------------

  @Test
  void aTokenMintedByAnotherHospitalCannotBePresentedHere() {
    IssuedRefreshToken foreign = issue();
    UUID otherTenantId =
        fixtures.createTenant(PREFIX + "other-" + UUID.randomUUID(), TenantStatus.ACTIVE);

    assertThatThrownBy(
            () -> TenantContext.run(otherTenantId, () -> service.rotate(foreign.rawToken(), AGENT)))
        .as("the digest simply is not in the other tenant's row set")
        .isInstanceOf(InvalidRefreshTokenException.class);

    assertThat(family(foreign.familyId()))
        .as("a cross-tenant guess must not damage the session it guessed at")
        .noneMatch(RefreshToken::isRevoked);
  }

  // -------------------------------------------------------------------------
  // account state
  // -------------------------------------------------------------------------

  @Test
  void deactivatingTheAccountEndsTheSessionItStillHeld() {
    IssuedRefreshToken first = issue();

    TenantContext.run(
        tenantId,
        () -> {
          User managed = userRepository.findById(user.getId()).orElseThrow();
          managed.setStatus(UserStatus.INACTIVE);
          userRepository.save(managed);
        });

    assertThatThrownBy(
            () -> TenantContext.run(tenantId, () -> service.rotate(first.rawToken(), AGENT)))
        .isInstanceOf(InvalidRefreshTokenException.class);

    List<RefreshToken> family = family(first.familyId());
    assertThat(family).allMatch(RefreshToken::isRevoked);
    assertThat(family).allSatisfy(row -> assertThat(row.getRevokedReason()).isEqualTo(LOGOUT));
  }

  // -------------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------------

  private IssuedRefreshToken issue() {
    return TenantContext.call(tenantId, () -> service.issue(user, AGENT));
  }

  private IssuedRefreshToken rotate(String rawToken) {
    return TenantContext.call(tenantId, () -> service.rotate(rawToken, AGENT));
  }

  private List<RefreshToken> family(UUID familyId) {
    return TenantContext.call(tenantId, () -> refreshTokenRepository.findAllByFamilyId(familyId));
  }
}
