# Healthcare-HMS — Features & Progress Tracker

| | |
|---|---|
| **Purpose** | Single checklist of every feature this product will ship. Tick items as they are implemented and verified. |
| **Created** | 2026-10-02 — Phases 0–2 complete |
| **Tracks** | PRD.md §5 (functional requirements), ROADMAP.md (phases/tasks), PRD §9 (non-functional) |
| **Not a source of truth** | PRD/ROADMAP/TDD define *what* and *how*; this file only records *progress*. Never tick without the ROADMAP `Verify:` evidence (ENGINEERING_RULES §2.4). |

**Status legend:** `- [ ]` not implemented · `- [x]` implemented & verified
**Priority:** **P0** = must-have for MVP · **P1** = important · **P2** = later
**How to use:** implement features in roadmap phase order → run the verify command → tick the box → when a group is fully ticked, flip its row in §1.

---

## 1. Progress Summary

| # | Feature group | Roadmap phase(s) | Priority mix | Status |
|---|---|---|---|---|
| A | Documentation & Architecture | 0 | – | ☑ Done |
| B | Repository & Infrastructure | 1 | – | ☑ Done |
| C | Backend Foundation | 2 | P0 | ☑ Done |
| D | Database Foundation | 3 | P0 | ☑ Done |
| E | Multi-Tenancy Isolation | 4 | P0 | ☑ Done (ADR-006 HTTP 404 proven at P6.7) |
| F | Authentication & Session | 5 | P0 | ☑ Done (2026-10-04, merged `ce01e76`, 236 tests; P5.11 fixed SEC-1/SEC-2/SEC-3) |
| G | Authorization / RBAC | 6 | P0 | ☑ Done (2026-10-04, `phase/06-authorization`, 294 tests; review findings SEC-6…SEC-10 recorded in PROJECT_CONTEXT §11) |
| H | Frontend Foundation | 7 | P0 | ☐ Not started |
| I | Hospital & Organization Mgmt | 8 | P0/P1 | ☐ Not started |
| J | Staff & Doctor Management | 9 | P0 | ☐ Not started |
| K | Patients | 10 | P0/P1 | ☐ Not started |
| L | Appointments & Queue | 11 | P0/P2 | ☐ Not started |
| M | Clinical Consultation | 12 | P0 | ☐ Not started |
| N | Medical History | 13 | P0 | ☐ Not started |
| O | Prescriptions & Medicines | 14 | P0/P1 | ☐ Not started |
| P | Lab & Imaging | 15 | P1 | ☐ Not started |
| Q | Documents & Files | 16 | P1 | ☐ Not started |
| R | Billing | 17 | P1 | ☐ Not started |
| S | Notifications | 18 | P0/P2 | ☐ Not started |
| T | Audit & Compliance Controls | 19 | P0/P1 | ☐ Not started |
| U | Search | 20 | P1 | ☐ Not started |
| V | Dashboards & Analytics | 21 | P2 | ☐ Not started |
| W | Performance Optimization | 22 | P0 (NFR) | ☐ Not started |
| X | Security Hardening | 23 | P0 (NFR) | ☐ Not started |
| Y | Automated Testing (completion) | 24 | P0 (NFR) | ☐ Not started |
| Z | Observability | 25 | P0 (NFR) | ☐ Not started |
| AA | CI/CD | 26 | P0 (NFR) | ☐ Not started |
| AB | Production Deployment | 27 | P0 (NFR) | ☐ Not started |
| AC | Disaster Recovery | 28 | P1 (NFR) | ☐ Not started |
| AD | Production Readiness | 29 | P0 (NFR) | ☐ Not started |
| AE | Future AI / Advanced | 30 | P2 | ☐ Not started |

**Overall: 7 / 31 groups done.**

---

## 2. Feature Checklists

### A. Documentation & Architecture — Phase 0 ☑

- [x] PRD: goals, roles, functional requirements, authorization model, open questions
- [x] TDD: stack, tenancy, auth, data model, API, testing, ADRs
- [x] Architecture, Engineering Rules, Roadmap, Project Context, Design System
- [x] DATABASE / API / SECURITY / TESTING / DEPLOYMENT / AI_DEVELOPMENT_GUIDE standards
- [x] Cross-document conflict review (CONF-1…6, GAP-1) — all decided
- [x] Phase 0 approval sign-off (OQ-1…6, TQ-1…7 accepted at defaults)

### B. Repository & Infrastructure — Phase 1 ☑

- [x] Monorepo layout: `backend/`, `frontend/`, `infra/`, `docs/`
- [x] Git init, `phase/NN-short-name` branches, conventional commits
- [x] Docker Compose stack: MySQL, Redis, MinIO, Nginx — all healthchecked, loopback-only ports
- [x] `--profile full` also runs backend/frontend containers (CONF-2)
- [x] Environment templates; `.env` gitignored; no secrets committed
- [x] Lint/format toolchain: Spotless (backend), ESLint + Prettier + `tsc --strict` (frontend)
- [x] README with verified local setup commands

### C. Backend Foundation — Phase 2 ☑

- [x] Spring Boot 3.5 / Java 21 skeleton with TDD §5 package layout (`com.healthcare.hms`)
- [x] Response envelope: success / paginated / error per API.md §3
- [x] Centralized `@RestControllerAdvice` with field-level 422 validation errors (`error.fields[]` names exact field)
- [x] Structured JSON logging with `traceId`; no PHI/secrets in logs
- [x] Health endpoints; actuator restricted (only health public)
- [x] OpenAPI enabled in dev, disabled/protected in prod profile
- [x] Fail-fast startup validation: missing/placeholder JWT secret aborts boot
- [x] Unit tests: envelope, errors, pagination helpers

### D. Database Foundation — Phase 3 ☑

- [x] Flyway integration; `ddl-auto=validate` in all profiles
- [x] Migration V1: `tenants`, `users` with audit columns + `tenant_id` rules (CONF-5)
- [x] Migration V2: `roles`, `permissions`, `role_permissions`, `user_roles` + permission seed
- [x] Migration V3: `audit_logs` append-only base table
- [x] Index rules enforced: every FK indexed, composite indexes lead with `tenant_id`
- [x] DB-level tenancy: `tenant_id NOT NULL` rejects NULL
- [x] Migration test from a clean database in one command (Testcontainers)

### E. Multi-Tenancy Isolation — Phase 4 ☑

- [x] `TenantContext` request-scoped holder (cleared in `finally`)
- [x] Tenant resolved from JWT claim only — missing claim rejected
- [x] Client tenant hints (`X-Tenant-ID`, body, path) ignored / rejected on mismatch
- [x] Hibernate tenant filtering (`@TenantId`) on every tenant-owned entity
- [x] Cross-tenant repository isolation suite (Tenant A vs Tenant B) — 0 failures
- [x] Tenant-prefixed Redis keys `t:{tenantId}:` and storage keys `tenants/{tenantId}/`
- [x] Tenant propagation helper for async job payloads
- [x] Foreign-tenant resources return **404**, never 403 (ADR-006) — repository primitive proven in P4.4/P4.5 (`findById` of a foreign row is `Optional.empty()`); the HTTP half closed at **P6.7** `WrongTenantTest` (foreign id → 404 on read/update/delete, body byte-identical to a random-UUID 404 after `timestamp`/`traceId` are stripped)

### F. Authentication & Session — Phase 5 ☑

- [x] **FR-2.1 (P0)** Email + password login with secure password hashing (Argon2id/bcrypt, TQ-2) — BCrypt cost 12 (D3), `POST /login` P5.3, `LoginTest`
- [x] **FR-1.1 (P0)** Hospital self-registration (admin name, email, hospital name, password) — `POST /register-hospital` P5.2, `RegistrationVerificationTest`
- [x] **FR-1.2 (P0)** Email verification on registration; unverified tenants cannot log in — P5.2, `VerificationService` + `TokenValues`
- [~] **FR-2.5 (P0)** Email verification for invited and self-registered users — self-registered leg proven in P5.2; the **invited-user leg lands with P9.3** (`UserInvitationIT`)
- [x] **FR-2.2 (P0)** Short-lived JWT access tokens (10–15 min) + refresh tokens with rotation and reuse detection (reuse revokes whole family) — 12 min / 7 d (D8), `JwtTokenService` P5.3, `RefreshTokenService` P5.4, `RefreshRotationReuseTest`
- [x] **FR-2.6 (P0)** Logout invalidates the refresh token server-side — `POST /logout` P5.5, `CookieLogoutTest`
- [x] Refresh token in HttpOnly/Secure/SameSite cookie; access token in memory only (never web storage) — `RefreshCookieBuilder` (D6); access token only ever in the login response body
- [x] CSRF protection on cookie-authenticated endpoints — `CustomHeaderCsrfFilter`, custom `X-Requested-With` header on `/refresh` + `/logout` (D5)
- [x] **FR-2.3 (P0)** Brute-force protection: Redis rate limiting + progressive account lockout (429 + `Retry-After`) — `RateLimitFilter`/`LockoutService` P5.6, `RateLimitPolicyTest` + `RateLimitLockoutTest`; keyed on the **client's** address behind a proxy since P5.11 (`server.forward-headers-strategy: native` + pinned `HMS_TRUSTED_PROXY`, SEC-1)
- [x] **FR-2.4 (P0)** Password reset via time-limited, single-use token; response never reveals account existence — `PasswordResetService` P5.7, `PasswordResetTest`
- [x] Uniform error messages for login/reset/verify — no account enumeration — one 401 for every login failure; uniform 202 for email-shaped requests (D9)
- [x] **FR-1.3 (P0)** Resend verification: rate limited, never times out or leaves the flow — P5.2 + `email-ip-limit` P5.6; the **async UI re-verification stays at P18.4**
- [~] **FR-1.4 (P0)** Tenant activation workflow (auto after verification, OQ-1) — never a manual DB edit — mechanism (slug → `TenantStatus.ACTIVE`) proven in P5.2; the **end-to-end `TenantActivationIT` stays at P8.4**
- [x] **FR-2.7 (P2)** MFA-ready architecture (TOTP/OTP hooks; enforcement deferred) — D12 login seam in `LoginService` + `mfa_enabled`/`mfa_enforced_at` columns in V1
- [x] Auth + tenant isolation tests: unauthenticated → 401; tenant B cannot read tenant A users — `AuthTenantIsolationTest` (10 tests, P5.8)

### G. Authorization / RBAC — Phase 6 ☑

- [x] **FR-3.1 (P0)** Every endpoint declares permission(s); default is deny (ArchUnit rule) — `@RequirePermission` on every controller method except the 8-route anonymous allow-list, `EndpointPermissionArchUnitTest` (6 tests, P6.3) + `denyAll()` fallback still the URL default; matrix proof at P6.6
- [x] **FR-3.2 (P0)** Tenant ID derived from authenticated principal, never from client — proven P4.2/P4.3 and re-verified on the new surface at **P6.7** (`X-Tenant-ID` mismatch → 404 before the controller, body `tenantId` → 422 with nothing written, `?tenantId=` ignored, no token + hint → 401)
- [x] Permission catalog `MODULE_ACTION` in code + Flyway seed (PATIENT_VIEW, PRESCRIPTION_CREATE, …) — `PermissionCatalog` == the V2 seed **both directions**, 53 rows (P6.1 `PermissionCatalogTest`, 5 tests); documented in SECURITY §4.1; **no migration (D6)**
- [x] **FR-3.5 (P1)** Roles CRUD (tenant-scoped) + Hospital Admin creates custom roles from catalog — P6.2 `RoleManagementTest` (14 tests): grant-scope rule, catalog-only writes, platform-only codes refused, system roles immutable, `RESOURCE_IN_USE`, six bundles provisioned at registration with the admin enrolled in `ADMIN`
- [x] **FR-3.3 (P0)** Resource-level rules: assigned/previously treated doctor policy (OQ-2), enforced in service + list queries — P6.4 `ResourcePolicyTest` (15 tests) proves the framework and a real `UserSelfOrStaffPolicy` over `GET /staff`; list filtering is a SQL predicate. Real `PatientAccessPolicy` lands at **P10.8** (D7)
- [x] **FR-3.4 (P0)** Field-level masking (`FieldMaskingService`): diagnosis/notes/vitals hidden by permission; masked fields omitted, not blanked — P6.5 `FieldMaskingTest` (9 tests); first production wiring **P10.8 / P12.7 / P17.4** (D8)
- [x] Wrong-role test matrix for every endpoint (allowed/denied) — P6.6 `EndpointPermissionMatrixTest` (4 tests), driven by `RequestMappingHandlerMapping` enumeration so it grows with the API
- [x] Wrong-tenant test for every endpoint (404, never 403) — P6.7 `WrongTenantTest` (5 tests); also closes group E's last open item above
- [~] Cross-doctor sensitive reads audited — **framework + log seam at P6.4** (`PolicyAudit`, event `cross_doctor_read`, asserted by `ResourcePolicyTest`); the `audit_logs` row lands at **P14.6** and full coverage at **P19** (recorded deferral = SEC-10, not a gap)

### H. Frontend Foundation — Phase 7 ☐

- [ ] Next.js 15 app shell: TS strict, Tailwind, shadcn tokens (light + dark)
- [ ] Axios layer: attach access token, silent refresh on 401 via cookie, error normalization, `traceId` surfaced in toasts
- [ ] Redux auth session slice + TanStack Query provider (server state not mirrored in Redux)
- [ ] Shared components: FormField (required `*` + `aria-required`), SelectField/Combobox (`{value,label}`), DataTable, ConfirmDialog, Button, Toast, StatusBadge, Empty/Error/Skeleton, AllergyBanner, Timeline, PermissionGate
- [ ] Layout shell: permission-filtered sidebar, top bar with tenant name + user menu
- [ ] Login/logout wired to auth endpoints (E2E via Playwright)
- [ ] Permission hooks / `PermissionGate` (UX only — server enforces)
- [ ] No-raw-IDs audit: selects, tables and labels render names, never UUIDs
- [ ] Every view defines loading / empty / error / forbidden / not-found / offline states

### I. Hospital & Organization Management — Phase 8 ☐

- [ ] **FR-1.5 (P1)** Hospital settings editable: name, contact, timezone, working hours
- [ ] **FR-4.1 (P0)** Departments CRUD with precise field-level validation errors
- [ ] **FR-1.6 (P1)** Platform Admin suspend/reactivate tenant; suspension blocks all tenant logins
- [ ] Frontend hospital settings + departments pages (FormField, DataTable, names not IDs)

### J. Staff & Doctor Management — Phase 9 ☐

- [ ] **FR-4.2 (P0)** Invite doctors/nurses/staff by email with role + department; secure expiring acceptance link
- [ ] **FR-4.3 (P0)** Staff profiles: specialty, license number, department, availability
- [ ] **FR-4.4 (P0)** Deactivating staff revokes sessions but preserves historical attribution
- [ ] Frontend staff list + invite form with field-level errors
- [ ] E2E: invite → accept → login

### K. Patients — Phase 10 ☐

- [ ] **FR-5.1 (P0)** Patient registration with demographics; field-specific validation errors
- [ ] **FR-5.2 (P0)** Human-readable, tenant-unique MRN generation
- [ ] **FR-5.4 (P0)** Allergies with severity, prominently visible in clinical views (AllergyBanner)
- [ ] **FR-5.5 (P0)** Paginated, filterable, tenant-scoped patient search
- [ ] **FR-5.6 (P0)** Patients archived/deactivated — never hard-deleted while clinical history exists
- [ ] **FR-5.3 (P1)** Duplicate detection warning (name + DOB + phone)
- [ ] Resource policy + field masking verified (receptionist sees no clinical fields)
- [ ] Patient tenant-isolation suite (direct ID, UUID manipulation, pagination boundaries)

### L. Appointments & Queue — Phase 11 ☐

- [ ] **FR-6.1 (P0)** Book, reschedule, cancel appointments
- [ ] **FR-6.2 (P0)** Double-booking prevention: unique constraint + transactional overlap check; concurrent requests → exactly one winner, other 409 `SLOT_UNAVAILABLE`
- [ ] **FR-6.3 (P0)** Appointment views show patient/doctor **names**, never raw IDs
- [ ] **FR-6.4 (P0)** Day queue with statuses: scheduled, checked-in, in-consultation, completed, no-show, cancelled
- [ ] **FR-6.5 (P2)** Appointment reminders via notifications
- [ ] Authorization tests by role (receptionist books, etc.) + tenant isolation (foreign patientId → 404)

### M. Clinical Consultation — Phase 12 ☐

- [ ] **FR-7.1 (P0)** Start a visit from an appointment or as a walk-in
- [ ] **FR-7.2 (P0)** Record per visit: vitals, clinical notes, diagnoses, orders, recommendations/advice
- [ ] **FR-7.3 (P0)** Finalize a visit → read-only; after finalization edits only via amendments/addenda (409 `RECORD_FINALIZED`)
- [ ] **FR-7.4 (P0)** Every clinical entry records author, timestamp and change reason
- [ ] **FR-7.5 (P0)** Consultation forms show doctor/patient names, never raw IDs; Clinical accent styling; finalize confirmation
- [ ] Authorization + field masking: receptionist never receives notes/diagnoses
- [ ] Visit/record tenant isolation tests

### N. Medical History — Phase 13 ☐

- [ ] **FR-8.1 (P0)** Conditions/problems, family history, surgical history CRUD
- [ ] **FR-8.2 (P0)** Unified, paginated patient timeline across visits, prescriptions, labs, imaging, documents (bounded — no unbounded queries)
- [ ] **FR-8.3 (P0)** Timeline entries show responsible clinician and department
- [ ] Frontend Timeline component + patient history page (loading/empty/error states)
- [ ] Authorization + tenant isolation for history/timeline

### O. Prescriptions & Medicines — Phase 14 ☐

- [ ] **FR-9.1 (P0)** Create prescriptions: medicine, dose, frequency, **duration**, instructions
- [ ] **FR-9.2 (P0)** Versioning v1 → v2 with reason; previous versions retrievable
- [ ] **FR-9.3 (P0)** Prescriber attribution — any authorized clinician sees which doctor prescribed what; cross-doctor read audited
- [ ] **FR-9.5 (P1)** Tenant-scoped searchable medicine catalog
- [ ] **FR-9.4 (P1)** Allergy conflict warning when prescribing against a recorded allergy
- [ ] **FR-9.6 (P1)** Printable/PDF prescription
- [ ] Frontend prescription form + history showing prescriber names and duration
- [ ] Tenant isolation: prescriptions Tenant A vs Tenant B

### P. Lab & Imaging — Phase 15 ☐

- [ ] **FR-10.1 (P1)** Create lab/imaging orders during a visit
- [ ] **FR-10.2 (P1)** Lab staff enter results / upload reports; linked to order and visit
- [ ] **FR-10.3 (P1)** Results on patient timeline; abnormal flags supported
- [ ] Lab/imaging staff restricted to their order-related data only (unrelated order → 404)
- [ ] Frontend result views (Clinical accent, names not IDs) + tenant isolation

### Q. Documents & Files — Phase 16 ☐

- [ ] **FR-11.1 (P1)** Upload documents (PDF, images) linked to patient/visit/order
- [ ] **FR-11.2 (P1)** Upload validation: extension + MIME allow-list, magic-byte check, size limit, sanitized names, checksum → `FILE_TOO_LARGE` / `FILE_TYPE_NOT_ALLOWED`
- [ ] **FR-11.3 (P1)** Short-lived, authorization-checked downloads — no permanent public URLs; every download audited
- [ ] **FR-11.4 (P1)** `StorageService` abstraction: MinIO locally, AWS S3 in production (profile-selected)
- [ ] Tenant isolation: object keys `tenants/{tenantId}/`; foreign key access blocked

### R. Billing — Phase 17 ☐

- [ ] **FR-12.1 (P1)** Invoice from a visit/services with line items and totals (`BigDecimal`)
- [ ] **FR-12.2 (P1)** Record payments (full/partial) + invoice status + `Idempotency-Key` replay safety
- [ ] **FR-12.3 (P1)** Billing staff see billable data only — no clinical detail (response has no diagnosis/notes fields)
- [ ] Frontend invoices/payments (StatusBadge, names not IDs, confirm destructive actions)
- [ ] Tenant isolation: foreign invoice/visit → 404

### S. Notifications — Phase 18 ☐

- [ ] **FR-13.1 (P0)** Transactional emails (verification, invite, reset) sent asynchronously via outbox with retry/backoff — sending never blocks requests
- [ ] Resend-verification rate limited; UI never times out or leaves the flow
- [ ] **FR-13.2 (P2)** In-app notifications with read/unread state
- [ ] Worker restores tenant context from job payload; notifications scoped to user/tenant
- [ ] Email provider decision recorded (OQ-5/TQ-3)

### T. Audit & Compliance Controls — Phase 19 ☐

- [ ] **FR-14.1 (P0)** Audit log covers: auth events, permission changes, clinical create/update/finalize/amend, exports, downloads, sensitive reads, cross-doctor reads, tenant suspension
- [ ] **FR-14.2 (P1)** Append-only rows (app DB user has INSERT/SELECT only), tenant-scoped, viewable by authorized admins
- [ ] Audit viewer endpoint + UI, paginated, admin-only
- [ ] Retention configuration; no purge job without a documented policy (OQ-4)
- [ ] Audit coverage checklist vs TDD §16 / SECURITY §10 — every item has a test

### U. Search — Phase 20 ☐

- [ ] **FR-15.1 (P1)** `GET /search?q=` across patients, staff, appointments — paginated, permission-aware, tenant-scoped, minimum query length
- [ ] Index support verified with `EXPLAIN` (no full-table scan on hot path)
- [ ] Frontend global search debounced ~300 ms
- [ ] Tests: no cross-tenant leakage, no unauthorized results, no unbounded results

### V. Dashboards & Analytics — Phase 21 ☐

- [ ] **FR-16.1 (P2)** Role-appropriate dashboards: queue size, appointments, revenue, workload
- [ ] Aggregate endpoints bounded; dashboard loads with a bounded number of requests
- [ ] Loading/empty/error states; role-scoped and tenant-scoped tests

### W. Performance Optimization — Phase 22 ☐

- [ ] Baseline capture: p95 latency + query counts on list/search/login/booking (k6)
- [ ] N+1 detection and removal where baseline shows it
- [ ] Query plan review (`EXPLAIN`) for list/search access paths
- [ ] Selective Redis caching where measured; no PHI cached without an ADR
- [ ] Frontend bundle analysis + code-splitting where measured
- [ ] **Target met:** p95 API latency ≤ 500 ms for standard reads (PRD §9.2)
- [ ] Before/after measurements documented

### X. Security Hardening — Phase 23 ☐

- [ ] Full security review (P0–P3 findings recorded in PROJECT_CONTEXT §11)
- [ ] Headers/CSP/CORS per SECURITY §6: HSTS, nosniff, frame-ancestors, no wildcard CORS
- [ ] Dependency + secret scans; all critical/high fixed
- [ ] Rate limits enforced per SECURITY §12 table (auth fails closed)
- [ ] Pen-test style checks: IDOR, mass assignment, XSS, upload abuse, enumeration
- [ ] No public Swagger/actuator/Prometheus in the production profile
- [ ] Zero open P0/P1 findings, each fix has a regression test

### Y. Automated Testing (completion) — Phase 24 ☐

- [ ] ≥ 80% service-layer line coverage (TESTING §3)
- [ ] Full tenant-isolation suite covering every vector in TESTING §4 — 0 failures
- [ ] All 14 critical E2E flows automated (TESTING §7) — Playwright green
- [ ] Failure-case matrix automated (TESTING §8)
- [ ] Accessibility (axe) checks on key pages
- [ ] Flaky tests fixed or quarantined with a tracked issue

### Z. Observability — Phase 25 ☐

- [ ] Prometheus metrics: request latency/error rate/throughput, DB & Redis latency, JVM, thread/connection pools
- [ ] `traceId` propagation across logs (and traces if OpenTelemetry added)
- [ ] Log redaction verification: no PHI, passwords or tokens in logs
- [ ] Monitoring dashboards + alerts (DEPLOYMENT §9)
- [ ] Monitoring endpoints reachable only from the internal network

### AA. CI/CD — Phase 26 ☐

- [ ] Backend compile + unit tests on every PR
- [ ] Integration + tenant-isolation tests (Testcontainers) in CI
- [ ] Migration check on an empty database in CI
- [ ] Frontend typecheck, lint, tests, build stage
- [ ] Dependency and secret scans in CI
- [ ] Deploy to staging + E2E smoke + manual approval → production
- [ ] Secrets from a store, never in the repo

### AB. Production Deployment — Phase 27 ☐

- [ ] Frontend on Vercel with environment configuration
- [ ] Backend container on EC2 behind Nginx with TLS, HTTP→HTTPS redirect, HSTS
- [ ] Managed MySQL + Redis in private networking; least-privilege `app_rw` user
- [ ] S3 private bucket with SSE; IAM scoped to `tenants/*` prefix
- [ ] Production configuration checklist (DEPLOYMENT §6) verified
- [ ] Automated backups enabled for database and object storage
- [ ] Production smoke tests; no dev CORS/Swagger/debug config present

### AC. Disaster Recovery — Phase 28 ☐

- [ ] Backup schedule + point-in-time recovery configured and verified
- [ ] RPO/RTO defined and recorded (proposed: RPO ≤ 15 min, RTO ≤ 4 h)
- [ ] Restore drill performed and documented
- [ ] Runbooks: restore DB, rotate secrets, revoke sessions, suspend tenant, rollback release

### AD. Production Readiness — Phase 29 ☐

- [ ] Production readiness review run; blockers listed and resolved
- [ ] All automatic NO-GO conditions absent (DEPLOYMENT §12)
- [ ] Full tenant-isolation + critical E2E suites green in a production-like environment
- [ ] Final **GO** decision recorded

### AE. Future AI / Advanced Features — Phase 30 ☐

- [ ] Each candidate (clinical summarization, decision support, OCR of lab documents, patient portal, HL7/FHIR, multi-branch tenants, message broker) proposed with an ADR + PRD addendum before any code
- [ ] Phase stays unstarted until a feature is approved

---

## 3. Cross-Cutting Product Rules (verify on every feature)

These are always-on; they cannot be "ticked" once — they must hold for every feature built:

- [ ] Tenant isolation enforced server-side at every layer; foreign data → 404
- [ ] Every endpoint declares a permission; default deny
- [ ] Field-level masking hides clinical data from unauthorized roles
- [ ] Names, never raw IDs, anywhere in the UI
- [ ] Field-level validation errors naming the exact field (no generic "unexpected error")
- [ ] All list endpoints paginated and bounded (size capped, default 20 / max 100)
- [ ] Finalized clinical records versioned/amended, never silently overwritten; no hard delete of clinical history
- [ ] Audit events emitted for sensitive actions; audit rows append-only and tenant-scoped
- [ ] Rate limiting on abuse-prone endpoints; no account enumeration
- [ ] No secrets in source/logs; no public Swagger/actuator/Prometheus in prod
- [ ] Loading / empty / error / forbidden / not-found states on every view; destructive actions confirmed; submit disabled while pending
- [ ] "Compliance-ready" wording only — never claim regulatory compliance
