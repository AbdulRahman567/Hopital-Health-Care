package com.healthcare.hms.authz.repository;

import com.healthcare.hms.authz.UserRole;
import com.healthcare.hms.authz.UserRoleKey;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads and writes of {@code user_roles} — the "user &rarr; roles" half of decision D1's lookup.
 *
 * <p>As with {@link RolePermissionRepository}, the composite key is the tenant discriminator, so
 * every statement states {@code tenant_id} explicitly. Nothing here can be called without a tenant.
 */
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleKey> {

  @Query(
      "select ur.id.roleId from UserRole ur"
          + " where ur.id.tenantId = :tenantId and ur.id.userId = :userId")
  Set<UUID> roleIdsForUser(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

  @Query(
      "select ur from UserRole ur"
          + " where ur.id.tenantId = :tenantId and ur.id.userId = :userId"
          + " order by ur.id.roleId")
  List<UserRole> findByUser(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

  @Query(
      "select ur from UserRole ur"
          + " where ur.id.tenantId = :tenantId and ur.id.roleId in :roleIds")
  List<UserRole> findByRoles(
      @Param("tenantId") UUID tenantId, @Param("roleIds") Collection<UUID> roleIds);

  @Query(
      "select count(ur) from UserRole ur"
          + " where ur.id.tenantId = :tenantId and ur.id.roleId = :roleId")
  long countForRole(@Param("tenantId") UUID tenantId, @Param("roleId") UUID roleId);

  @Query(
      "select ur.id.userId from UserRole ur"
          + " where ur.id.tenantId = :tenantId and ur.id.roleId = :roleId")
  Set<UUID> userIdsForRole(@Param("tenantId") UUID tenantId, @Param("roleId") UUID roleId);

  @Modifying
  @Query("delete from UserRole ur where ur.id.tenantId = :tenantId and ur.id.userId = :userId")
  int deleteForUser(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

  @Modifying
  @Query(
      "delete from UserRole ur where ur.id.tenantId = :tenantId and ur.id.userId = :userId"
          + " and ur.id.roleId = :roleId")
  int deleteGrant(
      @Param("tenantId") UUID tenantId, @Param("userId") UUID userId, @Param("roleId") UUID roleId);
}
