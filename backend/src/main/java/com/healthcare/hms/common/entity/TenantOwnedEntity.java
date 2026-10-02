package com.healthcare.hms.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;

/**
 * Base for every tenant-owned row: adds the mandatory {@code tenant_id NOT NULL} column (DATABASE
 * section 1, ENGINEERING_RULES section 4/5.2).
 *
 * <p>Hibernate query filtering via {@code @TenantId} is deliberately **not** here yet — it arrives
 * with P4.4 together with {@code TenantContext}; Phase 3 only proves the database-level guarantee.
 */
@MappedSuperclass
public abstract class TenantOwnedEntity extends BaseEntity {

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  protected TenantOwnedEntity() {}

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }
}
