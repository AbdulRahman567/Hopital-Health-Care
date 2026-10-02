-- =============================================================================
-- V3__audit_logs.sql
-- Phase 3 (ROADMAP P3.4) — the append-only audit trail base table.
--
-- Design (TDD section 9.2 / 9.3, DATABASE section 8):
--   * Append-only: rows are written once and never updated, so there is deliberately no
--     updated_at / updated_by / version. ENGINEERING_RULES section 5.6 still applies to
--     everything mutable; audit_logs carries created_at plus actor_id (which plays the
--     created_by role for a write-only table).
--   * tenant_id NOT NULL — every audit row belongs to exactly one tenant (TDD section 9.1).
--   * Two composite indexes from TDD section 9.3, both leading with tenant_id:
--       idx_audit_logs_tenant_entity (tenant_id, entity_type, entity_id, created_at)
--       idx_audit_logs_tenant_actor  (tenant_id, actor_id, created_at)
--     Together they cover both foreign keys (tenant_id leads, actor_id is indexed) and the
--     "audit by entity / by actor / by time" list filters of DATABASE section 4.
--
-- Append-only enforcement:
--   * DB-level INSERT/SELECT-only grants for the application user are Phase 19.1, NOT now.
--     P19.1 owns AuditAppendOnlyTest; this migration only lays down the structure.
--
-- Known deviation (recorded in DATABASE section 8 and the ROADMAP Phase 3 evidence block): no
-- updated_at / updated_by / version audit columns on this table — append-only rows are never
-- updated, so those three columns would always be a copy of created_at.
-- =============================================================================

CREATE TABLE audit_logs (
    id          BINARY(16)   NOT NULL,
    tenant_id   BINARY(16)   NOT NULL,
    actor_id    BINARY(16)   NULL,
    `action`    VARCHAR(64)  NOT NULL,
    entity_type VARCHAR(64)  NOT NULL,
    entity_id   BINARY(16)   NULL,
    ip          VARCHAR(45)  NULL,
    user_agent  VARCHAR(255) NULL,
    metadata    JSON         NULL,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_audit_logs_users FOREIGN KEY (actor_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_audit_logs_tenant_entity
    ON audit_logs (tenant_id, entity_type, entity_id, created_at);

CREATE INDEX idx_audit_logs_tenant_actor
    ON audit_logs (tenant_id, actor_id, created_at);
