package com.healthcare.hms.auth;

import com.healthcare.hms.auth.repository.UserRepository;
import com.healthcare.hms.authz.Role;
import com.healthcare.hms.authz.SystemRoleProvisioner;
import com.healthcare.hms.authz.UserRole;
import com.healthcare.hms.authz.UserRoleKey;
import com.healthcare.hms.authz.repository.RoleRepository;
import com.healthcare.hms.authz.repository.UserRoleRepository;
import com.healthcare.hms.tenant.TenantContext;
import com.healthcare.hms.tenant.TenantStatus;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Test-only builders for the rows a login test needs (TESTING.md section 10: synthetic data only).
 *
 * <p>A component rather than a static helper because the hash must come from the <b>same</b> {@link
 * PasswordEncoder} bean production uses: {@code UserFixtures}' labelled fake hash never verifies,
 * and a fixture that silently disagreed with the configured encoder would make every "success" case
 * pass for the wrong reason.
 *
 * <p>Tenants are written with {@code JdbcTemplate} for the same reason production registration is
 * (plan P5.2 / decision D1): {@code @UuidGenerator} rejects a pre-assigned id, so a fixture cannot
 * hand {@code TenantRepository} a row it has already keyed.
 */
@Component
public class AuthFixtures {

  private static final String INSERT_TENANT =
      "INSERT INTO tenants (id, name, slug, status, timezone, created_at, updated_at, version)"
          + " VALUES (?, ?, ?, ?, 'UTC', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)";

  private final PasswordEncoder passwordEncoder;
  private final UserRepository userRepository;
  private final JdbcTemplate jdbcTemplate;
  private final SystemRoleProvisioner systemRoleProvisioner;
  private final RoleRepository roleRepository;
  private final UserRoleRepository userRoleRepository;

  public AuthFixtures(
      PasswordEncoder passwordEncoder,
      UserRepository userRepository,
      JdbcTemplate jdbcTemplate,
      SystemRoleProvisioner systemRoleProvisioner,
      RoleRepository roleRepository,
      UserRoleRepository userRoleRepository) {
    this.passwordEncoder = passwordEncoder;
    this.userRepository = userRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.systemRoleProvisioner = systemRoleProvisioner;
    this.roleRepository = roleRepository;
    this.userRoleRepository = userRoleRepository;
  }

  /** A stored hash for {@code rawPassword}, produced exactly as registration produces one. */
  public String hash(String rawPassword) {
    return passwordEncoder.encode(rawPassword);
  }

  /**
   * Inserts a tenant registry row with an explicit lifecycle state.
   *
   * @param slug unique public slug; also used as the display name
   * @param status {@code PENDING} to model an unverified hospital, {@code ACTIVE} to model a
   *     verified one, {@code SUSPENDED} to model a platform-admin suspension
   * @return the new tenant's id
   */
  public UUID createTenant(String slug, TenantStatus status) {
    UUID tenantId = UUID.randomUUID();
    jdbcTemplate.update(INSERT_TENANT, uuidBytes(tenantId), slug, slug, status.name());
    return tenantId;
  }

  /** Creates an account whose stored hash is {@link #hash(String)} of {@code rawPassword}. */
  public User createUser(
      UUID tenantId, String email, String rawPassword, UserStatus status, boolean mfaEnabled) {
    return createUserWithHash(tenantId, email, hash(rawPassword), status, mfaEnabled);
  }

  /**
   * Creates an account with an already-stored hash — used to prove that {@link UserFixtures}'
   * fixture hash never verifies, which is why that class is never edited.
   */
  public User createUserWithHash(
      UUID tenantId, String email, String storedHash, UserStatus status, boolean mfaEnabled) {
    return TenantContext.call(
        tenantId,
        () -> {
          User user = new User();
          user.setEmail(email);
          user.setPasswordHash(storedHash);
          user.setFirstName("Fixture");
          user.setLastName("Login");
          user.setStatus(status);
          user.setMfaEnabled(mfaEnabled);
          return userRepository.save(user);
        });
  }

  /**
   * Provisions a tenant's six system bundles and enrols {@code adminUserId} in {@code ADMIN}
   * (decision D4) — the same call registration makes, available to suites whose tenant was inserted
   * directly rather than registered over HTTP. Idempotent, so it may be called on every test.
   *
   * <p>Additive in Phase 6: no method above changed its signature or what it returns (D10).
   */
  public void provisionRoles(UUID tenantId, UUID adminUserId) {
    systemRoleProvisioner.provision(tenantId, adminUserId);
  }

  /**
   * The six bundles without enrolling anybody — for suites that need a tenant's catalog to exist
   * but name the enrolments themselves. See {@link #createUserWithRoles} for why this is not the
   * same call as {@link #provisionRoles}.
   */
  public void provisionBundles(UUID tenantId) {
    systemRoleProvisioner.provisionBundles(tenantId);
  }

  /**
   * An account holding exactly {@code roleNames}: bundles provisioned first (so every role named
   * below exists), then the enrolments written on top.
   *
   * <p>Deliberately <b>not</b> routed through {@link #provisionRoles}: that overload enrols its
   * argument in {@code ADMIN}, which is registration's meaning of "provision". Routing a fixture
   * account through it would hand every one of them the administrator's full 51-code grant and make
   * the authority assertions in Phase 6's matrix test pass for the wrong reason.
   *
   * <p>Phase 6's matrix tests need a signed-in caller whose authority is spelled out at the fixture
   * rather than inferred from {@code ADMIN}; building it here keeps those tests free of repository
   * wiring of their own.
   */
  public User createUserWithRoles(
      UUID tenantId,
      String email,
      String rawPassword,
      UserStatus status,
      boolean mfaEnabled,
      Collection<String> roleNames) {
    User user = createUser(tenantId, email, rawPassword, status, mfaEnabled);
    systemRoleProvisioner.provisionBundles(tenantId);
    grantRoles(tenantId, user.getId(), roleNames);
    return user;
  }

  /** Grants named roles to an existing account; a role it already holds is left alone. */
  public void grantRoles(UUID tenantId, UUID userId, Collection<String> roleNames) {
    TenantContext.run(
        tenantId,
        () ->
            roleNames.forEach(
                name -> {
                  Role role =
                      roleRepository
                          .findByName(name)
                          .orElseThrow(
                              () ->
                                  new IllegalArgumentException(
                                      "No role named \"" + name + "\" in tenant " + tenantId));
                  if (userRoleRepository.existsById(
                      new UserRoleKey(tenantId, userId, role.getId()))) {
                    return;
                  }
                  userRoleRepository.save(UserRole.grant(tenantId, userId, role.getId()));
                }));
  }

  /**
   * Removes every tenant whose slug starts with {@code prefix}, together with the accounts and
   * tokens hanging off it.
   *
   * <p>The database is JVM-scoped and shared with every other suite (TESTING section 4), so a
   * fixture that outlived its test would eventually collide with a later one.
   *
   * <p>Phase 6 adds the three role tables in dependency order before the {@code users} delete. V2
   * declares no {@code ON DELETE CASCADE} on {@code user_roles} / {@code role_permissions} / {@code
   * roles}, so any tenant provisioned by decision D4 would otherwise make this statement fail with
   * a foreign-key error rather than clean up. Purely additive: the deletes below ran
   * unconditionally in Phase 5 and still do.
   */
  public void deleteTenantsStartingWith(String prefix) {
    String like = prefix + "%";
    jdbcTemplate.update(
        "DELETE FROM verification_tokens WHERE tenant_id IN"
            + " (SELECT id FROM tenants WHERE slug LIKE ?)",
        like);
    jdbcTemplate.update(
        "DELETE FROM refresh_tokens WHERE tenant_id IN (SELECT id FROM tenants WHERE slug LIKE ?)",
        like);
    jdbcTemplate.update(
        "DELETE FROM user_roles WHERE tenant_id IN (SELECT id FROM tenants WHERE slug LIKE ?)",
        like);
    jdbcTemplate.update(
        "DELETE FROM role_permissions WHERE tenant_id IN (SELECT id FROM tenants WHERE slug LIKE ?)",
        like);
    jdbcTemplate.update(
        "DELETE FROM roles WHERE tenant_id IN (SELECT id FROM tenants WHERE slug LIKE ?)", like);
    jdbcTemplate.update(
        "DELETE FROM users WHERE tenant_id IN (SELECT id FROM tenants WHERE slug LIKE ?)", like);
    jdbcTemplate.update("DELETE FROM tenants WHERE slug LIKE ?", like);
  }

  private static byte[] uuidBytes(UUID id) {
    return ByteBuffer.allocate(16)
        .putLong(id.getMostSignificantBits())
        .putLong(id.getLeastSignificantBits())
        .array();
  }
}
