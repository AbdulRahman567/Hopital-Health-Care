package com.healthcare.hms.auth.repository;

import com.healthcare.hms.auth.RefreshToken;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Tenant-scoped access to {@code refresh_tokens}.
 *
 * <p>Every method is filtered by Hibernate because {@link RefreshToken} inherits {@code @TenantId}:
 * a digest that belongs to another tenant is simply not there (ADR-006), which is why a leaked
 * cookie cannot be replayed against someone else's session and why a wrong-tenant lookup is
 * indistinguishable from an unknown one.
 *
 * <p>The two lookups by {@code tokenHash} differ only in locking — see {@link #lockByTokenHash}.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  /** Plain read of one row by digest; used wherever no mutation follows. */
  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /** Every row of one login session, oldest first. */
  List<RefreshToken> findAllByFamilyId(UUID familyId);

  /**
   * Every row ever issued to one account, across all of its login sessions.
   *
   * <p>Needed by password reset (SECURITY section 15: changing the password revokes every session
   * started under the old one), where the point is precisely that a user may have more than one
   * family open and all of them have to go at once. Still tenant-filtered like everything else: the
   * id can only be reached after the D1 secret leg has bound the tenant.
   */
  List<RefreshToken> findAllByUserId(UUID userId);

  /**
   * The first row ever written for a family — the family's age, and therefore the input to decision
   * D8's absolute cap, is measured from here rather than from any single token's lifetime.
   */
  Optional<RefreshToken> findFirstByFamilyIdOrderByCreatedAtAsc(UUID familyId);

  /**
   * Reads one row with {@code SELECT ... FOR UPDATE}.
   *
   * <p>Rotation must decide between "this token is still live, rotate it" and "this token is
   * already dead, poison the family" from the row's current state, so two requests presenting the
   * same value have to be serialised — otherwise both could read {@code revoked_at IS NULL} and
   * both would mint a successor, leaving two live descendants where the design allows exactly one.
   *
   * <p>Requires a transaction, which is why {@code RefreshTokenService} is the only caller and why
   * it declares one.
   *
   * @param tokenHash SHA-256 digest of the presented token
   * @return the row, locked for update, or empty when the digest is unknown here
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
  Optional<RefreshToken> lockByTokenHash(@Param("tokenHash") String tokenHash);
}
