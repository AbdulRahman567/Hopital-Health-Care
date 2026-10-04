package com.healthcare.hms.authz;

import com.healthcare.hms.authz.repository.RolePermissionRepository;
import com.healthcare.hms.authz.repository.RoleRepository;
import com.healthcare.hms.authz.repository.UserRoleRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Decision <b>D1</b> — where every authorization decision reads its data from: the database, per
 * request, resolved <i>after</i> tenant binding.
 *
 * <p>Two narrow statements, both against keys that lead with {@code tenant_id}:
 *
 * <ol>
 *   <li>{@code user_roles} by {@code (tenant_id, user_id)} &rarr; the role ids;
 *   <li>{@code role_permissions} by {@code (tenant_id, role_id IN ...)} &rarr; the permission
 *       codes.
 * </ol>
 *
 * <p><b>Fail closed, always.</b> A tenant with no rows for the user answers an empty set, and the
 * authorities stage turns that into an empty authority set &mdash; which is a 403, never "everyone
 * gets in". There is no default grant and no fallback to role <i>names</i>: role bundles are
 * tenant-editable, so a name would bypass the catalog the whole phase exists to enforce.
 *
 * <p><b>Why not the token.</b> Permission codes in the JWT would stay stale until the 12-minute
 * token is refreshed and a leaked one would carry the full privilege map. Reading the database
 * means a role edit takes effect on the <i>next request</i> with no re-issuance (TESTING section
 * 5's "as designed" branch), at the cost of two indexed lookups per request. Redis caching of this
 * result is sanctioned by TDD section 13 but deliberately deferred to P22.4 &mdash; a stale
 * permission is a security bug, not just a latency bug.
 */
@Service
public class PermissionResolver {

  private final UserRoleRepository userRoleRepository;
  private final RolePermissionRepository rolePermissionRepository;
  private final RoleRepository roleRepository;

  public PermissionResolver(
      UserRoleRepository userRoleRepository,
      RolePermissionRepository rolePermissionRepository,
      RoleRepository roleRepository) {
    this.userRoleRepository = userRoleRepository;
    this.rolePermissionRepository = rolePermissionRepository;
    this.roleRepository = roleRepository;
  }

  /**
   * The permission codes one account holds in one tenant.
   *
   * @return an unmodifiable, never-null set; empty means "no authority at all"
   */
  public Set<String> permissionCodes(UUID tenantId, UUID userId) {
    Set<UUID> roleIds = userRoleRepository.roleIdsForUser(tenantId, userId);
    if (roleIds.isEmpty()) {
      return Set.of();
    }
    return Set.copyOf(rolePermissionRepository.permissionCodesForRoles(tenantId, roleIds));
  }

  /**
   * The role <i>names</i> one account holds — display data only (decision D2: the JWT {@code roles}
   * claim and {@link com.healthcare.hms.auth.api.SessionProfile} exist so a sidebar and a header
   * can be rendered). Authorization never reads them.
   *
   * @return names ordered alphabetically for a stable claim
   */
  public List<String> roleNames(UUID tenantId, UUID userId) {
    Set<UUID> roleIds = userRoleRepository.roleIdsForUser(tenantId, userId);
    if (roleIds.isEmpty()) {
      return List.of();
    }
    return roleRepository.findAllById(roleIds).stream()
        .map(Role::getName)
        .sorted(Comparator.naturalOrder())
        .toList();
  }

  /**
   * The codes each of the given roles holds, keyed by role id and sorted inside each value.
   *
   * <p>One statement for the whole page rather than one per role, and the tenant predicate stays
   * visible in the SQL because this is the most security-sensitive table in the schema.
   */
  public Map<UUID, Set<String>> permissionCodesByRole(UUID tenantId, Collection<UUID> roleIds) {
    Map<UUID, Set<String>> byRole = new LinkedHashMap<>();
    for (UUID roleId : roleIds) {
      byRole.put(roleId, new TreeSet<>());
    }
    if (roleIds.isEmpty()) {
      return byRole;
    }
    for (RolePermission row : rolePermissionRepository.findByRoles(tenantId, roleIds)) {
      byRole.computeIfAbsent(row.getRoleId(), key -> new TreeSet<>()).add(row.getPermissionCode());
    }
    return byRole;
  }

  /** The codes one role holds. */
  public Set<String> rolePermissionCodes(UUID tenantId, UUID roleId) {
    return permissionCodesByRole(tenantId, List.of(roleId)).getOrDefault(roleId, Set.of());
  }
}
