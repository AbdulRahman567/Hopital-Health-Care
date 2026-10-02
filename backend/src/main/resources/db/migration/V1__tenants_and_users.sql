-- =============================================================================
-- V1__tenants_and_users.sql
-- Phase 3 (ROADMAP P3.2) — baseline identity tables.
--
-- Rules applied (docs/DATABASE.md section 1-4, docs/TDD.md section 9.1/9.2):
--   * platform tables (tenants) carry NO tenant_id; every other table does
--   * primary keys are UUIDs stored as BINARY(16) (v7, time ordered, assigned by the app)
--   * timestamps are DATETIME(6) in UTC; enums are VARCHAR + CHECK
--   * audit columns on every table: created_at, created_by, updated_at, updated_by, version
--   * naming: pk_ / fk_<table>_<ref> / uq_<table>_<cols> / idx_<table>_<cols>
--   * every FK column is indexed; composite indexes lead with tenant_id
--   * no ON DELETE CASCADE anywhere (ENGINEERING_RULES section 5.4)
--
-- Deviations recorded for P3.8:
--   * D4 — first_name / last_name added to users (not in TDD section 9.2): attribution for
--     FR-1.1 admin creation and audit trails needs a human-readable actor.
--   * users.status values are not documented anywhere: ('PENDING','ACTIVE','INACTIVE') was
--     chosen as the minimal set that covers invite-pending, active and deactivated accounts
--     (STAFF_DEACTIVATE in the permission catalog).
--   * created_by / updated_by are plain BINARY(16) columns with no FK on purpose: a self
--     referencing FK on users would be circular for the bootstrap rows, and platform-level
--     rows have no acting user at all.
-- =============================================================================

CREATE TABLE tenants (
    id          BINARY(16)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(63)  NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    timezone    VARCHAR(64)  NULL,
    verified_at DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    created_by  BINARY(16)   NULL,
    updated_at  DATETIME(6)  NOT NULL,
    updated_by  BINARY(16)   NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_tenants PRIMARY KEY (id),
    CONSTRAINT uq_tenants_slug UNIQUE (slug),
    CONSTRAINT chk_tenants_status CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Reserved platform tenant (GAP-1): fixed id so platform admins always have a valid
-- users.tenant_id target and no later data migration is needed (decision D5).
-- Keep the constant in sync with Tenant.PLATFORM_TENANT_ID.
INSERT INTO tenants (id, name, slug, status, timezone, verified_at, created_at, created_by, updated_at,
                     updated_by, version)
VALUES (UNHEX('00000000000000000000000000000001'),
        'Platform',
        'platform',
        'ACTIVE',
        'UTC',
        UTC_TIMESTAMP(6),
        UTC_TIMESTAMP(6),
        NULL,
        UTC_TIMESTAMP(6),
        NULL,
        0);

CREATE TABLE users (
    id              BINARY(16)    NOT NULL,
    tenant_id       BINARY(16)    NOT NULL,
    email           VARCHAR(320)  NOT NULL,
    password_hash   VARCHAR(255)  NOT NULL,
    first_name      VARCHAR(100)  NULL,
    last_name       VARCHAR(100)  NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    failed_attempts INT           NOT NULL DEFAULT 0,
    locked_until    DATETIME(6)   NULL,
    mfa_enabled     TINYINT(1)    NOT NULL DEFAULT 0,
    mfa_secret      VARBINARY(512) NULL,
    created_at      DATETIME(6)   NOT NULL,
    created_by      BINARY(16)    NULL,
    updated_at      DATETIME(6)   NOT NULL,
    updated_by      BINARY(16)    NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT fk_users_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT chk_users_status CHECK (status IN ('PENDING', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_users_mfa_enabled CHECK (mfa_enabled IN (0, 1)),
    CONSTRAINT chk_users_failed_attempts CHECK (failed_attempts >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Every FK column is indexed (ENGINEERING_RULES section 5.3). tenant_id also leads
-- uq_users_tenant_email; the explicit index keeps the FK guarantee independent of that key.
CREATE INDEX idx_users_tenant_id ON users (tenant_id);
