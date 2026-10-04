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
 * One link in a refresh-token family ({@code V4}, TDD section 9.2, SECURITY section 3).
 *
 * <p>The row is never the secret: {@code tokenHash} holds the SHA-256 digest, so a database dump
 * yields nothing that can be presented, and {@link TokenValues#newToken()} — the value the client
 * keeps — exists only in transit (plan risk 11).
 *
 * <p><b>Rotation is written, not mutated.</b> Presenting a valid token revokes this row with {@link
 * RefreshTokenRevokedReason#ROTATED} and inserts a successor carrying the same {@code familyId}, so
 * the chain of a login session is a set of rows rather than a moving pointer. That is what makes
 * reuse detectable: a token that has been superseded is a row that is already revoked, and finding
 * one means someone is replaying a value they should no longer have.
 *
 * <p>Inherits {@code @TenantId} like every other tenant-owned row (ISO-5), so a digest can only be
 * read once {@link TenantContext} is bound to the tenant that minted it.
 */
@Entity
@Table(
    name = "refresh_tokens",
    uniqueConstraints =
        @UniqueConstraint(name = "uq_refresh_tokens_token_hash", columnNames = "token_hash"))
public class RefreshToken extends TenantOwnedEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  /**
   * The login session this row belongs to. A new value per login, never per rotation — everything
   * downstream (reuse detection, logout, password reset) is "revoke the family".
   */
  @Column(name = "family_id", nullable = false, updatable = false)
  private UUID familyId;

  @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "revoked_reason", length = 16)
  private RefreshTokenRevokedReason revokedReason;

  /**
   * The client that minted this row. Recorded for the audit trail (SECURITY section 10) and never
   * trusted for any decision — a {@code User-Agent} is attacker-controlled and proves nothing.
   */
  @Column(name = "user_agent", length = 512, updatable = false)
  private String userAgent;

  protected RefreshToken() {}

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public UUID getFamilyId() {
    return familyId;
  }

  public void setFamilyId(UUID familyId) {
    this.familyId = familyId;
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

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(Instant revokedAt) {
    this.revokedAt = revokedAt;
  }

  public RefreshTokenRevokedReason getRevokedReason() {
    return revokedReason;
  }

  public void setRevokedReason(RefreshTokenRevokedReason revokedReason) {
    this.revokedReason = revokedReason;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  /** True once the token has been rotated, revoked or poisoned — i.e. it may not be used again. */
  public boolean isRevoked() {
    return revokedAt != null;
  }
}
