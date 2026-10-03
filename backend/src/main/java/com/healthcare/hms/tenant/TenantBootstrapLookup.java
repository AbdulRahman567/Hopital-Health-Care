package com.healthcare.hms.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Decision D1, slug leg — the narrow directory an <i>anonymous</i> request uses to learn which
 * tenant it is talking about ({@code login}, {@code forgot-password}, {@code resend-verification}
 * accept a {@code hospitalSlug}).
 *
 * <p>It must be a {@link JdbcTemplate} and not {@link TenantRepository}: with an empty {@link
 * TenantContext} Hibernate refuses to open a session at all ({@code "No tenant context is bound to
 * the current thread"}), which the decision-D1 probe confirmed for platform tables too. Reading the
 * key first, then binding, is therefore the only order that works.
 *
 * <p>Deliberately narrow — the ISO-1 pattern (ENGINEERING_RULES section 5, plan risk 11): one
 * statement, keyed by the caller's lookup value, returning <b>a key and nothing else</b>. No row
 * data crosses this boundary and no application code may widen it; everything after the binding
 * goes through the normal tenant-filtered repositories.
 */
@Component
public class TenantBootstrapLookup {

  private static final String FIND_TENANT_ID_BY_SLUG =
      "SELECT id FROM tenants WHERE slug = ? LIMIT 1";

  private final JdbcTemplate jdbcTemplate;

  public TenantBootstrapLookup(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Resolves a tenant's id from its public slug.
   *
   * <p>ENGINEERING_RULES section 1.2: the caller supplies a <i>lookup key</i>, never a tenant id —
   * the id returned here comes from a database row and is what {@code TenantContext} is then bound
   * to.
   *
   * @param slug the hospital slug from the request
   * @return the tenant id, or empty when no such slug exists (never 404 — D2 forbids tenant
   *     enumeration, so the caller answers with its uniform response)
   */
  public Optional<UUID> findTenantIdBySlug(String slug) {
    return jdbcTemplate.queryForList(FIND_TENANT_ID_BY_SLUG, byte[].class, slug).stream()
        .map(TenantBootstrapLookup::toUuid)
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
