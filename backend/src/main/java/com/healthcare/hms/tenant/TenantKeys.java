package com.healthcare.hms.tenant;

import java.util.UUID;

/**
 * The two tenant key shapes of TDD sections 12 and 13, as pure string builders.
 *
 * <p>Decision <b>D7</b>: no Redis client, no storage client, no Spring — just the prefixes, so
 * every future cache key, rate-limit key and object key is built the same way (ARCHITECTURE section
 * 3 puts this class in {@code tenant}, which {@code auth}, {@code document}, {@code notification}
 * and {@code search} are allowed to depend on).
 *
 * <p>ENGINEERING_RULES section 1.2: the {@code tenantId} handed to these methods must come from
 * {@link TenantContext}, never from a request DTO, header or path variable. A key is a namespace —
 * a tenant id from the client would put one tenant's cache entries or objects under another
 * tenant's prefix.
 *
 * <p>Every key is prefixed, so no call can produce an unprefixed key, and a storage key can never
 * escape its tenant directory: {@code ..}, {@code /} and {@code \} are rejected outright (path
 * traversal, SECURITY.md section 7).
 */
public final class TenantKeys {

  private TenantKeys() {}

  /**
   * A tenant-scoped Redis key: {@code t:<tenantId>:part:part}.
   *
   * <p>Covers every tenant-bound pattern in TDD section 13 — {@code t:<tid>:rl:<scope>:<id>},
   * {@code t:<tid>:cache:<name>:<key>}, {@code t:<tid>:notif:<userId>} — by joining the parts with
   * a colon after the prefix.
   *
   * @param tenantId tenant to namespace under, from {@link TenantContext}
   * @param parts key segments after the prefix; at least one, none null or blank
   * @return {@code t:<tenantId>:} followed by the colon-joined parts
   * @throws IllegalArgumentException when the tenant or any part is missing
   */
  public static String redis(UUID tenantId, String... parts) {
    requireTenant(tenantId);
    return "t:" + tenantId + ":" + join(parts, ":", false);
  }

  /**
   * A tenant-scoped object key: {@code tenants/<tenantId>/part/part}.
   *
   * <p>Matches TDD section 12's object key ({@code
   * tenants/{tenantId}/patients/{patientId}/{uuid}}). The original filename is never part of the
   * key (SECURITY.md section 7): it is stored as metadata.
   *
   * @param tenantId tenant to namespace under, from {@link TenantContext}
   * @param parts key segments after the prefix; at least one, none null or blank, none containing
   *     {@code ..}, {@code /} or {@code \}
   * @return {@code tenants/<tenantId>/} followed by the slash-joined parts
   * @throws IllegalArgumentException when the tenant or any part is missing, or a part looks like a
   *     path (traversal)
   */
  public static String storage(UUID tenantId, String... parts) {
    requireTenant(tenantId);
    return "tenants/" + tenantId + "/" + join(parts, "/", true);
  }

  private static void requireTenant(UUID tenantId) {
    if (tenantId == null) {
      throw new IllegalArgumentException(
          "tenantId must not be null - take it from TenantContext, never from a request");
    }
  }

  private static String join(String[] parts, String separator, boolean storageRules) {
    if (parts == null || parts.length == 0) {
      throw new IllegalArgumentException("at least one key part is required");
    }
    StringBuilder key = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      String part = parts[i];
      if (part == null || part.isBlank()) {
        throw new IllegalArgumentException("key parts must not be null or blank");
      }
      if (storageRules && (part.contains("..") || part.contains("/") || part.contains("\\"))) {
        throw new IllegalArgumentException(
            "storage key parts must not contain '..', '/' or '\\': " + part);
      }
      if (i > 0) {
        key.append(separator);
      }
      key.append(part);
    }
    return key.toString();
  }
}
