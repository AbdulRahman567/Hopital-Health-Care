package com.healthcare.hms.authz;

import com.healthcare.hms.common.entity.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A tenant's role: a named bundle of permission codes (TDD section 8.1, V2 {@code roles}).
 *
 * <p>{@code tenant_id} comes from {@link TenantOwnedEntity}, so Hibernate scopes every read, write
 * and delete of this entity to the bound {@code TenantContext} and a foreign id simply resolves to
 * {@code Optional.empty()} &mdash; which is what makes a cross-tenant {@code roleId} answer 404
 * rather than 403 (ADR-006).
 *
 * <p>{@code systemFlag} marks the bundles {@link SystemRoleProvisioner} creates per tenant
 * (decision D4). A system role's name, existence and permission set are immutable; only a custom
 * role created through {@code POST /api/v1/roles} may be edited or deleted. Customization is what
 * FR-3.5 exists for.
 */
@Entity
@Table(
    name = "roles",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_roles_tenant_name",
            columnNames = {"tenant_id", "name"}))
public class Role extends TenantOwnedEntity {

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "system_flag", nullable = false)
  private boolean systemFlag;

  protected Role() {}

  public static Role custom(String name) {
    Role role = new Role();
    role.name = name;
    role.systemFlag = false;
    return role;
  }

  public static Role system(String name) {
    Role role = new Role();
    role.name = name;
    role.systemFlag = true;
    return role;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isSystemFlag() {
    return systemFlag;
  }

  public void setSystemFlag(boolean systemFlag) {
    this.systemFlag = systemFlag;
  }
}
