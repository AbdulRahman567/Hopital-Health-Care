package com.healthcare.hms.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
  void v1WasAppliedSuccessfullyOnAnEmptyDatabase() {
    assertThat(db.migrationSucceeded("1"))
        .as("V1 must apply cleanly when Flyway starts from an empty schema")
        .isTrue();
  }

  @Test
  void tenantsAndUsersTablesExist() {
    assertThat(db.tableExists("tenants")).isTrue();
    assertThat(db.tableExists("users")).isTrue();
    assertThat(db.columnIsNullable("users", "tenant_id")).isFalse();
  }

  @Test
  void platformTenantIsSeededWithTheReservedId() {
    int count =
        db.count(
            "SELECT COUNT(*) FROM tenants WHERE id = ? AND slug = 'platform' AND status = 'ACTIVE'",
            MigrationTestSupport.uuidBytes(PLATFORM_TENANT_ID));
    assertThat(count).isEqualTo(1);
  }

  @Test
  void duplicateEmailWithinTheSameTenantIsRejected() {
    UUID tenant = db.insertTenant("tenant-unique-a");
    db.insertUser(tenant, "duplicate@example.com");

    assertThatThrownBy(() -> db.insertUser(tenant, "duplicate@example.com"))
        .as("uq_users_tenant_email must reject a second row for the same (tenant_id, email)")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("uq_users_tenant_email");
  }

  @Test
  void theSameEmailMayExistInTwoDifferentTenants() {
    UUID tenantA = db.insertTenant("tenant-unique-b");
    UUID tenantB = db.insertTenant("tenant-unique-c");
    db.insertUser(tenantA, "shared@example.com");
    db.insertUser(tenantB, "shared@example.com");

    int count = db.count("SELECT COUNT(*) FROM users WHERE email = 'shared@example.com'");
    assertThat(count).isEqualTo(2);
  }

  @Test
  void unknownTenantStatusIsRejected() {
    assertThatThrownBy(() -> db.insertTenant("tenant-bad-status", "NOT_A_STATUS"))
        .as("chk_tenants_status must reject an unknown status")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("chk_tenants_status");
  }

  @Test
  void unknownUserStatusIsRejected() {
    assertThatThrownBy(() -> db.insertUser(PLATFORM_TENANT_ID, "bad-status@example.com", "BOGUS"))
        .as("chk_users_status must reject an unknown status")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("chk_users_status");
  }

  @Test
  void userWithUnknownTenantIsRejectedByTheForeignKey() {
    assertThatThrownBy(() -> db.insertUser(UUID.randomUUID(), "orphan@example.com"))
        .as("fk_users_tenants must reject a user whose tenant does not exist")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("fk_users_tenants");
  }
}
