package com.healthcare.hms.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * P3.6 — DB-layer proof that a {@code tenant_id = NULL} row can never be written (plan section 5,
 * rule from TDD section 6.3 "Database").
 *
 * <p>Full cross-tenant isolation testing arrives with Phase 4; this suite only proves the
 * constraint layer rejects NULL, which is the one guarantee that must hold before any row exists.
 */
class TenantIdNotNullIT {

  /**
   * Every tenant-scoped table created by V1-V4 (platform tables `tenants`/`permissions` excluded).
   */
  private static final List<String> TENANT_TABLES =
      List.of(
          "users",
          "roles",
          "role_permissions",
          "user_roles",
          "audit_logs",
          "refresh_tokens",
          "verification_tokens");

  private static MigrationTestSupport db;
  private static UUID tenant;
  private static UUID user;
  private static UUID role;

  @BeforeAll
  static void migrateFromEmptyDatabase() {
    db = MigrationTestSupport.withEmptyDatabase();
    db.migrate();
    tenant = db.insertTenant("notnull-tenant");
    user = db.insertUser(tenant, "member@notnull-tenant.example.com");
    role = db.insertRole(tenant, "Tenant role");
  }

  @AfterAll
  static void dropSchema() {
    if (db != null) {
      db.close();
    }
  }

  @Test
  void everyTenantIdColumnIsNotNullInInformationSchema() {
    for (String table : TENANT_TABLES) {
      assertThat(db.columnExists(table, "tenant_id"))
          .as("%s must have a tenant_id column", table)
          .isTrue();
      assertThat(db.columnIsNullable(table, "tenant_id"))
          .as("%s.tenant_id must be IS_NULLABLE = 'NO'", table)
          .isFalse();
    }
  }

  @Test
  void usersRejectNullTenantId() {
    assertThatThrownBy(() -> insert("users", tenantColumnValue(null)))
        .as("users.tenant_id is NOT NULL")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("tenant_id");
  }

  @Test
  void rolesRejectNullTenantId() {
    assertThatThrownBy(() -> insert("roles", tenantColumnValue(null)))
        .as("roles.tenant_id is NOT NULL")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("tenant_id");
  }

  @Test
  void rolePermissionsRejectNullTenantId() {
    assertThatThrownBy(() -> insert("role_permissions", tenantColumnValue(null)))
        .as("role_permissions.tenant_id is part of the primary key")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("tenant_id");
  }

  @Test
  void userRolesRejectNullTenantId() {
    assertThatThrownBy(() -> insert("user_roles", tenantColumnValue(null)))
        .as("user_roles.tenant_id is part of the primary key")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("tenant_id");
  }

  @Test
  void auditLogsRejectNullTenantId() {
    assertThatThrownBy(() -> insert("audit_logs", tenantColumnValue(null)))
        .as("audit_logs.tenant_id is NOT NULL")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("tenant_id");
  }

  @Test
  void rowsWithARealTenantStillInsert() {
    // Positive control: without it the NULL cases could pass for an unrelated reason (syntax
    // error, missing table) instead of the tenant_id constraint.
    insert("roles", tenantColumnValue(tenant));

    assertThat(
            db.count(
                "SELECT COUNT(*) FROM roles WHERE tenant_id = ?",
                MigrationTestSupport.uuidBytes(tenant)))
        .as("one role from setup plus the control row")
        .isEqualTo(2);
  }

  // -------------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------------

  private static String tenantColumnValue(UUID tenantId) {
    return tenantId == null ? "NULL" : "UNHEX('" + tenantId.toString().replace("-", "") + "')";
  }

  private static void insert(String table, String tenantIdExpression) {
    String sql =
        switch (table) {
          case "users" ->
              "INSERT INTO users (id, tenant_id, email, password_hash, created_at, updated_at)"
                  + " VALUES (UNHEX('"
                  + uuidLiteral(UUID.randomUUID())
                  + "'), "
                  + tenantIdExpression
                  + ", 'null-tenant@example.com', 'hash-not-a-secret', UTC_TIMESTAMP(6),"
                  + " UTC_TIMESTAMP(6))";
          case "roles" ->
              "INSERT INTO roles (id, tenant_id, name, created_at, updated_at)"
                  + " VALUES (UNHEX('"
                  + uuidLiteral(UUID.randomUUID())
                  + "'), "
                  + tenantIdExpression
                  + ", 'null tenant role', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))";
          case "role_permissions" ->
              "INSERT INTO role_permissions (tenant_id, role_id, permission_code)"
                  + " VALUES ("
                  + tenantIdExpression
                  + ", UNHEX('"
                  + uuidLiteral(role)
                  + "'), 'PATIENT_VIEW')";
          case "user_roles" ->
              "INSERT INTO user_roles (tenant_id, user_id, role_id, granted_at) VALUES ("
                  + tenantIdExpression
                  + ", UNHEX('"
                  + uuidLiteral(user)
                  + "'), UNHEX('"
                  + uuidLiteral(role)
                  + "'), UTC_TIMESTAMP(6))";
          case "audit_logs" ->
              "INSERT INTO audit_logs (id, tenant_id, actor_id, `action`, entity_type, entity_id,"
                  + " created_at) VALUES (UNHEX('"
                  + uuidLiteral(UUID.randomUUID())
                  + "'), "
                  + tenantIdExpression
                  + ", UNHEX('"
                  + uuidLiteral(user)
                  + "'), 'PATIENT_VIEW', 'patients', UNHEX('"
                  + uuidLiteral(UUID.randomUUID())
                  + "'), UTC_TIMESTAMP(6))";
          default -> throw new IllegalArgumentException("unknown table " + table);
        };
    db.jdbc().execute(sql);
  }

  private static String uuidLiteral(UUID uuid) {
    return uuid.toString().replace("-", "");
  }
}
