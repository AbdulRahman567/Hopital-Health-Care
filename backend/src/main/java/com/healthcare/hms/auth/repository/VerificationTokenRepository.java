package com.healthcare.hms.auth.repository;

import com.healthcare.hms.auth.VerificationToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Tenant-scoped access to {@link VerificationToken}.
 *
 * <p>Every method is filtered by Hibernate because the entity inherits {@code @TenantId}: a digest
 * presented by a caller only resolves after the D1 secret-leg directory has bound that digest's
 * tenant, so a foreign tenant's row is invisible rather than readable.
 *
 * <p>Lookups are by {@code token_hash}, which carries a unique index ({@code V4}) — one digest
 * addresses at most one row, which is what makes the bootstrap directory's "±1 row" guarantee true.
 */
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

  Optional<VerificationToken> findByTokenHash(String tokenHash);
}
