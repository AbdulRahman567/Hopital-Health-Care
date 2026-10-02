package com.healthcare.hms.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * Base for every tenant-owned row: adds the mandatory {@code tenant_id NOT NULL} column (DATABASE
 * section 1, ENGINEERING_RULES section 4/5.2).
 *
 * <p>{@code @TenantId} is applied here (P4.4, TQ-1 default): Hibernate adds {@code tenant_id = ?}
 * to every query, insert and update of a subclass such as {@code User}, using the identifier from
 * {@code TenantContext} through {@code TenantIdentifierResolver}. The database-level {@code NOT
 * NULL} guarantee from Phase 3 stays as the second layer of the same rule (TDD section 6.3).
 */
@MappedSuperclass
public abstract class TenantOwnedEntity extends BaseEntity {

  @TenantId
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
