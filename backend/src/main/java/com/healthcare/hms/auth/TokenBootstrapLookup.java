package com.healthcare.hms.auth;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Decision D1, secret leg — the directory that turns an unguessable token into the tenant it
 * belongs to, so {@code TenantContext} can be bound <i>before</i> any tenant-filtered read.
 *
 * <p>{@code verify-email} and {@code reset-password} carry no slug and no tenant id (API.md section
 * 5 forbids both), so without this lookup the request would reach a repository with an empty
 * context and fail closed. The digest is the whole credential: {@code token_hash} is unique ({@code
 * V4}), so a hit is ±1 row and a miss simply produces the uniform failure.
 *
 * <p>Same discipline as the slug leg and as ISO-1 (ENGINEERING_RULES section 5, plan risk 11): a
 * single statement, keyed by a digest, returning <b>a key and nothing else</b>. It must stay a
 * {@link JdbcTemplate} because Hibernate will not open a session without a bound context. Row data
 * is read afterwards, through the normal tenant-filtered repository, under the bound context.
 */
@Component
public class TokenBootstrapLookup {

  private static final String FIND_TENANT_ID_BY_HASH =
      "SELECT tenant_id FROM verification_tokens WHERE token_hash = ? LIMIT 1";

  private static final String FIND_TENANT_ID_BY_REFRESH_HASH =
      "SELECT tenant_id FROM refresh_tokens WHERE token_hash = ? LIMIT 1";

  private final JdbcTemplate jdbcTemplate;

  public TokenBootstrapLookup(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @param tokenHash SHA-256 hex digest of the token the client presented
   * @return the owning tenant, or empty when no such token exists — the caller answers with the
   *     uniform failure and never distinguishes "unknown" from "expired"
   */
  public Optional<UUID> findTenantIdByTokenHash(String tokenHash) {
    return findTenantId(FIND_TENANT_ID_BY_HASH, tokenHash);
  }

  /**
   * The refresh-token half of the same directory (P5.5): {@code refresh} and {@code logout} carry
   * nothing but the cookie, so this is what tells the request which tenant to bind before it may
   * touch a row.
   *
   * @param tokenHash SHA-256 hex digest of the cookie's value
   * @return the owning tenant, or empty when no such token exists — the caller answers with the
   *     uniform 401 and never distinguishes "unknown" from "expired" from "revoked"
   */
  public Optional<UUID> findTenantIdByRefreshTokenHash(String tokenHash) {
    return findTenantId(FIND_TENANT_ID_BY_REFRESH_HASH, tokenHash);
  }

  private Optional<UUID> findTenantId(String sql, String tokenHash) {
    return jdbcTemplate.queryForList(sql, byte[].class, tokenHash).stream()
        .map(TokenBootstrapLookup::toUuid)
        .filter(Objects::nonNull)
        .findFirst();
  }

  private static UUID toUuid(byte[] raw) {
    if (raw.length != 16) {
      return null;
    }
    long msb = 0;
    long lsb = 0;
    for (int i = 0; i < 8; i++) {
      msb = (msb << 8) | (raw[i] & 0xffL);
      lsb = (lsb << 8) | (raw[i + 8] & 0xffL);
    }
    return new UUID(msb, lsb);
  }
}
