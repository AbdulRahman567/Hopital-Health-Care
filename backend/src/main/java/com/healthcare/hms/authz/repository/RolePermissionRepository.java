package com.healthcare.hms.authz.repository;

import com.healthcare.hms.authz.RolePermission;
import com.healthcare.hms.authz.RolePermissionId;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads and writes of {@code role_permissions}, the table the whole authorization decision rests on
 * (decision D1).
 *
 * <p><b>Every statement names {@code tenant_id}.</b> The association table carries no
 * {@code @TenantId} &mdash; its composite primary key <i>is</i> the discriminator &mdash; so
 * scoping is explicit here and never inferred. The {@code @Query} forms (rather than derived
 * queries) keep that predicate visible in one place, and {@code EndpointPermissionArchUnitTest}
 * rules over what this package is allowed to reach.
 */
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

  @Query(
      "select rp.id.permissionCode from RolePermission rp"
          + " where rp.id.tenantId = :tenantId and rp.id.roleId = :roleId")
  Set<String> permissionCodesForRole(
      @Param("tenantId") UUID tenantId, @Param("roleId") UUID roleId);

  @Query(
      "select rp from RolePermission rp"
          + " where rp.id.tenantId = :tenantId and rp.id.roleId = :roleId"
          + " order by rp.id.permissionCode")
  List<RolePermission> findByRole(@Param("tenantId") UUID tenantId, @Param("roleId") UUID roleId);

  @Query(
      "select rp.id.permissionCode from RolePermission rp"
          + " where rp.id.tenantId = :tenantId and rp.id.roleId in :roleIds")
  Set<String> permissionCodesForRoles(
      @Param("tenantId") UUID tenantId, @Param("roleIds") Collection<UUID> roleIds);

  @Query(
      "select rp from RolePermission rp"
          + " where rp.id.tenantId = :tenantId and rp.id.roleId in :roleIds")
  List<RolePermission> findByRoles(
      @Param("tenantId") UUID tenantId, @Param("roleIds") Collection<UUID> roleIds);

  @Query(
      "select count(rp) from RolePermission rp"
          + " where rp.id.tenantId = :tenantId and rp.id.roleId = :roleId")
  long countForRole(@Param("tenantId") UUID tenantId, @Param("roleId") UUID roleId);

  @Modifying
  @Query(
      "delete from RolePermission rp where rp.id.tenantId = :tenantId and rp.id.roleId = :roleId")
  int deleteForRole(@Param("tenantId") UUID tenantId, @Param("roleId") UUID roleId);
}
