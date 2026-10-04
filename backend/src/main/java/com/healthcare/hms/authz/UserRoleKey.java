package com.healthcare.hms.authz;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Primary key of {@code user_roles}: {@code (tenant_id, user_id, role_id)} — the composite PK V2
 * declares, leading with {@code tenant_id} (CONF-5).
 *
 * <p>Same reasoning as {@link RolePermissionId}: the key <i>is</i> the discriminator, so the tenant
 * id is written once and every statement names it explicitly instead of relying on
 * {@code @TenantId} for a column that is already part of the identifier.
 */
@Embeddable
public class UserRoleKey implements Serializable {

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "role_id", nullable = false)
  private UUID roleId;

  protected UserRoleKey() {}

  public UserRoleKey(UUID tenantId, UUID userId, UUID roleId) {
    this.tenantId = tenantId;
    this.userId = userId;
    this.roleId = roleId;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getRoleId() {
    return roleId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof UserRoleKey that)) {
      return false;
    }
    return Objects.equals(tenantId, that.tenantId)
        && Objects.equals(userId, that.userId)
        && Objects.equals(roleId, that.roleId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tenantId, userId, roleId);
  }
}
