package com.healthcare.hms.tenant;

import com.healthcare.hms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * Tenant registry row (TDD section 9.2). Platform table — it has no {@code tenant_id} (DATABASE
 * section 1) and is the only table that may be referenced by every other table's {@code tenant_id}.
 */
@Entity
@Table(
    name = "tenants",
    uniqueConstraints = @UniqueConstraint(name = "uq_tenants_slug", columnNames = "slug"))
public class Tenant extends BaseEntity {

  /**
   * Reserved platform tenant seeded by {@code V1__tenants_and_users.sql} (GAP-1 / decision D5).
   * Keep in sync with the migration.
   */
  public static final UUID PLATFORM_TENANT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");

  /** Slug of the reserved platform tenant; excluded from tenant listings (P8.5). */
  public static final String PLATFORM_TENANT_SLUG = "platform";

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "slug", nullable = false, length = 63)
  private String slug;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private TenantStatus status = TenantStatus.PENDING;

  @Column(name = "timezone", length = 64)
  private String timezone;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  protected Tenant() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSlug() {
    return slug;
  }

  public void setSlug(String slug) {
    this.slug = slug;
  }

  public TenantStatus getStatus() {
    return status;
  }

  public void setStatus(TenantStatus status) {
    this.status = status;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  public Instant getVerifiedAt() {
    return verifiedAt;
  }

  public void setVerifiedAt(Instant verifiedAt) {
    this.verifiedAt = verifiedAt;
  }
}
