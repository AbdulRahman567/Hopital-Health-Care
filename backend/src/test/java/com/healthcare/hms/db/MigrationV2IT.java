package com.healthcare.hms.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** P3.3 — {@code V2__roles_permissions_and_seed.sql}: RBAC tables plus the permission seed. */
class MigrationV2IT {

  /** Exact size of the catalog frozen by decision D3 (plan section 5.4). */
  private static final int CATALOG_SIZE = 53;

  private static MigrationTestSupport db;
  private static UUID tenantA;
  private static UUID tenantB;
  private static UUID userInTenantA;
  private static UUID roleInTenantA;
  private static UUID roleInTenantB;

  @BeforeAll
  static void migrateFromEmptyDatabase() {
    db = MigrationTestSupport.withEmptyDatabase();
    db.migrate();
    tenantA = db.insertTenant("v2-tenant-a");
    tenantB = db.insertTenant("v2-tenant-b");
    userInTenantA = db.insertUser(tenantA, "admin@tenant-a.example.com");
    roleInTenantA = db.insertRole(tenantA, "Tenant A admin");
    roleInTenantB = db.insertRole(tenantB, "Tenant B admin");
  }

  @AfterAll
  static void dropSchema() {
    if (db != null) {
      db.close();
    }
  }

  @Test
  void v2WasAppliedSuccessfullyOnAnEmptyDatabase() {
    assertThat(db.migrationSucceeded("2"))
        .as("V2 must apply cleanly when Flyway starts from an empty schema")
        .isTrue();
  }

  @Test
  void rbacTablesExist() {
    assertThat(db.tableExists("permissions")).isTrue();
    assertThat(db.tableExists("roles")).isTrue();
    assertThat(db.tableExists("role_permissions")).isTrue();
    assertThat(db.tableExists("user_roles")).isTrue();
    assertThat(db.columnIsNullable("role_permissions", "tenant_id")).isFalse();
  }

  @Test
  void permissionCatalogIsSeeded() {
    int rows = db.count("SELECT COUNT(*) FROM permissions");

    assertThat(rows).as("ROADMAP P3.3: seeded permission rows > 0").isGreaterThan(0);
    assertThat(rows).as("catalog frozen by decision D3").isEqualTo(CATALOG_SIZE);
    assertThat(
            db.count(
                "SELECT COUNT(*) FROM permissions WHERE SUBSTRING_INDEX(code, '_', 1) <> `module`"))
        .as("every code must start with its module (MODULE_ACTION)")
        .isZero();
    assertThat(
            db.count(
                "SELECT COUNT(*) FROM permissions"
                    + " WHERE SUBSTRING(code, LENGTH(`module`) + 2) <> `action`"))
        .as("every code must end with its action (MODULE_ACTION)")
        .isZero();
    assertThat(db.count("SELECT COUNT(*) FROM permissions WHERE description IS NULL"))
        .as("every permission carries a human readable description")
        .isZero();
  }

  @Test
  void fieldMaskingPermissionsReferencedByTddArePresent() {
    int count =
        db.count(
            "SELECT COUNT(*) FROM permissions"
                + " WHERE code IN ('PATIENT_VIEW_DIAGNOSIS', 'PATIENT_VIEW_NOTES')");
    assertThat(count).isEqualTo(2);
  }

  @Test
  void moduleAndActionAreUniqueTogether() {
    int duplicates =
        db.count(
            "SELECT COUNT(*) FROM (SELECT `module`, `action` FROM permissions"
                + " GROUP BY `module`, `action` HAVING COUNT(*) > 1) duplicated");
    assertThat(duplicates).isZero();
  }

  @Test
  void duplicateRolePermissionIsRejected() {
    insertRolePermission(tenantA, roleInTenantA, "PATIENT_VIEW");

    assertThatThrownBy(() -> insertRolePermission(tenantA, roleInTenantA, "PATIENT_VIEW"))
        .as("the (tenant_id, role_id, permission_code) key must reject duplicates")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("Duplicate entry")
        .hasMessageContaining("role_permissions");
  }

  @Test
  void compositeForeignKeyRejectsARoleBelongingToAnotherTenant() {
    // roleInTenantA lives in tenant A: writing it under tenant B must fail even though the
    // role_id alone would match (CONF-5 composite FKs carry tenant_id).
    assertThatThrownBy(() -> insertRolePermission(tenantB, roleInTenantA, "PATIENT_VIEW"))
        .as("fk_role_permissions_roles must reject a cross-tenant (tenant_id, role_id) pair")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("fk_role_permissions_roles");
  }

  @Test
  void userRolesRejectAUserFromAnotherTenant() {
    assertThatThrownBy(() -> insertUserRole(tenantB, userInTenantA, roleInTenantB))
        .as("fk_user_roles_users must reject a user whose tenant differs from the row")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("fk_user_roles_users");
  }

  @Test
  void userRolesRejectARoleFromAnotherTenant() {
    assertThatThrownBy(() -> insertUserRole(tenantA, userInTenantA, roleInTenantB))
        .as("fk_user_roles_roles must reject a role whose tenant differs from the row")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("fk_user_roles_roles");
  }

  @Test
  void supportingParentKeysForTheCompositeForeignKeysExist() {
    assertThat(constraintExists("users", "uq_users_tenant_id")).isTrue();
    assertThat(constraintExists("roles", "uq_roles_tenant_id")).isTrue();
  }

  @Test
  void userRolesRoundTripWithinOneTenantWorks() {
    insertUserRole(tenantA, userInTenantA, roleInTenantA);

    int count =
        db.count(
            "SELECT COUNT(*) FROM user_roles WHERE tenant_id = ? AND user_id = ? AND role_id = ?",
            MigrationTestSupport.uuidBytes(tenantA),
            MigrationTestSupport.uuidBytes(userInTenantA),
            MigrationTestSupport.uuidBytes(roleInTenantA));
    assertThat(count).isEqualTo(1);
  }

  private void insertRolePermission(UUID tenantId, UUID roleId, String permissionCode) {
    db.jdbc()
        .update(
            "INSERT INTO role_permissions (tenant_id, role_id, permission_code) VALUES (?, ?, ?)",
            MigrationTestSupport.uuidBytes(tenantId),
            MigrationTestSupport.uuidBytes(roleId),
            permissionCode);
  }

  private void insertUserRole(UUID tenantId, UUID userId, UUID roleId) {
    db.jdbc()
        .update(
            "INSERT INTO user_roles (tenant_id, user_id, role_id, granted_at) VALUES (?, ?, ?,"
                + " ?)",
            MigrationTestSupport.uuidBytes(tenantId),
            MigrationTestSupport.uuidBytes(userId),
            MigrationTestSupport.uuidBytes(roleId),
            MigrationTestSupport.now());
  }

  private boolean constraintExists(String table, String constraint) {
    return db.count(
            "SELECT COUNT(*) FROM information_schema.table_constraints"
                + " WHERE constraint_schema = ? AND table_name = ? AND constraint_name = ?",
            db.schema(),
            table,
            constraint)
        > 0;
  }
}
