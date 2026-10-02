package com.healthcare.hms.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** P3.2 — {@code V1__tenants_and_users.sql} on an empty database. */
class MigrationV1IT {

  private static final UUID PLATFORM_TENANT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");

  private static MigrationTestSupport db;

  @BeforeAll
  static void migrateFromEmptyDatabase() {
    db = MigrationTestSupport.withEmptyDatabase();
    db.migrate();
  }

  @AfterAll
  static void dropSchema() {
    if (db != null) {
      db.close();
    }
  }

  @Test
  void flywayIsAtV1() {
    assertThat(db.currentVersion()).isEqualTo("1");
  }

  @Test
  void tenantsAndUsersTablesExist() {
    assertThat(tableExists("tenants")).isTrue();
    assertThat(tableExists("users")).isTrue();
    assertThat(columnIsNullable("users", "tenant_id")).isFalse();
  }

  @Test
  void platformTenantIsSeededWithTheReservedId() {
    Integer count =
        db.jdbc()
            .queryForObject(
                "SELECT COUNT(*) FROM tenants WHERE id = ? AND slug = 'platform' AND status = 'ACTIVE'",
                Integer.class,
                uuidBytes(PLATFORM_TENANT_ID));
    assertThat(count).isEqualTo(1);
  }

  @Test
  void duplicateEmailWithinTheSameTenantIsRejected() {
    UUID tenant = insertTenant("tenant-unique-a");
    insertUser(tenant, "duplicate@example.com", UserStatusRow.ACTIVE);

    assertThatThrownBy(() -> insertUser(tenant, "duplicate@example.com", UserStatusRow.ACTIVE))
        .as("uq_users_tenant_email must reject a second row for the same (tenant_id, email)")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("uq_users_tenant_email");
  }

  @Test
  void theSameEmailMayExistInTwoDifferentTenants() {
    UUID tenantA = insertTenant("tenant-unique-b");
    UUID tenantB = insertTenant("tenant-unique-c");
    insertUser(tenantA, "shared@example.com", UserStatusRow.ACTIVE);
    insertUser(tenantB, "shared@example.com", UserStatusRow.ACTIVE);

    Integer count =
        db.jdbc()
            .queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = 'shared@example.com'", Integer.class);
    assertThat(count).isEqualTo(2);
  }

  @Test
  void unknownTenantStatusIsRejected() {
    assertThatThrownBy(() -> insertTenant("tenant-bad-status", "NOT_A_STATUS"))
        .as("chk_tenants_status must reject an unknown status")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("chk_tenants_status");
  }

  @Test
  void unknownUserStatusIsRejected() {
    assertThatThrownBy(
            () -> insertUser(PLATFORM_TENANT_ID, "bad-status@example.com", UserStatusRow.BOGUS))
        .as("chk_users_status must reject an unknown status")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("chk_users_status");
  }

  @Test
  void userWithUnknownTenantIsRejectedByTheForeignKey() {
    assertThatThrownBy(
            () -> insertUser(UUID.randomUUID(), "orphan@example.com", UserStatusRow.ACTIVE))
        .as("fk_users_tenants must reject a user whose tenant does not exist")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("fk_users_tenants");
  }

  private boolean tableExists(String table) {
    Integer count =
        db.jdbc()
            .queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?",
                Integer.class,
                db.schema(),
                table);
    return count != null && count > 0;
  }

  private boolean columnIsNullable(String table, String column) {
    String nullable =
        db.jdbc()
            .queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                String.class,
                db.schema(),
                table,
                column);
    return "YES".equalsIgnoreCase(nullable);
  }

  private UUID insertTenant(String slug) {
    return insertTenant(slug, "ACTIVE");
  }

  private UUID insertTenant(String slug, String status) {
    UUID id = UUID.randomUUID();
    db.jdbc()
        .update(
            "INSERT INTO tenants (id, name, slug, status, timezone, verified_at, created_at,"
                + " created_by, updated_at, updated_by, version)"
                + " VALUES (?, ?, ?, ?, 'UTC', NULL, ?, NULL, ?, NULL, 0)",
            uuidBytes(id),
            slug.replace('-', ' '),
            slug,
            status,
            now(),
            now());
    return id;
  }

  private void insertUser(UUID tenantId, String email, UserStatusRow status) {
    db.jdbc()
        .update(
            "INSERT INTO users (id, tenant_id, email, password_hash, first_name, last_name, status,"
                + " failed_attempts, locked_until, mfa_enabled, mfa_secret, created_at, created_by,"
                + " updated_at, updated_by, version)"
                + " VALUES (?, ?, ?, '{noop-not-a-real-hash', 'Test', 'User', ?, 0, NULL, 0, NULL,"
                + " ?, NULL, ?, NULL, 0)",
            uuidBytes(UUID.randomUUID()),
            uuidBytes(tenantId),
            email,
            status.name(),
            now(),
            now());
  }

  private static Timestamp now() {
    return Timestamp.from(Instant.now());
  }

  private static byte[] uuidBytes(UUID uuid) {
    return ByteBuffer.allocate(16)
        .putLong(uuid.getMostSignificantBits())
        .putLong(uuid.getLeastSignificantBits())
        .array();
  }

  /** Local stand-in so the CHECK rejection test can insert a value outside the enum. */
  private enum UserStatusRow {
    ACTIVE,
    BOGUS
  }
}
