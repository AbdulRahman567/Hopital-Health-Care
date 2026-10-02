package com.healthcare.hms.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** P3.4 — {@code V3__audit_logs.sql}: the append-only audit trail table. */
class MigrationV3IT {

  private static MigrationTestSupport db;
  private static UUID tenant;
  private static UUID actor;

  @BeforeAll
  static void migrateFromEmptyDatabase() {
    db = MigrationTestSupport.withEmptyDatabase();
    db.migrate();
    tenant = db.insertTenant("v3-tenant");
    actor = db.insertUser(tenant, "auditor@v3-tenant.example.com");
  }

  @AfterAll
  static void dropSchema() {
    if (db != null) {
      db.close();
    }
  }

  @Test
  void v3WasAppliedSuccessfullyOnAnEmptyDatabase() {
    assertThat(db.migrationSucceeded("3")).isTrue();
  }

  @Test
  void auditLogsTableHasTheAppendOnlyShape() {
    assertThat(db.tableExists("audit_logs")).isTrue();
    assertThat(db.columnIsNullable("audit_logs", "tenant_id")).isFalse();
    assertThat(db.columnIsNullable("audit_logs", "action")).isFalse();
    assertThat(db.columnIsNullable("audit_logs", "entity_type")).isFalse();
    assertThat(db.columnIsNullable("audit_logs", "created_at")).isFalse();
    assertThat(db.columnIsNullable("audit_logs", "actor_id")).isTrue();

    assertThat(db.columnExists("audit_logs", "updated_at"))
        .as("append-only rows are never updated (plan P3.4)")
        .isFalse();
    assertThat(db.columnExists("audit_logs", "updated_by")).isFalse();
    assertThat(db.columnExists("audit_logs", "version")).isFalse();
  }

  @Test
  void bothCompositeIndexesLeadWithTenantIdInTddOrder() {
    assertThat(db.indexColumns("audit_logs", "idx_audit_logs_tenant_entity"))
        .containsExactly("tenant_id", "entity_type", "entity_id", "created_at");
    assertThat(db.indexColumns("audit_logs", "idx_audit_logs_tenant_actor"))
        .containsExactly("tenant_id", "actor_id", "created_at");
  }

  @Test
  void bothForeignKeysAreIndexed() {
    // MySQL always reports the primary key index as PRIMARY, even when the DDL names the
    // constraint pk_audit_logs (see the P3.8 note on the pk_ naming convention).
    assertThat(db.indexColumns("audit_logs", "PRIMARY")).containsExactly("id");
    assertThat(
            db.count(
                "SELECT COUNT(*) FROM information_schema.statistics"
                    + " WHERE table_schema = ? AND table_name = ? AND column_name = 'tenant_id'",
                db.schema(),
                "audit_logs"))
        .as("tenant_id must be indexed (ENGINEERING_RULES 5.3)")
        .isGreaterThan(0);
    assertThat(
            db.count(
                "SELECT COUNT(*) FROM information_schema.statistics"
                    + " WHERE table_schema = ? AND table_name = ? AND column_name = 'actor_id'",
                db.schema(),
                "audit_logs"))
        .as("actor_id must be indexed (ENGINEERING_RULES 5.3)")
        .isGreaterThan(0);
  }

  @Test
  void insertWithNullTenantIdFails() {
    assertThatThrownBy(() -> insertAuditLog(null, actor, "TENANT_UPDATE", "tenants"))
        .as("audit_logs.tenant_id is NOT NULL")
        .isInstanceOf(Exception.class);
  }

  @Test
  void unknownActorIsRejectedByTheForeignKey() {
    assertThatThrownBy(() -> insertAuditLog(tenant, UUID.randomUUID(), "PATIENT_VIEW", "patients"))
        .as("fk_audit_logs_users must reject an actor that does not exist")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("fk_audit_logs_users");
  }

  @Test
  void unknownTenantIsRejectedByTheForeignKey() {
    assertThatThrownBy(() -> insertAuditLog(UUID.randomUUID(), actor, "PATIENT_VIEW", "patients"))
        .as("fk_audit_logs_tenants must reject a tenant that does not exist")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("fk_audit_logs_tenants");
  }

  @Test
  void tenantScopedInsertAndSelectRoundTripWorks() {
    UUID entity = UUID.randomUUID();
    insertAuditLog(tenant, actor, "PATIENT_UPDATE", "patients", entity);

    List<String> actions =
        db.jdbc()
            .queryForList(
                "SELECT `action` FROM audit_logs WHERE tenant_id = ? AND entity_id = ?"
                    + " ORDER BY created_at DESC",
                String.class,
                MigrationTestSupport.uuidBytes(tenant),
                MigrationTestSupport.uuidBytes(entity));

    assertThat(actions).containsExactly("PATIENT_UPDATE");
  }

  private void insertAuditLog(UUID tenantId, UUID actorId, String action, String entityType) {
    insertAuditLog(tenantId, actorId, action, entityType, UUID.randomUUID());
  }

  private void insertAuditLog(
      UUID tenantId, UUID actorId, String action, String entityType, UUID entityId) {
    db.jdbc()
        .update(
            "INSERT INTO audit_logs (id, tenant_id, actor_id, `action`, entity_type, entity_id,"
                + " ip, user_agent, metadata, created_at) VALUES (?, ?, ?, ?, ?, ?,"
                + " '127.0.0.1', 'junit', NULL, ?)",
            MigrationTestSupport.uuidBytes(UUID.randomUUID()),
            tenantId == null ? null : MigrationTestSupport.uuidBytes(tenantId),
            actorId == null ? null : MigrationTestSupport.uuidBytes(actorId),
            action,
            entityType,
            MigrationTestSupport.uuidBytes(entityId),
            MigrationTestSupport.now());
  }
}
