package com.healthcare.hms.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * P5.1 — {@code V4__refresh_and_verification_tokens.sql}: the hashed, expiring, single-use token
 * tables that every Phase 5 flow is built on.
 *
 * <p>{@code ddl-auto=validate} ignores tables no entity maps yet, so this suite is the only proof
 * that the schema landed correctly before {@code RefreshToken}/{@code VerificationToken} exist —
 * and {@link IndexConventionIT} picks both tables up automatically because it iterates {@code
 * information_schema} rather than a list.
 */
class MigrationAuthIT {

  private static MigrationTestSupport db;
  private static UUID tenant;
  private static UUID user;

  @BeforeAll
  static void migrateFromEmptyDatabase() {
    db = MigrationTestSupport.withEmptyDatabase();
    db.migrate();
    tenant = db.insertTenant("v4-tenant");
    user = db.insertUser(tenant, "owner@v4-tenant.example.com");
  }

  @AfterAll
  static void dropSchema() {
    if (db != null) {
      db.close();
    }
  }

  @Test
  void v4WasAppliedSuccessfullyOnAnEmptyDatabase() {
    assertThat(db.migrationSucceeded("4")).isTrue();
    assertThat(db.tableExists("refresh_tokens")).isTrue();
    assertThat(db.tableExists("verification_tokens")).isTrue();
  }

  @Test
  void flywayHistoryIsStrictlyOrderedFromOneToFour() {
    List<String> versions =
        db.jdbc()
            .queryForList(
                "SELECT version FROM flyway_schema_history"
                    + " WHERE version IS NOT NULL AND success = 1 ORDER BY installed_rank",
                String.class);

    assertThat(versions).containsExactly("1", "2", "3", "4");
    assertThat(db.currentVersion()).isEqualTo("4");
    assertThat(db.flyway().info().pending()).isEmpty();
  }

  @Test
  void tenantIdAndLookupColumnsAreNotNullOnBothTokenTables() {
    for (String table : List.of("refresh_tokens", "verification_tokens")) {
      assertThat(db.columnExists(table, "tenant_id"))
          .as("%s must have a tenant_id column (CONF-5)", table)
          .isTrue();
      assertThat(db.columnIsNullable(table, "tenant_id"))
          .as("%s.tenant_id must be IS_NULLABLE = 'NO'", table)
          .isFalse();
      assertThat(db.columnIsNullable(table, "user_id")).isFalse();
      assertThat(db.columnIsNullable(table, "token_hash")).isFalse();
      assertThat(db.columnIsNullable(table, "expires_at")).isFalse();
    }
    assertThat(db.columnIsNullable("refresh_tokens", "family_id")).isFalse();
    assertThat(db.columnIsNullable("verification_tokens", "type")).isFalse();
    assertThat(db.columnIsNullable("refresh_tokens", "revoked_at")).isTrue();
    assertThat(db.columnIsNullable("verification_tokens", "used_at"))
        .as("a token is single-use only while used_at can still be NULL")
        .isTrue();
  }

  @Test
  void refreshTokenHashIsUnique() {
    insertRefreshToken(tenant, user, UUID.randomUUID(), "a".repeat(64), null);

    assertThatThrownBy(
            () -> insertRefreshToken(tenant, user, UUID.randomUUID(), "a".repeat(64), null))
        .as("uq_refresh_tokens_token_hash — the D1 bootstrap assumes at most one row per digest")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("uq_refresh_tokens_token_hash");
  }

  @Test
  void verificationTokenHashIsUnique() {
    insertVerificationToken(tenant, user, "VERIFY_EMAIL", "b".repeat(64));

    assertThatThrownBy(() -> insertVerificationToken(tenant, user, "VERIFY_EMAIL", "b".repeat(64)))
        .as("uq_verification_tokens_token_hash")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("uq_verification_tokens_token_hash");
  }

  @Test
  void verificationTypeCheckRejectsAnUnknownType() {
    assertThatThrownBy(() -> insertVerificationToken(tenant, user, "TOTP_ENROLL", "c".repeat(64)))
        .as("chk_verification_tokens_type — INVITE is reserved for P9.3, nothing else may appear")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("chk_verification_tokens_type");
  }

  @Test
  void revokedReasonCheckRejectsAnUnknownReason() {
    assertThatThrownBy(
            () -> insertRefreshToken(tenant, user, UUID.randomUUID(), "d".repeat(64), "STOLEN"))
        .as("chk_refresh_tokens_revoked_reason")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("chk_refresh_tokens_revoked_reason");
  }

  @Test
  void aLiveTokenHasNoRevocationReasonAtAll() {
    String tokenHash = "e".repeat(64);
    insertRefreshToken(tenant, user, UUID.randomUUID(), tokenHash, null);

    assertThat(
            db.count(
                "SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ?"
                    + " AND revoked_reason IS NULL AND revoked_at IS NULL",
                tokenHash))
        .isEqualTo(1);
  }

  @Test
  void refreshTokenForeignKeysRejectOrphanTenantAndUser() {
    assertThatThrownBy(
            () ->
                insertRefreshToken(
                    UUID.randomUUID(), user, UUID.randomUUID(), "f".repeat(64), null))
        .as("fk_refresh_tokens_tenants must reject an unknown tenant")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("fk_refresh_tokens_tenants");

    assertThatThrownBy(
            () ->
                insertRefreshToken(
                    tenant, UUID.randomUUID(), UUID.randomUUID(), "g".repeat(64), null))
        .as("fk_refresh_tokens_users must reject an unknown user")
        .isInstanceOf(Exception.class)
        .hasMessageContaining("fk_refresh_tokens_users");
  }

  @Test
  void verificationTokenForeignKeysRejectOrphanTenantAndUser() {
    assertThatThrownBy(
            () ->
                insertVerificationToken(UUID.randomUUID(), user, "PASSWORD_RESET", "j".repeat(64)))
        .isInstanceOf(Exception.class)
        .hasMessageContaining("fk_verification_tokens_tenants");

    assertThatThrownBy(
            () -> insertVerificationToken(tenant, UUID.randomUUID(), "INVITE", "k".repeat(64)))
        .isInstanceOf(Exception.class)
        .hasMessageContaining("fk_verification_tokens_users");
  }

  @Test
  void noTokenForeignKeyDeletesOnCascade() {
    List<Map<String, Object>> rules =
        db.jdbc()
            .queryForList(
                "SELECT table_name, constraint_name, delete_rule"
                    + " FROM information_schema.referential_constraints"
                    + " WHERE constraint_schema = ? AND table_name IN"
                    + " ('refresh_tokens', 'verification_tokens')",
                db.schema());

    assertThat(rules)
        .as("ENGINEERING_RULES section 5.4: no ON DELETE CASCADE on session evidence")
        .isNotEmpty()
        .allSatisfy(rule -> assertThat(rule.get("delete_rule")).isNotEqualTo("CASCADE"));
  }

  @Test
  void everyNewForeignKeyColumnIsIndexed() {
    assertIndexed("refresh_tokens", "tenant_id");
    assertIndexed("refresh_tokens", "user_id");
    assertIndexed("verification_tokens", "tenant_id");
    assertIndexed("verification_tokens", "user_id");
  }

  @Test
  void lookupIndexesMatchTheAccessPathsThatNeedThem() {
    assertThat(db.indexColumns("refresh_tokens", "idx_refresh_tokens_family_id"))
        .as("reuse detection scans a whole family")
        .containsExactly("family_id");
    assertThat(db.indexColumns("refresh_tokens", "idx_refresh_tokens_tenant_user"))
        .as("composite indexes lead with tenant_id (DATABASE section 4)")
        .containsExactly("tenant_id", "user_id");
    assertThat(db.indexColumns("verification_tokens", "idx_verification_tokens_tenant_type"))
        .containsExactly("tenant_id", "type");
    assertThat(db.indexColumns("refresh_tokens", "uq_refresh_tokens_token_hash"))
        .containsExactly("token_hash");
    assertThat(db.indexColumns("verification_tokens", "uq_verification_tokens_token_hash"))
        .containsExactly("token_hash");
  }

  @Test
  void aRealRoundTripRowCanBeWrittenToBothTables() {
    insertRefreshToken(tenant, user, UUID.randomUUID(), "h".repeat(64), null);
    insertVerificationToken(tenant, user, "PASSWORD_RESET", "i".repeat(64));

    assertThat(db.count("SELECT COUNT(*) FROM refresh_tokens")).isGreaterThanOrEqualTo(1);
    assertThat(
            db.count(
                "SELECT COUNT(*) FROM verification_tokens WHERE tenant_id = ?",
                MigrationTestSupport.uuidBytes(tenant)))
        .isGreaterThanOrEqualTo(1);
  }

  // -------------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------------

  private void assertIndexed(String table, String column) {
    assertThat(
            db.count(
                "SELECT COUNT(*) FROM information_schema.statistics"
                    + " WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                db.schema(),
                table,
                column))
        .as("%s.%s must be indexed (ENGINEERING_RULES 5.3)", table, column)
        .isGreaterThan(0);
  }

  private void insertRefreshToken(
      UUID tenantId, UUID userId, UUID familyId, String tokenHash, String revokedReason) {
    db.jdbc()
        .update(
            "INSERT INTO refresh_tokens (id, tenant_id, user_id, family_id, token_hash,"
                + " expires_at, revoked_at, revoked_reason, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, ?)",
            MigrationTestSupport.uuidBytes(UUID.randomUUID()),
            MigrationTestSupport.uuidBytes(tenantId),
            MigrationTestSupport.uuidBytes(userId),
            MigrationTestSupport.uuidBytes(familyId),
            tokenHash,
            inSevenDays(),
            revokedReason,
            MigrationTestSupport.now(),
            MigrationTestSupport.now());
  }

  private void insertVerificationToken(UUID tenantId, UUID userId, String type, String tokenHash) {
    db.jdbc()
        .update(
            "INSERT INTO verification_tokens (id, tenant_id, user_id, type, token_hash,"
                + " expires_at, used_at, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?)",
            MigrationTestSupport.uuidBytes(UUID.randomUUID()),
            MigrationTestSupport.uuidBytes(tenantId),
            MigrationTestSupport.uuidBytes(userId),
            type,
            tokenHash,
            inSevenDays(),
            MigrationTestSupport.now(),
            MigrationTestSupport.now());
  }

  private static Timestamp inSevenDays() {
    return Timestamp.from(Instant.now().plusSeconds(7 * 24 * 3600));
  }
}
