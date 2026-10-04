package com.healthcare.hms.authz;

import com.healthcare.hms.authz.repository.RolePermissionRepository;
import com.healthcare.hms.authz.repository.RoleRepository;
import com.healthcare.hms.authz.repository.UserRoleRepository;
import com.healthcare.hms.tenant.TenantContext;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes a tenant's system-role bundles and enrols its first administrator (decision <b>D4</b>,
 * ROADMAP P6.2).
 *
 * <p><b>Why this exists at all.</b> {@code user_roles} is empty for every tenant until something
 * fills it, and an empty grant is an empty authority set — so without provisioning every authorized
 * endpoint answers 403 to everybody, administrators included, and no part of Phase 6 can prove
 * anything. Provisioning is therefore on the critical path of the phase (plan risk 1).
 *
 * <p><b>Why in code, inside registration.</b> A migration cannot know the id of a tenant that does
 * not exist yet; lazy creation on first {@code GET /roles} would show an administrator an empty
 * list with no way to act; provisioning on first login would silently split one transaction's work
 * across two flows. {@code RegistrationService} already runs one transaction bound to the brand-new
 * tenant, and {@code auth &rarr; authz} is an edge ARCHITECTURE section 3 already permits — so the
 * provisioner is called there, after the administrator row exists.
 *
 * <p><b>Idempotent.</b> A bundle that already exists is reconciled rather than duplicated (its
 * permission set is re-read back to the bundle definition), and an enrolment that is already
 * present is left alone. Calling it twice changes nothing, which is what lets the test fixtures
 * call it directly for tenants created before this class existed.
 *
 * <p>Roles written here carry {@code system_flag = 1}: name immutable, not deletable, permission
 * set read-only through the API. Customization is FR-3.5's job.
 */
@Service
public class SystemRoleProvisioner {

  private static final Logger log = LoggerFactory.getLogger(SystemRoleProvisioner.class);

  private final RoleRepository roleRepository;
  private final RolePermissionRepository rolePermissionRepository;
  private final UserRoleRepository userRoleRepository;
  private final TransactionTemplate transactionTemplate;

  public SystemRoleProvisioner(
      RoleRepository roleRepository,
      RolePermissionRepository rolePermissionRepository,
      UserRoleRepository userRoleRepository,
      TransactionTemplate transactionTemplate) {
    this.roleRepository = roleRepository;
    this.rolePermissionRepository = rolePermissionRepository;
    this.userRoleRepository = userRoleRepository;
    this.transactionTemplate = transactionTemplate;
  }

  /**
   * Ensures the tenant has all six system bundles and that {@code adminUserId} holds {@code ADMIN}.
   *
   * <p>Safe to call from inside an existing transaction and an already-bound {@link TenantContext}
   * (registration does both); it also binds the context itself when the caller has not, which is
   * how the test fixtures use it.
   *
   * <p><b>Why {@link TransactionTemplate} rather than {@code @Transactional}.</b> Spring opens the
   * {@code EntityManager} when the interceptor <i>begins</i> the transaction, and the first thing
   * Hibernate does with a new session is ask {@link
   * com.healthcare.hms.tenant.TenantIdentifierResolver} for the tenant id — which fails closed when
   * nothing is bound. A {@code @Transactional} proxy would therefore start the transaction before
   * this method had a chance to bind anything, and every standalone call would die with {@code
   * CannotCreateTransactionException}. Binding first and starting second is the order registration
   * already uses; making it explicit here means both paths behave identically.
   *
   * @param tenantId tenant to provision
   * @param adminUserId the registering administrator to enrol in {@code ADMIN}
   */
  public void provision(UUID tenantId, UUID adminUserId) {
    TenantContext.run(
        tenantId,
        () ->
            transactionTemplate.executeWithoutResult(
                status -> {
                  provisionBundlesInTenant(tenantId);
                  enrolAdmin(tenantId, adminUserId);
                  log.info(
                      "System roles provisioned: tenantId={}, adminUserId={}, bundles={}",
                      tenantId,
                      adminUserId,
                      SystemRoleBundle.allRoleNames());
                }));
  }

  /**
   * The six bundles only — no enrolment, because the caller already knows which roles it wants.
   *
   * <p>{@link #provision} is registration's call: bundles <i>and</i> the first administrator.
   * {@code AuthFixtures.createUserWithRoles} is not registering anybody — it names the roles itself
   * — so routing it through {@code provision} would silently enrol every fixture account as {@code
   * ADMIN} and make every authority assertion in Phase 6 vacuous.
   *
   * @param tenantId tenant to provision
   */
  public void provisionBundles(UUID tenantId) {
    TenantContext.run(
        tenantId,
        () ->
            transactionTemplate.executeWithoutResult(status -> provisionBundlesInTenant(tenantId)));
  }

  private void provisionBundlesInTenant(UUID tenantId) {
    for (SystemRoleBundle bundle : SystemRoleBundle.values()) {
      Role role =
          roleRepository.findByName(bundle.roleName()).orElseGet(() -> createBundle(bundle));
      reconcilePermissions(tenantId, role, bundle.permissionCodes());
    }
  }

  /**
   * Inserts one bundle.
   *
   * <p>{@code tenant_id} is stamped by Hibernate from the bound {@link TenantContext}, never passed
   * in — the same way {@code RegistrationService} creates its administrator, and the reason this
   * method runs inside {@link TenantContext#run}.
   */
  private Role createBundle(SystemRoleBundle bundle) {
    return roleRepository.save(Role.system(bundle.roleName()));
  }

  /**
   * Makes the role's codes exactly the bundle's.
   *
   * <p>Skipped when they already match, which is the normal second call. When they differ only the
   * difference is written, so reconciliation is a no-op for an untouched system role and a repair
   * for one whose bundle definition moved with a code review.
   */
  private void reconcilePermissions(UUID tenantId, Role role, Set<String> wanted) {
    Set<String> current = permissionCodesOf(tenantId, role.getId());
    if (current.equals(wanted)) {
      return;
    }
    rolePermissionRepository.deleteForRole(tenantId, role.getId());
    for (String code : wanted) {
      rolePermissionRepository.save(RolePermission.of(tenantId, role.getId(), code));
    }
  }

  private Set<String> permissionCodesOf(UUID tenantId, UUID roleId) {
    return rolePermissionRepository.permissionCodesForRole(tenantId, roleId);
  }

  private void enrolAdmin(UUID tenantId, UUID adminUserId) {
    Set<UUID> held = userRoleRepository.roleIdsForUser(tenantId, adminUserId);
    Role admin =
        roleRepository
            .findByName(SystemRoleBundle.ADMIN.roleName())
            .orElseThrow(
                () -> new IllegalStateException("ADMIN bundle is missing after provisioning"));
    if (held.contains(admin.getId())) {
      return;
    }
    userRoleRepository.save(UserRole.grant(tenantId, adminUserId, admin.getId()));
  }

  /** The bundle names this provisioner owns, for documentation and assertions. */
  public static List<String> bundleNames() {
    return SystemRoleBundle.allRoleNames();
  }
}
