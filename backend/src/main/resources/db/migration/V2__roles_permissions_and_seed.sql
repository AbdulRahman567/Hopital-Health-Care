-- =============================================================================
-- V2__roles_permissions_and_seed.sql
-- Phase 3 (ROADMAP P3.3) — RBAC tables plus the authoritative permission catalog.
--
-- Confirmed decisions applied here:
--   * CONF-5 — tenant_id is present on roles / role_permissions / user_roles, and the
--     relationships use COMPOSITE foreign keys that carry tenant_id so a cross-tenant
--     (tenant_id, role_id) pair can never be written.
--   * D3 — this migration seeds the authoritative permission catalog (plan section 5.4).
--     P6.1's in-code catalog must reproduce these rows exactly; any future permission is
--     added here AND in the code catalog in the same change.
--
-- Supporting keys required by the composite FKs (a parent key must start with the same
-- columns): uq_roles_tenant_id on roles and uq_users_tenant_id on users. V1 cannot be edited
-- (DATABASE section 3.1), so the users side is added here as a backward-compatible expansion.
--
-- Audit columns follow ENGINEERING_RULES section 5.6: present on the entity tables
-- (permissions, roles); the pure association tables role_permissions / user_roles carry only
-- their key columns (user_roles.granted_at plays the "created_at" role).
-- =============================================================================

CREATE TABLE permissions (
    code        VARCHAR(64)  NOT NULL,
    `module`    VARCHAR(32)  NOT NULL,
    `action`    VARCHAR(32)  NOT NULL,
    description VARCHAR(255) NULL,
    created_at  DATETIME(6)  NOT NULL,
    created_by  BINARY(16)   NULL,
    updated_at  DATETIME(6)  NOT NULL,
    updated_by  BINARY(16)   NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_permissions PRIMARY KEY (code),
    CONSTRAINT uq_permissions_module_action UNIQUE (`module`, `action`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE roles (
    id          BINARY(16)   NOT NULL,
    tenant_id   BINARY(16)   NOT NULL,
    name        VARCHAR(100) NOT NULL,
    system_flag TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL,
    created_by  BINARY(16)   NULL,
    updated_at  DATETIME(6)  NOT NULL,
    updated_by  BINARY(16)   NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT fk_roles_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT chk_roles_system_flag CHECK (system_flag IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Supporting parent keys for the composite foreign keys below: MySQL requires a unique index
-- that starts with the same columns as the FK. V1 is already applied and cannot be edited
-- (DATABASE section 3.1), so both keys are added here in a backward-compatible way.
ALTER TABLE roles
    ADD CONSTRAINT uq_roles_tenant_id UNIQUE (tenant_id, id);

ALTER TABLE users
    ADD CONSTRAINT uq_users_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE role_permissions (
    tenant_id       BINARY(16)  NOT NULL,
    role_id         BINARY(16)  NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (tenant_id, role_id, permission_code),
    CONSTRAINT fk_role_permissions_roles FOREIGN KEY (tenant_id, role_id)
        REFERENCES roles (tenant_id, id),
    CONSTRAINT fk_role_permissions_permissions FOREIGN KEY (permission_code)
        REFERENCES permissions (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- The composite primary key only leads with tenant_id, so the trailing FK columns still need
-- their own indexes (ENGINEERING_RULES section 5.3).
CREATE INDEX idx_role_permissions_role_id ON role_permissions (role_id);
CREATE INDEX idx_role_permissions_permission_code ON role_permissions (permission_code);

CREATE TABLE user_roles (
    tenant_id  BINARY(16) NOT NULL,
    user_id    BINARY(16) NOT NULL,
    role_id    BINARY(16) NOT NULL,
    granted_at DATETIME(6) NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (tenant_id, user_id, role_id),
    CONSTRAINT fk_user_roles_users FOREIGN KEY (tenant_id, user_id)
        REFERENCES users (tenant_id, id),
    CONSTRAINT fk_user_roles_roles FOREIGN KEY (tenant_id, role_id)
        REFERENCES roles (tenant_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_user_roles_user_id ON user_roles (user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);

-- -----------------------------------------------------------------------------
-- Permission seed (decision D3, plan section 5.4) — format MODULE_ACTION.
-- Platform-level rows: permissions is one of the two tables without tenant_id (DATABASE
-- section 1). No PHI and no secrets here; these are code-like catalog values.
-- -----------------------------------------------------------------------------

INSERT INTO permissions (code, `module`, `action`, description, created_at, created_by, updated_at, updated_by, version)
VALUES ('TENANT_VIEW', 'TENANT', 'VIEW', 'View tenant details', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('TENANT_UPDATE', 'TENANT', 'UPDATE', 'Update tenant settings', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('TENANT_SUSPEND', 'TENANT', 'SUSPEND', 'Suspend a tenant', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('TENANT_ACTIVATE', 'TENANT', 'ACTIVATE', 'Activate a tenant', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0);

INSERT INTO permissions (code, `module`, `action`, description, created_at, created_by, updated_at, updated_by, version)
VALUES ('DEPARTMENT_VIEW', 'DEPARTMENT', 'VIEW', 'List departments', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('DEPARTMENT_CREATE', 'DEPARTMENT', 'CREATE', 'Create a department', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('DEPARTMENT_UPDATE', 'DEPARTMENT', 'UPDATE', 'Update a department', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('DEPARTMENT_DELETE', 'DEPARTMENT', 'DELETE', 'Remove a department', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0);

INSERT INTO permissions (code, `module`, `action`, description, created_at, created_by, updated_at, updated_by, version)
VALUES ('STAFF_VIEW', 'STAFF', 'VIEW', 'List staff members', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('STAFF_CREATE', 'STAFF', 'CREATE', 'Create a staff account', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('STAFF_UPDATE', 'STAFF', 'UPDATE', 'Update a staff account', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('STAFF_DEACTIVATE', 'STAFF', 'DEACTIVATE', 'Deactivate a staff account', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('STAFF_INVITE', 'STAFF', 'INVITE', 'Send a staff invitation', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0);

INSERT INTO permissions (code, `module`, `action`, description, created_at, created_by, updated_at, updated_by, version)
VALUES ('ROLE_VIEW', 'ROLE', 'VIEW', 'List roles', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('ROLE_CREATE', 'ROLE', 'CREATE', 'Create a role', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('ROLE_UPDATE', 'ROLE', 'UPDATE', 'Update a role', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('ROLE_DELETE', 'ROLE', 'DELETE', 'Remove a role', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0);

INSERT INTO permissions (code, `module`, `action`, description, created_at, created_by, updated_at, updated_by, version)
VALUES ('PATIENT_VIEW', 'PATIENT', 'VIEW', 'List patients', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('PATIENT_CREATE', 'PATIENT', 'CREATE', 'Register a patient', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('PATIENT_UPDATE', 'PATIENT', 'UPDATE', 'Update patient demographics', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('PATIENT_EXPORT', 'PATIENT', 'EXPORT', 'Export patient data', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('PATIENT_VIEW_DIAGNOSIS', 'PATIENT', 'VIEW_DIAGNOSIS', 'Read diagnosis fields', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('PATIENT_VIEW_NOTES', 'PATIENT', 'VIEW_NOTES', 'Read clinical note fields', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0);

INSERT INTO permissions (code, `module`, `action`, description, created_at, created_by, updated_at, updated_by, version)
VALUES ('APPOINTMENT_VIEW', 'APPOINTMENT', 'VIEW', 'List appointments', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('APPOINTMENT_CREATE', 'APPOINTMENT', 'CREATE', 'Book an appointment', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('APPOINTMENT_UPDATE', 'APPOINTMENT', 'UPDATE', 'Reschedule an appointment', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('APPOINTMENT_CANCEL', 'APPOINTMENT', 'CANCEL', 'Cancel an appointment', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0);

INSERT INTO permissions (code, `module`, `action`, description, created_at, created_by, updated_at, updated_by, version)
VALUES ('VISIT_VIEW', 'VISIT', 'VIEW', 'List visits', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('VISIT_CREATE', 'VISIT', 'CREATE', 'Open a visit', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('VISIT_UPDATE', 'VISIT', 'UPDATE', 'Edit an open visit', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('VISIT_FINALIZE', 'VISIT', 'FINALIZE', 'Finalize a visit', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0);

INSERT INTO permissions (code, `module`, `action`, description, created_at, created_by, updated_at, updated_by, version)
VALUES ('PRESCRIPTION_VIEW', 'PRESCRIPTION', 'VIEW', 'List prescriptions', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('PRESCRIPTION_CREATE', 'PRESCRIPTION', 'CREATE', 'Issue a prescription', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('PRESCRIPTION_UPDATE', 'PRESCRIPTION', 'UPDATE', 'Amend a prescription', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('PRESCRIPTION_EXPORT', 'PRESCRIPTION', 'EXPORT', 'Export a prescription', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0);

INSERT INTO permissions (code, `module`, `action`, description, created_at, created_by, updated_at, updated_by, version)
VALUES ('MEDICINE_VIEW', 'MEDICINE', 'VIEW', 'List medicines', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('MEDICINE_CREATE', 'MEDICINE', 'CREATE', 'Add a medicine', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('MEDICINE_UPDATE', 'MEDICINE', 'UPDATE', 'Update a medicine', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0);

INSERT INTO permissions (code, `module`, `action`, description, created_at, created_by, updated_at, updated_by, version)
VALUES ('LAB_VIEW', 'LAB', 'VIEW', 'List lab orders and results', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('LAB_CREATE', 'LAB', 'CREATE', 'Order a lab test', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('LAB_UPDATE', 'LAB', 'UPDATE', 'Record a lab result', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0);

INSERT INTO permissions (code, `module`, `action`, description, created_at, created_by, updated_at, updated_by, version)
VALUES ('DOCUMENT_VIEW', 'DOCUMENT', 'VIEW', 'List documents', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('DOCUMENT_UPLOAD', 'DOCUMENT', 'UPLOAD', 'Upload a document', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('DOCUMENT_DOWNLOAD', 'DOCUMENT', 'DOWNLOAD', 'Download a document', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0);

INSERT INTO permissions (code, `module`, `action`, description, created_at, created_by, updated_at, updated_by, version)
VALUES ('BILLING_VIEW', 'BILLING', 'VIEW', 'Read invoices and payments', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('BILLING_CREATE', 'BILLING', 'CREATE', 'Raise an invoice', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('BILLING_UPDATE', 'BILLING', 'UPDATE', 'Adjust an invoice', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0);

INSERT INTO permissions (code, `module`, `action`, description, created_at, created_by, updated_at, updated_by, version)
VALUES ('AUDIT_VIEW', 'AUDIT', 'VIEW', 'Read audit log entries', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0);

INSERT INTO permissions (code, `module`, `action`, description, created_at, created_by, updated_at, updated_by, version)
VALUES ('NOTIFICATION_VIEW', 'NOTIFICATION', 'VIEW', 'Read notifications', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('NOTIFICATION_MANAGE', 'NOTIFICATION', 'MANAGE', 'Configure notification delivery', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0);

INSERT INTO permissions (code, `module`, `action`, description, created_at, created_by, updated_at, updated_by, version)
VALUES ('SEARCH_QUERY', 'SEARCH', 'QUERY', 'Run global search', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0),
       ('SEARCH_EXPORT', 'SEARCH', 'EXPORT', 'Export search results', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0);

INSERT INTO permissions (code, `module`, `action`, description, created_at, created_by, updated_at, updated_by, version)
VALUES ('DASHBOARD_VIEW', 'DASHBOARD', 'VIEW', 'Read dashboards', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), NULL, 0);
