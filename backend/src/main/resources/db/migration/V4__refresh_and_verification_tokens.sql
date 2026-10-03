-- =============================================================================
-- V4__refresh_and_verification_tokens.sql
-- Phase 5 (ROADMAP P5.1) — hashed, expiring, single-use auth token tables.
--
-- Design (TDD section 7, section 9.2; SECURITY section 3; DATABASE section 2/4):
--   * refresh_tokens  : opaque refresh values (D8/D10) stored ONLY as a SHA-256 digest.
--     A login session owns a family_id; each rotation writes a new row in the same family and
--     revokes the predecessor, so reuse detection is "presented a revoked row" and family-wide
--     revocation is "UPDATE ... WHERE family_id = ?".
--   * verification_tokens : VERIFY_EMAIL / PASSWORD_RESET / INVITE (INVITE reserved for P9.3),
--     single-use through used_at, time-limited through expires_at.
--   * tenant_id NOT NULL on both (CONF-5, DATABASE section 1) — every token belongs to exactly
--     one tenant, and the D1 secret-leg bootstrap reads it back to bind TenantContext before any
--     tenant-filtered query can run.
--   * No ON DELETE CASCADE (ENGINEERING_RULES section 5.4): revoking sessions is an explicit,
--     auditable act; deleting a user must never silently erase the evidence.
--
-- Why hashed:
--   Refresh and verification tokens are 256-bit random secrets handed to the client, exactly
--   like a password in shape — but unlike a password they are high-entropy, single-use and
--   verified by exact comparison, so a plain SHA-256 digest (no salt, no stretching) is both
--   sufficient and correct: an attacker with the database learns nothing usable and cannot
--   invert the value. Password hashes stay in users.password_hash and remain Argon2/bcrypt
--   (TQ-2) because passwords are low-entropy and need a slow KDF.
--   uq_*_token_hash is what makes the D1 bootstrap lookup exact: one digest -> at most one row.
--
-- Index discipline (ENGINEERING_RULES section 5.3, DATABASE section 4): every FK column is
-- indexed, composites lead with tenant_id, and names are written into the DDL as
-- pk_/uq_/fk_/idx_ (MySQL stores primary key indexes as PRIMARY regardless — P3.8 note).
-- =============================================================================

CREATE TABLE refresh_tokens (
    id             BINARY(16)   NOT NULL,
    tenant_id      BINARY(16)   NOT NULL,
    user_id        BINARY(16)   NOT NULL,
    family_id      BINARY(16)   NOT NULL,
    token_hash     VARCHAR(64)  NOT NULL,
    expires_at     DATETIME(6)  NOT NULL,
    revoked_at     DATETIME(6)  NULL,
    revoked_reason VARCHAR(16)  NULL,
    user_agent     VARCHAR(512) NULL,
    created_at     DATETIME(6)  NOT NULL,
    created_by     BINARY(16)   NULL,
    updated_at     DATETIME(6)  NOT NULL,
    updated_by     BINARY(16)   NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_refresh_tokens_users FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_refresh_tokens_revoked_reason
        CHECK (revoked_reason IS NULL
               OR revoked_reason IN ('ROTATED', 'REUSED', 'LOGOUT', 'PASSWORD_RESET'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Both FK columns indexed (ENGINEERING_RULES section 5.3); family_id is the reuse-detection
-- scan; (tenant_id, user_id) is the "revoke every session of this user" path and leads with
-- tenant_id as every composite must.
CREATE INDEX idx_refresh_tokens_tenant_id ON refresh_tokens (tenant_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_tenant_user ON refresh_tokens (tenant_id, user_id);

CREATE TABLE verification_tokens (
    id         BINARY(16)  NOT NULL,
    tenant_id  BINARY(16)  NOT NULL,
    user_id    BINARY(16)  NOT NULL,
    type       VARCHAR(16) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BINARY(16)  NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BINARY(16)  NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_verification_tokens PRIMARY KEY (id),
    CONSTRAINT uq_verification_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_verification_tokens_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_verification_tokens_users FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_verification_tokens_type
        CHECK (type IN ('VERIFY_EMAIL', 'PASSWORD_RESET', 'INVITE'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_verification_tokens_tenant_id ON verification_tokens (tenant_id);
CREATE INDEX idx_verification_tokens_user_id ON verification_tokens (user_id);
CREATE INDEX idx_verification_tokens_tenant_type ON verification_tokens (tenant_id, type);
