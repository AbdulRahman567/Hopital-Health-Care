package com.healthcare.hms.authz;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One row of {@code role_permissions}: "this tenant's role holds this permission code" (V2, CONF-5
 * composite foreign keys).
 *
 * <p>A pure association table: no audit columns and no optimistic-lock version, exactly as V2
 * declares them (ENGINEERING_RULES section 5.6). Its tenant discriminator lives inside {@link
 * RolePermissionId}, so no query can be written without naming one.
 */
@Entity
@Table(name = "role_permissions")
public class RolePermission {

  @EmbeddedId private RolePermissionId id;

  protected RolePermission() {}

  public RolePermission(RolePermissionId id) {
    this.id = id;
  }

  /** Builds a row for the given tenant, role and catalog code. */
  public static RolePermission of(UUID tenantId, UUID roleId, String code) {
    return new RolePermission(new RolePermissionId(tenantId, roleId, code));
  }

  public RolePermissionId getId() {
    return id;
  }

  public UUID getTenantId() {
    return id.getTenantId();
  }

  public UUID getRoleId() {
    return id.getRoleId();
  }

  public String getPermissionCode() {
    return id.getPermissionCode();
  }
}
