package com.healthcare.hms.authz;

import com.healthcare.hms.authz.api.CreateRoleRequest;
import com.healthcare.hms.authz.api.RoleResponse;
import com.healthcare.hms.authz.api.UpdateRoleRequest;
import com.healthcare.hms.authz.repository.RolePermissionRepository;
import com.healthcare.hms.authz.repository.RoleRepository;
import com.healthcare.hms.authz.repository.UserRoleRepository;
import com.healthcare.hms.common.api.FieldViolation;
import com.healthcare.hms.common.api.PageParams;
import com.healthcare.hms.common.exception.ConflictException;
import com.healthcare.hms.common.exception.ErrorCodes;
import com.healthcare.hms.common.exception.FieldValidationException;
import com.healthcare.hms.common.exception.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-scoped roles CRUD (FR-3.5, ROADMAP P6.2, decisions D4/D5).
 *
 * <p><b>Four rules that make role editing safe</b> (plan risk 7 &mdash; privilege escalation by
 * role editing is the headline threat of this phase):
 *
 * <ol>
 *   <li><b>Catalog only.</b> A code that is not in {@link PermissionCatalog} is 422 before any row
 *       is written, so a typo can never become a silent deny — and the FK to {@code permissions}
 *       stays satisfied.
 *   <li><b>Platform-only codes are refused on a tenant role.</b> {@code TENANT_SUSPEND} and {@code
 *       TENANT_ACTIVATE} operate on the tenant itself and are reserved for the platform tenant
 *       (P8.5 / GAP-1); granting one here would be a tenant suspending itself.
 *   <li><b>Grant scope.</b> A creator may only grant permissions <i>they themselves hold</i>, so a
 *       half-privileged custom role cannot be used as a ladder to the administrator's set. ADMIN
 *       holds 51 codes, which makes this workable rather than obstructive.
 *   <li><b>System roles are immutable.</b> Name, existence and permission set are fixed; only a
 *       custom role may be edited or removed, so a hospital cannot quietly hollow out the bundle
 *       its own administrators run on.
 * </ol>
 *
 * <p>Cross-tenant ids answer 404 and never 403 (ADR-006): {@code @TenantId} makes a foreign row
 * indistinguishable from a missing one, which is exactly the property {@code WrongTenantTest} at
 * P6.7 re-proves on this surface.
 */
@Service
public class RoleService {

  static final String UNKNOWN_PERMISSION = "Unknown permission code.";
  static final String PLATFORM_ONLY_PERMISSION =
      "This permission cannot be granted to a hospital role.";
  static final String BEYOND_GRANTER = "You cannot grant a permission you do not hold.";
  static final String SYSTEM_ROLE_IMMUTABLE = "System roles are immutable.";
  static final String ROLE_IN_USE = "This role is still assigned to one or more users.";

  private final RoleRepository roleRepository;
  private final RolePermissionRepository rolePermissionRepository;
  private final UserRoleRepository userRoleRepository;
  private final PermissionResolver permissionResolver;

  public RoleService(
      RoleRepository roleRepository,
      RolePermissionRepository rolePermissionRepository,
      UserRoleRepository userRoleRepository,
      PermissionResolver permissionResolver) {
    this.roleRepository = roleRepository;
    this.rolePermissionRepository = rolePermissionRepository;
    this.userRoleRepository = userRoleRepository;
    this.permissionResolver = permissionResolver;
  }

  /** The tenant's roles, name-ordered, each with the codes it grants. */
  @Transactional(readOnly = true)
  public Page<RoleResponse> list(PageParams params) {
    Pageable pageable = params.toPageRequest(Sort.unsorted());
    Page<Role> roles = roleRepository.findAllByOrderByNameAsc(pageable);
    Map<UUID, Set<String>> codes = codesOf(roles.getContent());
    return roles.map(role -> toResponse(role, codes.getOrDefault(role.getId(), Set.of())));
  }

  /** One role, or 404 — including a role that exists in another tenant. */
  @Transactional(readOnly = true)
  public RoleResponse get(UUID roleId) {
    Role role = findOr404(roleId);
    return toResponse(role, permissionResolver.rolePermissionCodes(tenantId(), roleId));
  }

  /** Creates a custom role from catalog codes. */
  @Transactional
  public RoleResponse create(CreateRoleRequest request) {
    UUID tenantId = tenantId();
    UUID actorId = CurrentActor.requireUserId();
    List<String> requested = normalize(request.permissionCodes());
    validateGrant(tenantId, actorId, requested);

    if (roleRepository.findByName(request.name()).isPresent()) {
      throw duplicateName();
    }

    Role saved;
    Set<String> granted;
    try {
      saved = roleRepository.save(Role.custom(request.name()));
      granted = replacePermissions(tenantId, saved.getId(), requested);
    } catch (DataIntegrityViolationException ex) {
      // uq_roles_tenant_name: two creations of the same name raced past the pre-check.
      throw duplicateName();
    }
    return toResponse(saved, granted);
  }

  /** Replaces name and permission set of a custom role. */
  @Transactional
  public RoleResponse update(UUID roleId, UpdateRoleRequest request) {
    UUID tenantId = tenantId();
    UUID actorId = CurrentActor.requireUserId();
    Role role = findOr404(roleId);
    rejectWhenSystem(role);

    List<String> requested = normalize(request.permissionCodes());
    validateGrant(tenantId, actorId, requested);

    if (!role.getName().equals(request.name())
        && roleRepository.findByName(request.name()).isPresent()) {
      throw duplicateName();
    }

    role.setName(request.name());
    Role saved;
    Set<String> granted;
    try {
      saved = roleRepository.save(role);
      granted = replacePermissions(tenantId, roleId, requested);
    } catch (DataIntegrityViolationException ex) {
      throw duplicateName();
    }
    return toResponse(saved, granted);
  }

  /** Deletes a custom role that nobody holds. */
  @Transactional
  public void delete(UUID roleId) {
    UUID tenantId = tenantId();
    Role role = findOr404(roleId);
    rejectWhenSystem(role);
    if (userRoleRepository.countForRole(tenantId, roleId) > 0) {
      throw new ConflictException(ErrorCodes.RESOURCE_IN_USE, ROLE_IN_USE);
    }
    rolePermissionRepository.deleteForRole(tenantId, roleId);
    roleRepository.delete(role);
  }

  // -------------------------------------------------------------------------
  // rules
  // -------------------------------------------------------------------------

  private void validateGrant(UUID tenantId, UUID actorId, List<String> requested) {
    Set<String> unknown =
        requested.stream()
            .filter(code -> !PermissionCatalog.contains(code))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    if (!unknown.isEmpty()) {
      throw violations(List.of(new FieldViolation("permissionCodes", UNKNOWN_PERMISSION)));
    }

    List<String> platformOnly =
        requested.stream()
            .map(PermissionCatalog::find)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(PermissionCatalog::isPlatformOnly)
            .map(PermissionCatalog::code)
            .toList();
    if (!platformOnly.isEmpty()) {
      throw violations(List.of(new FieldViolation("permissionCodes", PLATFORM_ONLY_PERMISSION)));
    }

    Set<String> held = permissionResolver.permissionCodes(tenantId, actorId);
    if (!held.containsAll(requested)) {
      throw violations(List.of(new FieldViolation("permissionCodes", BEYOND_GRANTER)));
    }
  }

  private static FieldValidationException violations(List<FieldViolation> fields) {
    return new FieldValidationException("One or more fields are invalid.", fields);
  }

  private void rejectWhenSystem(Role role) {
    if (role.isSystemFlag()) {
      throw new ConflictException(ErrorCodes.RECORD_FINALIZED, SYSTEM_ROLE_IMMUTABLE);
    }
  }

  private Role findOr404(UUID roleId) {
    return roleRepository
        .findById(roleId)
        .orElseThrow(() -> new NotFoundException("Role not found."));
  }

  private Set<String> replacePermissions(UUID tenantId, UUID roleId, List<String> requested) {
    rolePermissionRepository.deleteForRole(tenantId, roleId);
    Set<String> granted = new LinkedHashSet<>();
    for (String code : requested) {
      if (granted.add(code)) {
        rolePermissionRepository.save(RolePermission.of(tenantId, roleId, code));
      }
    }
    return granted;
  }

  /** De-duplicates while keeping the caller's order, and treats a null element as unknown. */
  private static List<String> normalize(List<String> codes) {
    List<String> normalized = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    if (codes != null) {
      for (String code : codes) {
        if (seen.add(code)) {
          normalized.add(code);
        }
      }
    }
    return normalized;
  }

  private static ConflictException duplicateName() {
    return new ConflictException(
        ErrorCodes.DUPLICATE_RESOURCE,
        "A role with this name already exists.",
        List.of(new FieldViolation("name", "A role with this name already exists.")));
  }

  private Map<UUID, Set<String>> codesOf(List<Role> roles) {
    Set<UUID> ids = roles.stream().map(Role::getId).collect(Collectors.toSet());
    return permissionResolver.permissionCodesByRole(tenantId(), ids);
  }

  private static UUID tenantId() {
    return CurrentActor.tenantId();
  }

  private static RoleResponse toResponse(Role role, Set<String> codes) {
    return new RoleResponse(
        role.getId().toString(),
        role.getName(),
        role.isSystemFlag(),
        codes.stream().sorted().toList(),
        role.getCreatedAt(),
        role.getUpdatedAt(),
        role.getVersion());
  }
}
