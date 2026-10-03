package com.healthcare.hms.auth;

import com.healthcare.hms.auth.repository.UserRepository;
import com.healthcare.hms.tenant.TenantContext;
import com.healthcare.hms.tenant.TenantStatus;
import java.nio.ByteBuffer;
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

  public AuthFixtures(
      PasswordEncoder passwordEncoder, UserRepository userRepository, JdbcTemplate jdbcTemplate) {
    this.passwordEncoder = passwordEncoder;
    this.userRepository = userRepository;
    this.jdbcTemplate = jdbcTemplate;
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
   * Removes every tenant whose slug starts with {@code prefix}, together with the accounts and
   * tokens hanging off it.
   *
   * <p>The database is JVM-scoped and shared with every other suite (TESTING section 4), so a
   * fixture that outlived its test would eventually collide with a later one.
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
