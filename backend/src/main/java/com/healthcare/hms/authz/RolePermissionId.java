package com.healthcare.hms.authz;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Primary key of {@code role_permissions}: {@code (tenant_id, role_id, permission_code)} — the
 * composite PK V2 declares, which leads with {@code tenant_id} so a cross-tenant {@code (tenant_id,
 * role_id)} pair cannot be written (CONF-5).
 *
 * <p><b>Why the tenant id lives in the key rather than in {@code @TenantId}.</b> The association
 * table's key already <i>is</i> the tenant discriminator; adding the annotation on top would make
 * Hibernate write {@code tenant_id} twice for one column and fight this mapping. Scoping is
 * therefore explicit: every repository method takes the tenant first and the generated SQL always
 * carries {@code tenant_id = ?} (plan risk 3, ISO-1's "prove the SQL is scoped" rule).
 */
@Embeddable
public class RolePermissionId implements Serializable {

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "role_id", nullable = false)
  private UUID roleId;

  @Column(name = "permission_code", nullable = false, length = 64)
  private String permissionCode;

  protected RolePermissionId() {}

  public RolePermissionId(UUID tenantId, UUID roleId, String permissionCode) {
    this.tenantId = tenantId;
    this.roleId = roleId;
    this.permissionCode = permissionCode;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getRoleId() {
    return roleId;
  }

  public String getPermissionCode() {
    return permissionCode;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof RolePermissionId that)) {
      return false;
    }
    return Objects.equals(tenantId, that.tenantId)
        && Objects.equals(roleId, that.roleId)
        && Objects.equals(permissionCode, that.permissionCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tenantId, roleId, permissionCode);
  }
}
