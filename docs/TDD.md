# Healthcare-HMS — Technical Design Document (TDD)

| | |
|---|---|
| **Document status** | Draft v1.0 — for approval before Phase 1 |
| **Implements** | PRD.md v1.0 |
| **Scope** | System architecture, security design, data model, APIs, infrastructure, testing |

> Deviations from this document require a documented Architecture Decision Record (ADR, §20). Agents must not invent architecture that contradicts it.

---

## 1. Purpose

This document describes **how** Healthcare-HMS satisfies the PRD: a multi-tenant Healthcare SaaS with strict tenant isolation, layered authorization, versioned clinical records, auditability and observability.

## 2. Technology Stack

| Layer | Technology |
|---|---|
| **Frontend** | Next.js 15, React 19, TypeScript, Tailwind CSS, shadcn/ui, Redux Toolkit, TanStack Query, React Hook Form, Zod, Axios, Lucide React, Framer Motion |
| **Backend** | Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Hibernate, Flyway, MapStruct, Lombok, Jakarta Validation, OpenAPI |
| **Data** | MySQL (managed in production), Redis |
| **Storage** | MinIO (local), AWS S3 (production) |
| **Infrastructure** | Docker, Docker Compose, Nginx |
| **Deployment (v1)** | Frontend → Vercel; Backend → AWS EC2; MySQL → managed DB; Files → S3 |

The stack is fixed unless changed by an ADR.

## 3. Architectural Principles

1. **Security by default:** every endpoint denies unless permission is declared.
2. **Tenant context originates from authentication**, never from client input.
3. **Stateless backend:** no server-side session state; state lives in MySQL, Redis or S3.
4. **Modular monolith:** one deployable, strict module boundaries; extract services only on evidence.
5. **Layered:** Controller → Service → Repository; no business logic in controllers, no entities across the API boundary.
6. **Additive history:** clinical data is versioned, not overwritten.
7. **Simple first:** background jobs in-process now; brokers later.

## 4. High-Level Architecture

```text
                        ┌────────────────────────┐
   Browser ───────────▶ │ Next.js (Vercel)       │
                        └───────────┬────────────┘
                                    │ HTTPS (/api/v1)
                        ┌───────────▼────────────┐
                        │ Nginx / Reverse proxy  │
                        └───────────┬────────────┘
                        ┌───────────▼────────────────────────────────┐
                        │ Spring Boot (stateless)                    │
                        │  Filters: RateLimit → Auth → TenantContext │
                        │  Controller → Service → Repository         │
                        │  Async jobs · Audit · Metrics              │
                        └──┬─────────────┬─────────────┬─────────────┘
                           │             │             │
                     ┌─────▼────┐  ┌─────▼────┐  ┌─────▼──────┐
                     │  MySQL   │  │  Redis   │  │ S3 / MinIO │
                     └──────────┘  └──────────┘  └────────────┘
```

### 4.1 Request Pipeline (Tenant Isolation Chain)

```text
Request → Rate limit → Authentication → Tenant Resolution → Tenant Context
        → Authorization → Service → Repository (tenant-filtered) → Database
```

Isolation is enforced at **every** stage; no single layer is trusted alone.

## 5. Repository Structure

```text
healthcare-hms/
├── docs/                    # PRD, TDD, ARCHITECTURE, ENGINEERING_RULES, ROADMAP,
│                            # PROJECT_CONTEXT, DESIGN_SYSTEM, DATABASE, API, SECURITY,
│                            # TESTING, DEPLOYMENT, AI_DEVELOPMENT_GUIDE
├── backend/
│   └── src/main/java/.../hms/
│       ├── common/          # exceptions, response envelope, pagination, audit base
│       ├── config/          # security, redis, storage, openapi, async
│       ├── tenant/          # TenantContext, resolver, filters
│       ├── auth/            # login, tokens, reset, verification
│       ├── authz/           # permissions, roles, policies, field masking
│       ├── organization/    # hospital, departments
│       ├── staff/
│       ├── patient/
│       ├── appointment/
│       ├── clinical/        # visits, vitals, notes, diagnoses, orders
│       ├── history/         # conditions, family/surgical, timeline
│       ├── prescription/
│       ├── lab/
│       ├── document/
│       ├── billing/
│       ├── notification/
│       ├── audit/
│       └── search/
│   └── src/main/resources/db/migration/   # Flyway V###__*.sql
├── frontend/
│   └── src/{app, components, features, hooks, lib, store, types}
├── infra/                   # docker-compose, nginx, env templates
└── .github/workflows/       # CI/CD
```

Each backend feature module follows: `api` (controller, DTOs) → `service` → `repository` → `domain`.

## 6. Multi-Tenancy Design

### 6.1 Strategy
**Shared database, shared schema, `tenant_id` discriminator** on every tenant-owned table. Chosen for operational simplicity at the MVP; isolation strength comes from defense in depth (§6.3). The design does not preclude moving large tenants to separate schemas/databases later (ADR-002).

### 6.2 Tenant Resolution
1. Access token contains the `tenantId` claim, issued at login from the user's own record.
2. A `TenantContextFilter` reads it after authentication and stores it in a request-scoped `TenantContext` (cleared in `finally`).
3. If any client-sent tenant hint exists (header, path, body), it is **ignored or validated against** the authenticated tenant and rejected on mismatch. Never trusted.
4. Platform Admin operations use an explicit, audited cross-tenant path.

### 6.3 Enforcement Layers
| Layer | Mechanism |
|---|---|
| Controller | Authenticated principal required; no tenant IDs accepted as authority |
| Service | Reads tenant from `TenantContext`; passes never from DTOs |
| Repository | Hibernate tenant filtering (`@TenantId` or `@Filter`) applied to all queries; native queries must include `tenant_id` explicitly and are code-reviewed |
| Database | `tenant_id NOT NULL`, composite unique keys and FKs include `tenant_id` |
| Cache/Redis | Keys prefixed `t:{tenantId}:` |
| Files | Object keys prefixed `tenants/{tenantId}/` |
| Async jobs | Job payload carries `tenantId`; context restored on execution |
| Audit | Every audit row stores `tenant_id` |

### 6.4 Rules
- Every tenant-owned entity extends `TenantOwnedEntity` (`tenant_id`, audit fields, `version`).
- Cross-tenant "not found" responses use **404**, not 403, to avoid confirming existence.
- IDs are UUIDs (non-guessable) for external exposure; internal numeric keys are never exposed.

## 7. Authentication Design

| Concern | Design |
|---|---|
| Password hashing | Argon2id or bcrypt (cost tuned), per-user salt, never logged |
| Access token | JWT, short TTL (e.g., 10–15 min), claims: `sub`, `tenantId`, `roles`, `jti`; signed with a strong secret/key from environment |
| Refresh token | Opaque random value; stored **hashed** in DB with `family_id`, `expires_at`, `revoked_at`, device metadata |
| Rotation | Each refresh issues a new token and revokes the old one; **reuse of a revoked token revokes the entire family** |
| Transport | Refresh token in `HttpOnly; Secure; SameSite` cookie; access token held in memory on the client (not `localStorage`/`sessionStorage`) |
| CSRF | Refresh/logout endpoints use cookies → protected via SameSite + CSRF token or custom header check |
| Brute force | Redis rate limits per IP and per account; progressive lockout after N failures; uniform error messages |
| Verification / Reset | Single-use, hashed, expiring tokens; reset never reveals account existence |
| Startup safety | Application **fails to start** if JWT secret is missing, short or a known placeholder |
| MFA-ready | `mfa_enabled`, `mfa_secret` (encrypted) columns and a second-step login state reserved; enforcement deferred |

## 8. Authorization Design

### 8.1 Model
```text
User ─▶ Role(s) ─▶ Permission(s)        (module + action, tenant-scoped roles)
                     │
                     ├─ Resource policy (relationship-based)
                     └─ Field policy (data sensitivity)
```

### 8.2 Permission Catalog
Permissions are strings `MODULE_ACTION`, e.g. `PATIENT_VIEW`, `PATIENT_EXPORT`, `PRESCRIPTION_CREATE`, `BILLING_VIEW`, `DOCUMENT_DOWNLOAD`. Catalog is defined in code and seeded by Flyway; roles are per-tenant data mapping to permissions.

### 8.3 Enforcement
- **Endpoint level:** `@PreAuthorize("hasAuthority('PATIENT_VIEW')")` (or a custom `@RequirePermission`) on every controller method; a startup/architecture test fails if an endpoint lacks it.
- **Resource level:** a `PatientAccessPolicy` (and similar) bean is called in the **service** layer: e.g., `canAccess(user, patient)` checks assigned doctor, active visit/appointment, care team, or admin policy. List queries apply the same rule as a query predicate (not post-filtering) to keep pagination correct.
- **Cross-doctor reads:** doctors in the same hospital can see prescriber attribution per PRD FR-9.3; the read is audited.
- **Field level:** response mapping goes through a `FieldMaskingService` driven by permissions (e.g., `PATIENT_VIEW_DIAGNOSIS`, `PATIENT_VIEW_NOTES`); masked fields are omitted, not blanked with misleading values.
- **Deny by default:** unmapped routes return 401/403.

## 9. Data Model

### 9.1 Conventions
- Primary key: `id BINARY(16)`/UUID (v7 preferred for index locality) unless noted.
- All tenant tables: `tenant_id`, `created_at`, `created_by`, `updated_at`, `updated_by`, `version` (optimistic locking).
- Timestamps in UTC (`DATETIME(6)`); tenant timezone applied at presentation.
- Enums stored as strings with check constraints, not ordinals.
- Soft delete (`deleted_at`) or status columns for clinical/business entities; no destructive cascades on clinical data.
- Every FK column is indexed; composite indexes lead with `tenant_id`.
- Schema changes **only** through Flyway migrations.

### 9.2 Core Tables

**Platform / identity**
| Table | Key columns | Notes |
|---|---|---|
| `tenants` | id, name, slug, status, timezone, verified_at | status: PENDING, ACTIVE, SUSPENDED |
| `users` | id, tenant_id, email, password_hash, status, failed_attempts, locked_until, mfa_* | unique (tenant_id, email) |
| `roles` | id, tenant_id, name, system_flag | |
| `permissions` | code, module, action | global catalog |
| `role_permissions` | tenant_id, role_id, permission_code | composite FK (tenant_id, role_id) → roles |
| `user_roles` | tenant_id, user_id, role_id | composite FK (tenant_id, user_id) → users |
| `refresh_tokens` | id, tenant_id, user_id, family_id, token_hash, expires_at, revoked_at | index on token_hash, family_id |
| `verification_tokens` | id, tenant_id, user_id, type, token_hash, expires_at, used_at | verify / reset / invite |
| `invitations` | id, tenant_id, email, role_id, department_id, token_hash, expires_at, status | |

**Organization**
| Table | Key columns |
|---|---|
| `departments` | id, tenant_id, name, status; unique (tenant_id, name) |
| `staff_profiles` | id, tenant_id, user_id, department_id, specialty, license_no |
| `doctor_availability` | id, tenant_id, staff_id, weekday, start, end |

**Patients & clinical**
| Table | Key columns |
|---|---|
| `patients` | id, tenant_id, mrn, first/last name, dob, sex, phone, address, status; unique (tenant_id, mrn) |
| `patient_assignments` | patient_id, staff_id, type, active_from, active_to (drives resource policy) |
| `allergies` | patient_id, substance, reaction, severity, status |
| `conditions` | patient_id, code/label, onset, status |
| `family_history`, `surgical_history` | patient_id, description, date |
| `appointments` | id, tenant_id, patient_id, doctor_id, start_at, end_at, status; unique (tenant_id, doctor_id, start_at) for active slots |
| `visits` | id, tenant_id, patient_id, doctor_id, appointment_id, status (OPEN/FINALIZED), finalized_at |
| `vitals` | visit_id, measurements, recorded_by, recorded_at |
| `clinical_notes` | visit_id, type, content, author_id, version_no |
| `diagnoses` | visit_id, code/label, type, author_id |
| `recommendations` | visit_id, content, author_id |
| `prescriptions` | id, tenant_id, patient_id, visit_id, prescriber_id, status |
| `prescription_versions` | prescription_id, version_no, created_by, created_at, reason |
| `prescription_items` | prescription_version_id, medicine_id, dose, frequency, duration_days, instructions |
| `medicines` | id, tenant_id, name, form, strength |
| `orders`, `lab_results`, `imaging_studies` | visit/patient links, status, result data, `document_id` |
| `addenda` | entity_type, entity_id, content, author_id, reason, created_at |

**Support**
| Table | Key columns |
|---|---|
| `documents` | id, tenant_id, patient_id, storage_key, content_type, size, checksum, uploaded_by, status |
| `invoices`, `invoice_items`, `payments` | tenant_id, patient_id, visit_id, totals, status |
| `notifications` | tenant_id, user_id, type, payload, read_at |
| `outbox_jobs` | id, tenant_id, type, payload, status, attempts, next_run_at |
| `audit_logs` | id, tenant_id, actor_id, action, entity_type, entity_id, ip, user_agent, metadata JSON, created_at (append-only) |

### 9.3 Key Indexes (initial)
- `patients (tenant_id, mrn)` unique; `(tenant_id, last_name, first_name)`; `(tenant_id, phone)`
- `appointments (tenant_id, doctor_id, start_at)`; `(tenant_id, patient_id, start_at)`
- `visits (tenant_id, patient_id, created_at DESC)`
- `prescriptions (tenant_id, patient_id, created_at DESC)`
- `audit_logs (tenant_id, entity_type, entity_id, created_at)`; `(tenant_id, actor_id, created_at)`
- Validate with `EXPLAIN` before adding more; remove unused indexes.

### 9.4 Double-Booking Prevention
Unique constraint on active slot `(tenant_id, doctor_id, start_at)` plus transactional overlap check. Constraint violations map to a `409 SLOT_UNAVAILABLE` response, making the guarantee independent of application race conditions.

## 10. Clinical Record Versioning

| Record | Strategy |
|---|---|
| Prescription | Immutable `prescription_versions`; edit = new version with `reason`; header points to current version |
| Visit content | Editable while `OPEN`; on **finalize**, becomes read-only |
| After finalization | Corrections via `addenda` (or new version) linking to the original; original retained |
| Notes/diagnoses | Versioned rows with author and timestamps |
| Deletion | Not permitted for finalized clinical data; status like `ENTERED_IN_ERROR` may be set with reason and audit |

Service layer enforces finalized-record rules; DB triggers/permissions may add a second guard later.

## 11. API Design

### 11.1 Standards
- Base path `/api/v1`; nouns, plural, kebab-case: `/api/v1/patients/{id}/allergies`.
- Correct methods/status codes: 200, 201 (+Location), 204, 400, 401, 403, 404, 409, 422, 429, 500.
- DTO-only contracts (MapStruct); entities never serialized.
- Bean Validation on request DTOs; unknown fields rejected (prevents mass assignment).
- Idempotency-Key support on create endpoints where duplicates are harmful (appointments, invoices, payments).
- Optimistic locking via `version`/`If-Match` → `409` on conflict.

### 11.2 Response Envelope
```json
{
  "success": true,
  "data": { },
  "meta": { "page": 0, "size": 20, "totalElements": 134, "totalPages": 7 },
  "timestamp": "2026-01-01T10:00:00Z",
  "traceId": "..."
}
```

### 11.3 Error Format (centralized `@RestControllerAdvice`)
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "One or more fields are invalid.",
    "fields": [ { "field": "dateOfBirth", "message": "Date of birth is required." } ]
  },
  "traceId": "..."
}
```
Generic "unexpected error" is reserved for true 500s (no stack traces to clients; details logged with `traceId`).

### 11.4 Pagination / Sorting / Filtering
`?page=0&size=20&sort=lastName,asc&q=...`; `size` capped (e.g., 100); allow-listed sort fields; unbounded lists forbidden.

### 11.5 Readable Labels
List/detail DTOs include display fields (`patientName`, `doctorName`, `departmentName`) or use lookup DTOs `{id, label}`, so the UI never needs to render raw IDs. Select/Combobox components bind to `{value: id, label}` pairs.

### 11.6 Documentation Exposure
OpenAPI available in dev; in production it is disabled or protected behind authentication/network restrictions.

## 12. File Storage Design

- `StorageService` interface with `MinioStorageService` (local) and `S3StorageService` (prod), selected by profile.
- Object key: `tenants/{tenantId}/patients/{patientId}/{uuid}`; original filenames stored as metadata only (no path traversal).
- Upload validation: extension + MIME allow-list, magic-byte sniffing, max size, filename sanitization, checksum stored; optional malware scanning hook.
- Download: authorization check, then **short-lived pre-signed URL** or streamed response; every download audited.
- Buckets private; server-side encryption enabled in S3.

## 13. Redis Usage

| Use | Key pattern | TTL |
|---|---|---|
| Rate limits | `t:{tid}:rl:{scope}:{id}` / `rl:ip:{ip}` | window-based |
| OTP / MFA challenge | `otp:{userId}` | minutes |
| Short-lived tokens | `st:{hash}` | minutes |
| Selective cache (medicines, permissions) | `t:{tid}:cache:{name}:{key}` | bounded + explicit invalidation |
| Notification state | `t:{tid}:notif:{userId}` | as needed |

Not used as a source of truth; the app must degrade safely if Redis is unavailable (security-critical limiters fail closed for auth endpoints). Never cache clinical PHI without an explicit ADR.

## 14. Asynchronous Processing

MVP: Spring `@Async` / scheduled workers backed by a transactional **outbox table** (`outbox_jobs`) for emails, PDF generation, exports and notifications, with retries and backoff. Jobs carry `tenantId`.
Later (on evidence): RabbitMQ/SQS/Kafka replaces the outbox transport without changing producers (interface `JobPublisher`).

## 15. Security Controls Matrix

| Threat | Control |
|---|---|
| Broken access control / IDOR | Permission + resource policy + tenant filter; UUIDs; 404 on foreign IDs |
| Tenant escape | §6 layered isolation; automated cross-tenant tests |
| XSS | Output encoding, no `dangerouslySetInnerHTML` on user data, strict CSP, sanitize rich text |
| CSRF | SameSite cookies, CSRF token on cookie-authenticated endpoints |
| SQL injection | JPA/parameterized queries only; native queries reviewed |
| SSRF | No user-controlled outbound URLs; allow-list for integrations |
| Brute force / enumeration | Rate limit, lockout, uniform responses |
| Secrets exposure | Env/secret manager; startup validation; no committed `.env`; log redaction |
| Insecure uploads | §12 validation, private buckets |
| Mass assignment | Explicit DTOs, reject unknown properties |
| Sensitive data in logs | No PHI/passwords/tokens in logs; masking |
| Management exposure | Actuator/Prometheus/Swagger bound to internal network or authenticated |
| Transport | TLS everywhere; HSTS |
| Data at rest | Encrypted DB volumes and S3 SSE; sensitive columns encrypted where warranted |
| Headers/CORS | HSTS, X-Content-Type-Options, frame-ancestors, Referrer-Policy; CORS allow-list per environment |

Compliance wording: controls **support** HIPAA/GDPR/SOC 2-oriented requirements; no compliance claim is made.

## 16. Audit Design

- Written by a `AuditService` invoked from services (and an aspect for annotated methods); async but durable through the outbox.
- Events: login success/failure, token reuse, password reset, role/permission changes, patient create/update, visit finalize/amend, prescription version, document upload/download, exports, cross-doctor reads, tenant suspension.
- Append-only: application DB user has no UPDATE/DELETE on `audit_logs`; retention per policy.
- Never stores secrets or full clinical payloads — stores entity references and change metadata.

## 17. Observability

| Signal | Implementation |
|---|---|
| Logs | Structured JSON (Logback encoder), fields: `traceId`, `tenantId`, `userId`, `route`, latency; PHI redacted |
| Metrics | Micrometer → Prometheus: request latency/error rate/throughput, DB & Redis latency, JVM memory/GC/threads, HikariCP pool |
| Traces | OpenTelemetry (later phase) propagated via `traceId` |
| Health | Liveness/readiness endpoints, DB/Redis/storage checks, container health checks, graceful shutdown |
| Access | Management endpoints on a separate port/network, not public |

## 18. Frontend Architecture

- **Next.js App Router**, feature-based folders; server components for static shells, client components for interactive clinical UIs.
- **State:** TanStack Query for server state (default `staleTime`, targeted invalidation); Redux Toolkit only for cross-cutting client state (auth session, UI prefs).
- **Forms:** React Hook Form + Zod schemas mirroring backend validation; server `fields[]` errors mapped to the exact field; required-field asterisk built into shared form field component.
- **API layer:** single Axios instance with interceptors (attach access token, silent refresh on 401 using cookie, error normalization, `traceId` surfacing).
- **Authorization UX:** permission hooks/guards hide unavailable actions but are **never** the security boundary.
- **Shared components:** `SelectField`/`Combobox` accepting `{value,label}` options (prevents ID-instead-of-name bugs); `DataTable` with pagination, empty/loading/error states; `ConfirmDialog` for destructive actions; submit buttons disabled while pending.
- **Security:** strict CSP, no tokens in web storage, sanitize any rendered rich text, no user data in `dangerouslySetInnerHTML`.
- **Performance:** debounced search, pagination/virtualization for long histories, code-splitting, image optimization.
- **Accessibility:** keyboard navigation, focus management, ARIA labels, WCAG-oriented contrast; animations minimal (Framer Motion only where they add clarity).

## 19. Testing Strategy

```text
              E2E (Playwright)
            /                 \
     Integration            Component
    (Testcontainers)        (RTL/Vitest)
            \                 /
                 Unit (JUnit / Vitest)
```

| Type | Tools / scope |
|---|---|
| Unit | JUnit 5, Mockito; services, policies, mappers |
| Repository / Integration | Testcontainers (MySQL, Redis, MinIO); Flyway migrations run from scratch |
| Security | Spring Security test; unauthenticated, wrong role, expired token, wrong tenant |
| **Tenant isolation** | Tenant A vs Tenant B across patients, appointments, visits, prescriptions, invoices, documents, notifications, audit logs, search, pagination, exports, downloads, cache, background jobs |
| API contract | Response envelope, error format, pagination, status codes |
| Architecture tests | ArchUnit: controllers only call services; every endpoint has permission annotation; no entity in controller signature |
| Component | Forms, tables, selects (labels not IDs) |
| E2E | Register → verify → admin login → department → invite doctor → doctor login → patient → appointment → consult → prescription → invoice (+ document upload, history, logout) |
| Failure cases | Duplicate record, double booking, missing resource, oversized/unsupported file, rate limit exceeded, invalid input |
| Performance | k6/Gatling on key list/search endpoints; `EXPLAIN` review |

CI gates: compile, unit + integration tests, migration check, frontend build/lint/typecheck, dependency vulnerability scan.

## 20. Deployment and Evolution

### 20.1 Environments
`local` (Docker Compose — default profile runs MySQL, Redis, MinIO, Nginx; `--profile full` additionally runs backend and frontend containers, CONF-2) → `staging` → `production`. Environment variables and secrets per environment; no dev CORS origins or debug endpoints in production.

### 20.2 First Production Deployment
```text
Vercel (Next.js) ──HTTPS──▶ Nginx ▶ Spring Boot on EC2 ──▶ Managed MySQL · Redis · S3
```
Single instance is acknowledged as **not** highly available.

### 20.3 Evolution to HA
```text
Internet → Load Balancer → Backend A / B / C → Managed DB (Primary + Replica)
                                             → Redis (HA)
```
Enabled by the stateless design (§3): no local disk state, no in-memory sessions, jobs in DB outbox, shared Redis for limits.

### 20.4 Operational Requirements
CI/CD pipeline (Phase 26), automated DB backups and restore drill (Phase 28), health checks, graceful shutdown, container health checks, secrets from a manager, rollback plan.

## 21. Phase-to-Design Mapping

| Phase | Primary design sections |
|---|---|
| 1 Repo & Infra | §5, §20.1 |
| 2 Backend Foundation | §3, §11, §17 (logs) |
| 3 Database Foundation | §9, §10 |
| 4 Multi-Tenancy | §6 |
| 5 Authentication | §7, §13 |
| 6 Authorization | §8 |
| 7 Frontend Foundation | §18 |
| 8–9 Org & Staff | §9.2, §7 (invitations) |
| 10 Patients | §8.3, §9.2 |
| 11 Appointments | §9.4 |
| 12–14 Clinical, History, Prescriptions | §10 |
| 15–16 Lab, Documents | §12 |
| 17 Billing | §9.2, §11.1 (idempotency) |
| 18 Notifications | §14 |
| 19 Audit | §16 |
| 20–21 Search, Dashboards | §9.3, §13 |
| 22 Performance | §9.3, §13, §19 |
| 23 Security Hardening | §15 |
| 24 Testing | §19 |
| 25 Observability | §17 |
| 26–29 CI/CD → Readiness | §20 |

## 22. Architecture Decision Records (Initial)

| ADR | Decision | Status |
|---|---|---|
| ADR-001 | Modular monolith with strict module boundaries | Accepted |
| ADR-002 | Shared schema + `tenant_id` with Hibernate filtering; revisit for large tenants | Accepted |
| ADR-003 | Refresh token in HttpOnly cookie, access token in memory | Accepted |
| ADR-004 | Outbox-based background jobs; no broker for MVP | Accepted |
| ADR-005 | Versioned/immutable clinical records with addenda | Accepted |
| ADR-006 | UUID external identifiers; 404 for foreign-tenant resources | Accepted |
| ADR-007 | Compliance-ready language only; no compliance claims | Accepted |

## 23. Open Technical Questions

**Status: TQ-1…TQ-7 accepted at their defaults at the Phase 0 sign-off (2026-09-30); change only via an ADR.**

| ID | Question | Decision (= default) |
|---|---|---|
| TQ-1 | Hibernate `@TenantId` vs `@Filter` for enforcement (also depends on native-query needs) | `@TenantId` (Hibernate 6.x) with repository tests proving isolation |
| TQ-2 | Password hasher (Argon2id vs bcrypt) | Argon2id if library approved, else bcrypt |
| TQ-3 | Email provider | SMTP abstraction; choose in Phase 18 |
| TQ-4 | Tenant activation flow (PRD OQ-1) | Auto after email verification |
| TQ-5 | Doctor–patient access policy (PRD OQ-2) | Assigned/previously treated; audit cross-doctor reads |
| TQ-6 | Sensitive column encryption scope | MFA secrets and tokens initially; extend per SECURITY.md |
| TQ-7 | Multi-branch hospitals per tenant | Not in MVP; schema keeps room (`hospital_id` nullable-ready) |
