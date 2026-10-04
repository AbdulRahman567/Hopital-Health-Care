# Healthcare-HMS — Security Specification

> **Compliance positioning:** the system is designed with controls that *support* HIPAA / GDPR / SOC 2-oriented security and privacy requirements. It is **compliance-ready**, not certified or "compliant". Actual compliance also depends on infrastructure, policies, contracts, procedures, retention rules and incident response outside the codebase. Never claim compliance in code, UI or docs.

## 1. Security Objectives
1. Confidentiality of patient data and strict tenant isolation.
2. Integrity of clinical records (versioned, attributable).
3. Availability sufficient for clinical use.
4. Accountability through audit logging.

## 2. Threat Model (summary)
| Threat actor | Goal | Primary controls |
|---|---|---|
| Malicious tenant user | Read another hospital's data | Tenant isolation chain, 404 on foreign IDs, isolation tests |
| Over-privileged internal role | See clinical data outside duty | Permissions, resource and field policies, audit |
| External attacker | Account takeover, data theft | Rate limits, lockout, refresh rotation, TLS, CSP |
| Compromised token | Session abuse | Short-lived access tokens, rotation with reuse detection |
| Malicious upload | Malware, path traversal, XSS | Validation, private storage, download authorization |
| Insider with DB/infra access | Bulk exfiltration | Least-privilege DB users, encryption, audit, secrets management |

## 3. Authentication
- Passwords hashed with Argon2id (or bcrypt with tuned cost); minimum length and breached-password check.
- Access token: JWT, 10–15 min; refresh token: opaque, hashed at rest, rotated on every use; reuse revokes the token family.
- Refresh token in `HttpOnly; Secure; SameSite` cookie; access token in memory only.
- Lockout after repeated failures with progressive delay; per-IP and per-account rate limits.
- Uniform responses for login, reset and verification (no account enumeration).
- Email verification and password reset via single-use, hashed, expiring tokens.
- MFA-ready data model and flow; enforcement is a later phase.
- Startup fails if JWT secret is missing, too short, or a known placeholder.

## 4. Authorization
- Default deny; every endpoint declares a permission.
- Tenant ID always derived from the authenticated principal.
- Resource-level policies (assigned/treated patients) enforced in services and list queries.
- Field-level masking driven by permissions.
- Cross-tenant and cross-doctor sensitive reads are audited.
- Frontend checks are UX only.

### 4.1 Permission catalog (`MODULE_ACTION`, 53 codes)
The catalog exists twice and the two halves must agree: the **seed** (`V2__roles_permissions_and_seed.sql`, P3.3) and the **code** (`authz/PermissionCatalog`, P6.1). `PermissionCatalogTest` compares the two **sets in both directions**, so a row nobody can reference and a code nobody can grant both fail the build; `MigrationV2IT` freezes the row count at **53**. `code = module || '_' || action` and the enum constant *is* the code, so there is no second spelling to drift. Nothing outside `PermissionCatalog` may spell a permission as a raw string — annotations carry `@RequirePermission(PermissionCatalog.…)` constants, bundles reference `PermissionCatalog.code()`, and `EndpointPermissionArchUnitTest` fails the build on a code that is not a catalog member.

**Growth rule:** a new permission is a new `Vx` migration **and** a new constant in one commit, together with the two sanctioned bumps that forces (`MigrationV2IT.CATALOG_SIZE`, `MigrationIT` migration count). Phase 6 shipped **no migration** (decision D6).

| Module | Codes | # |
|---|---|---|
| TENANT | `TENANT_VIEW`, `TENANT_UPDATE`, `TENANT_SUSPEND`, `TENANT_ACTIVATE` | 4 |
| DEPARTMENT | `DEPARTMENT_VIEW`, `DEPARTMENT_CREATE`, `DEPARTMENT_UPDATE`, `DEPARTMENT_DELETE` | 4 |
| STAFF | `STAFF_VIEW`, `STAFF_CREATE`, `STAFF_UPDATE`, `STAFF_DEACTIVATE`, `STAFF_INVITE` | 5 |
| ROLE | `ROLE_VIEW`, `ROLE_CREATE`, `ROLE_UPDATE`, `ROLE_DELETE` | 4 |
| PATIENT | `PATIENT_VIEW`, `PATIENT_CREATE`, `PATIENT_UPDATE`, `PATIENT_EXPORT`, `PATIENT_VIEW_DIAGNOSIS`, `PATIENT_VIEW_NOTES` | 6 |
| APPOINTMENT | `APPOINTMENT_VIEW`, `APPOINTMENT_CREATE`, `APPOINTMENT_UPDATE`, `APPOINTMENT_CANCEL` | 4 |
| VISIT | `VISIT_VIEW`, `VISIT_CREATE`, `VISIT_UPDATE`, `VISIT_FINALIZE` | 4 |
| PRESCRIPTION | `PRESCRIPTION_VIEW`, `PRESCRIPTION_CREATE`, `PRESCRIPTION_UPDATE`, `PRESCRIPTION_EXPORT` | 4 |
| MEDICINE | `MEDICINE_VIEW`, `MEDICINE_CREATE`, `MEDICINE_UPDATE` | 3 |
| LAB | `LAB_VIEW`, `LAB_CREATE`, `LAB_UPDATE` | 3 |
| DOCUMENT | `DOCUMENT_VIEW`, `DOCUMENT_UPLOAD`, `DOCUMENT_DOWNLOAD` | 3 |
| BILLING | `BILLING_VIEW`, `BILLING_CREATE`, `BILLING_UPDATE` | 3 |
| AUDIT | `AUDIT_VIEW` | 1 |
| NOTIFICATION | `NOTIFICATION_VIEW`, `NOTIFICATION_MANAGE` | 2 |
| SEARCH | `SEARCH_QUERY`, `SEARCH_EXPORT` | 2 |
| DASHBOARD | `DASHBOARD_VIEW` | 1 |
| **Total** | | **53** |

### 4.2 Platform-only codes
`TENANT_SUSPEND` and `TENANT_ACTIVATE` carry out a **platform** operation on a tenant row and are reserved for the GAP-1 platform tenant's own roles at P8.5. They are rejected on any tenant role → **422 `VALIDATION_FAILED`, `error.fields[]` → `permissionCodes`** (`RoleService`). `PermissionCatalog.PLATFORM_ONLY` is asserted to contain exactly those two, so even the hospital's own administrator (who holds the other 51 codes) can never hold them.

### 4.3 System role bundles (provisioned per tenant at registration)
Six bundles are defined in code (`authz/SystemRoleBundle`, decision D4) and written per tenant by `SystemRoleProvisioner`, **idempotently inside the registration transaction**; the registering admin is enrolled into `ADMIN` through `user_roles`. A Flyway migration cannot do this (roles are tenant rows and future tenants do not exist yet), so the bundles are code and the seed stays 53 permission rows.

`system_flag = 1` roles are **immutable**: name not renameable, not deletable, permission set read-only — customization is what FR-3.5 custom roles are for. Delete of a role still assigned to users → **409 `RESOURCE_IN_USE`**; duplicate name → **409 `DUPLICATE_RESOURCE`** with `error.fields[] → name`; foreign `roleId` → **404** (ADR-006).

| Bundle | Permission codes | Modules touched |
|---|---|---|
| `ADMIN` (hospital administrator) | **51** — every catalog row except the two platform-only codes | 16 |
| `DOCTOR` | **30** — consult, diagnose, prescribe, order, document, view history | 12 |
| `NURSE` | **18** — vitals/visits/queue, prescriptions **view only**, no diagnosis or notes | 11 |
| `RECEPTIONIST` | **12** — register patients, book appointments, documents | 6 |
| `LAB_TECHNICIAN` | **11** — order-scoped lab + document handling | 7 |
| `BILLING` | **11** — invoices, encounter readable but no clinical detail | 9 |

Bundle → module matrix (✓ = at least one code of that module; 133 `role_permissions` rows per tenant in total):

| Module | ADMIN | DOCTOR | NURSE | RECEPTIONIST | LAB_TECH | BILLING |
|---|:--:|:--:|:--:|:--:|:--:|:--:|
| TENANT | ✓ | – | – | – | – | – |
| DEPARTMENT | ✓ | ✓ | – | – | – | – |
| STAFF | ✓ | ✓ | ✓ | – | – | – |
| ROLE | ✓ | – | – | – | – | – |
| PATIENT | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| APPOINTMENT | ✓ | ✓ | ✓ | ✓ | – | ✓ |
| VISIT | ✓ | ✓ | ✓ | – | ✓ | ✓ |
| PRESCRIPTION | ✓ | ✓ | ✓ | – | – | ✓ |
| MEDICINE | ✓ | ✓ | ✓ | – | – | – |
| LAB | ✓ | ✓ | ✓ | – | ✓ | – |
| DOCUMENT | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| BILLING | ✓ | – | – | – | – | ✓ |
| AUDIT | ✓ | – | – | – | – | – |
| NOTIFICATION | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| SEARCH | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| DASHBOARD | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

Two deliberate rules: **no non-admin bundle holds any `ROLE_*` code** (role management is an administrator's job, and P6.6's matrix depends on a non-admin caller being denied it), and PRD §6.4's "Configurable" clinical cells default **off** for `NURSE` — fail closed; a hospital that wants them grants them through a custom role.

### 4.4 Custom roles (FR-3.5)
A tenant administrator creates roles from catalog codes only (`POST /roles` → `ROLE_CREATE`). Escalation guards, all tested in `RoleManagementTest`:
- **Grant scope** — a creator may only grant permissions **they themselves hold** → 422 otherwise (anti-privilege-escalation-by-proxy; `ADMIN` holds 51, so it is workable).
- **Catalog only** — an unknown code → 422 **and no row written** (FK to `permissions` + code check).
- **Platform-only blocked** — see §4.2.
- **System roles immutable** — see §4.3.
- **Tenant scope** — every read and write goes through `@TenantId`; a foreign `roleId` is `Optional.empty()` → 404, never 403.

### 4.5 Endpoint → permission map
| Endpoint | Permission |
|---|---|
| `GET /api/v1/roles`, `GET /api/v1/roles/{roleId}`, `GET /api/v1/permissions` | `ROLE_VIEW` |
| `POST /api/v1/roles` | `ROLE_CREATE` |
| `PUT /api/v1/roles/{roleId}` | `ROLE_UPDATE` |
| `DELETE /api/v1/roles/{roleId}` | `ROLE_DELETE` |
| `GET /api/v1/staff`, `GET /api/v1/staff/{userId}` | `STAFF_VIEW` |

`GET /permissions` is gated by `ROLE_VIEW` because the frozen catalog has no `PERMISSION` module (decision D5). The eight `/api/v1/auth/*` POSTs stay **anonymous** and are allow-listed in ArchUnit **by class+method, never by package**. Everything else keeps `anyRequest().denyAll()` — an undeclared route is 401 (no token) / 403 (authenticated), which `EndpointPermissionMatrixTest` asserts over the live mapping rather than a hand-written list.

**Status discipline (TESTING §8, ADR-006):** no token → **401** `UNAUTHENTICATED` · token without the permission → **403** `ACCESS_DENIED` · foreign-tenant resource → **404** `NOT_FOUND`. Wrong-role is never 404; wrong-tenant is never 403.

### 4.6 Enforcement layers
1. **URL** — `SecurityConfig` `denyAll()` fallback plus `.authenticated()` matchers for exactly `/api/v1/roles/**` and `/api/v1/permissions` (defense in depth; method security decides).
2. **Method** — `@RequirePermission("CODE")` + `@EnableMethodSecurity` + `PermissionAuthorizationManager` (decision D3). Authorities are resolved from the **database per request, after tenant binding** (`PermissionAuthoritiesFilter`, two indexed JPQL queries, decision D1): a role edit takes effect on the **next request**, no re-login, no token re-issue. **Fail closed** — token present + zero rows → empty authorities → 403; never "authenticated ⇒ permitted", never role names, never a default grant.
3. **Architecture** — `EndpointPermissionArchUnitTest` (decision D9): every public controller method annotated or on the 8-route allow-list (a canary test proves the rule can fail) · every declared code is a catalog member · controllers touch services only · module dependency allow-list per ARCHITECTURE §3 · `nativeQuery = true` only in the documented SQL-guardrail classes (ISO-1).
4. **Resource policy** — §4.7.
5. **Field masking** — §4.8.
6. **JWT `roles` claim is display-only** (decision D2) — populated at login/refresh for the UI, never read for an authorization decision.

### 4.7 Resource-policy contract (FR-3.3, OQ-2)
`ResourcePolicy<T>` in `authz` exposes `canRead` / `requireRead` plus `readPredicate(...)` so a list endpoint applies the rule **as a query predicate, never post-filtering** (pagination stays correct, TDD §8.3). The contract is **deny by default**: no known relationship ⇒ deny. The OQ-2 default — *assigned **or** previously treated, cross-doctor sensitive reads audited* — is encoded as the framework contract with a **log seam** today; the `audit_logs` row lands at P14.6/P19 (recorded deferral, not a gap). Proven two ways at P6.4: a **real** policy over an existing entity (`UserSelfOrStaffPolicy`: own account, or any caller with `STAFF_VIEW`) through a real endpoint, and `ResourcePolicyTest` driving the framework with a test-double assignment set so *unassigned doctor denied / assigned allowed / previously-treated allowed* passes as written. The real `PatientAccessPolicy` over `patients`/`patient_assignments` wires at **P10.8** (decision D7 — Phase 10 owns those migrations).

### 4.8 Field-level masking rules (FR-3.4, D8)
`FieldMaskingService.mask(response, authorities)` removes **field paths** — never blanks them (API §6, DESIGN_SYSTEM §7) — from a Jackson tree. Rules are a per-type table of `requiredPermission → field paths`; an **unknown or absent permission omits the field** (fail closed).

| Field path | Required permission | Bundles that keep it |
|---|---|---|
| `diagnosis` | `PATIENT_VIEW_DIAGNOSIS` | `ADMIN`, `DOCTOR` |
| `notes` | `PATIENT_VIEW_NOTES` | `ADMIN`, `DOCTOR` |
| `vitals` | `VISIT_VIEW` | `ADMIN`, `DOCTOR`, `NURSE`, `LAB_TECHNICIAN`, `BILLING` |

`RECEPTIONIST` holds **none** of the three, so a receptionist's payload contains no `diagnosis`/`notes`/`vitals` key at all — exactly what `FieldMaskingTest` asserts. First production wiring: P10.8 (receptionist patient detail), P12.7, P17.4 (decision D8).

## 5. Tenant Isolation Controls
| Layer | Control |
|---|---|
| API | No tenant identifiers accepted as authority |
| Service | Tenant from `TenantContext` only |
| Repository | Hibernate tenant filter; native query review |
| Database | `tenant_id NOT NULL`; composite keys/FKs |
| Cache | `t:{tenantId}:` key prefix |
| Storage | `tenants/{tenantId}/` object prefix |
| Jobs | Tenant in payload; context restored |
| Logs/Audit | `tenantId` on every entry |

## 6. Input, Output and Web Security
| Area | Requirement |
|---|---|
| Validation | Jakarta Validation on all DTOs; reject unknown fields |
| SQL injection | Parameterized JPA queries; no string-built SQL |
| XSS | Output encoding; no raw HTML rendering of user data; sanitize rich text; strict CSP |
| CSRF | SameSite cookies plus CSRF token/header on cookie-authenticated endpoints |
| CORS | Explicit per-environment origin allow-list; no wildcard with credentials |
| SSRF | No user-controlled outbound URLs; allow-listed integrations only |
| Headers | HSTS, `X-Content-Type-Options: nosniff`, `frame-ancestors 'none'`, `Referrer-Policy`, `Permissions-Policy` |
| Mass assignment | Dedicated request DTOs; server-controlled fields never bound |
| Error handling | No stack traces, SQL or class names to clients; `traceId` only |

## 7. File Upload Security
- Extension **and** MIME allow-list; magic-byte verification; max size per type.
- Generated storage keys; original filename kept as metadata only.
- Private buckets; server-side encryption; short-lived pre-signed downloads after authorization.
- Optional antivirus scanning hook.
- Every upload/download audited.

## 8. Secrets & Configuration
- Secrets only from environment variables or a secret manager; never in source.
- `.env` files gitignored; provide `.env.example` with non-secret placeholders that **cannot** boot the app in production.
- Separate secrets per environment; rotation procedure documented.
- Secret scanning in CI.

## 9. Data Protection
- TLS 1.2+ everywhere (browser→edge, edge→backend, backend→DB/Redis/S3).
- Encryption at rest: DB volumes, S3 SSE, Redis persistence if enabled.
- Column-level encryption for MFA secrets and other high-sensitivity values.
- PHI never in logs, URLs, metrics labels or error messages.
- Data minimization in API responses and exports.

## 10. Audit Logging
Log: authentication events, token reuse, password changes, role/permission changes, patient and clinical record creation/change/finalize/amend, prescription versions, document access, exports, cross-doctor reads, tenant suspension.
Requirements: append-only, tenant-scoped, includes actor, action, entity reference, timestamp, IP, user agent, `traceId`; no secrets or full clinical payloads.

## 11. Management Surface
- Swagger/OpenAPI: disabled or authenticated in production.
- Actuator: only `health` liveness/readiness exposed publicly if needed; everything else internal-only.
- Prometheus: reachable only from the internal network/monitoring host.
- Admin/debug endpoints never enabled in production.

## 12. Rate Limiting (Redis-backed)
| Scope | Example limit |
|---|---|
| Login | 5/min per account, 20/min per IP |
| Registration / resend verification | 3/hour per email, 10/hour per IP |
| Password reset | 3/hour per account |
| Search/export | Stricter per-user limits |
Auth endpoints **fail closed** if the limiter is unavailable.

## 13. Dependency & Supply Chain
- Automated dependency scanning (backend and frontend) in CI; critical/high fixed before release.
- Pin versions; review new dependencies; keep framework versions patched (e.g., Next.js security advisories).
- Container images from minimal, maintained bases; scanned in CI.

## 14. Security Testing
- Unit/integration: authentication abuse cases, wrong role, wrong tenant, expired/reused tokens.
- Automated tenant-isolation suite (see TESTING.md).
- ArchUnit rule: every endpoint carries a permission.
- Periodic AI-assisted security review after each security-sensitive phase (AI_DEVELOPMENT_GUIDE.md).
- Before production: full hardening pass (Phase 23) and independent penetration test recommended.

## 15. Incident Response (baseline)
- `traceId` correlation across logs.
- Ability to revoke all sessions for a user/tenant.
- Tenant suspension switch.
- Documented runbook: detect → contain → eradicate → recover → review. Legal/regulatory notification duties are handled by the operating organization.

## 16. Security Review Checklist (per phase)
- [ ] Endpoints authenticated and permissioned
- [ ] Tenant isolation verified with tests
- [ ] Inputs validated; outputs encoded
- [ ] No sensitive data in logs/errors
- [ ] Rate limits on abuse-prone endpoints
- [ ] Audit events emitted
- [ ] No new public management endpoints
- [ ] Dependencies scanned
