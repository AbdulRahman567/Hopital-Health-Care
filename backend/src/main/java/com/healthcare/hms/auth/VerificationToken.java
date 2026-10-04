package com.healthcare.hms.auth;

import com.healthcare.hms.common.entity.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * A single-use, expiring link credential ({@code V4}, TDD section 9.2, SECURITY section 3).
 *
 * <p>Row values are <b>not</b> the secret the client holds: {@code tokenHash} is the SHA-256
 * digest, so a database dump yields nothing usable. Single-use is expressed by {@code usedAt}
 * rather than by deleting the row — the evidence of a consumed link outlives the link.
 *
 * <p>Inherits {@code @TenantId}: the row can only be read once {@code TenantContext} is bound to
 * its tenant, which is exactly why the D1 secret-leg directory exists.
 */
@Entity
@Table(
    name = "verification_tokens",
    uniqueConstraints =
        @UniqueConstraint(name = "uq_verification_tokens_token_hash", columnNames = "token_hash"))
public class VerificationToken extends TenantOwnedEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 16, updatable = false)
  private VerificationTokenType type;

  @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  protected VerificationToken() {}

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public VerificationTokenType getType() {
    return type;
  }

  public void setType(VerificationTokenType type) {
    this.type = type;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String tokenHash) {
    this.tokenHash = tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getUsedAt() {
    return usedAt;
  }

  public void setUsedAt(Instant usedAt) {
    this.usedAt = usedAt;
  }
}
