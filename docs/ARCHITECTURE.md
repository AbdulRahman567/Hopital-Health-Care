# Healthcare-HMS — Architecture

| | |
|---|---|
| **Status** | Draft v1.0 |
| **Read with** | PRD.md (what), TDD.md (detailed how), ENGINEERING_RULES.md (constraints) |

This document is the architectural summary and the source of truth for **structure and boundaries**. Detailed designs (tables, token flows, API envelopes) live in TDD.md; if the two disagree, raise an ADR rather than choosing silently.

## 1. Architectural Style

- **Modular monolith** (Spring Boot) with strict module boundaries; extraction to services only on evidence.
- **Stateless** backend: no in-memory sessions, no local-disk state. State lives in MySQL, Redis and S3/MinIO.
- **Multi-tenant, shared schema** with a `tenant_id` discriminator on every tenant-owned table.
- **Layered:** Controller → Service → Repository. Business logic lives only in services.
- **Separate frontend** (Next.js) communicating over a versioned REST API (`/api/v1`).

## 2. System Context

```text
Users (Admin, Doctor, Nurse, Reception, Lab, Billing)
        │ HTTPS
        ▼
 Next.js frontend (Vercel) ──► Nginx ──► Spring Boot API ──► MySQL
                                              │──────────► Redis
                                              │──────────► S3 / MinIO
                                              └──────────► Email provider (SMTP)
```

## 3. Backend Module Map

| Module | Responsibility | May depend on |
|---|---|---|
| `common` | Response envelope, exceptions, pagination, base entities | – |
| `config` | Security, Redis, storage, OpenAPI, async | common |
| `tenant` | Tenant context, resolution filter | common |
| `auth` | Login, tokens, reset, verification | tenant, authz, audit, notification |
| `authz` | Permissions, roles, resource & field policies | tenant, common |
| `organization` | Hospital settings, departments | tenant, authz, audit |
| `staff` | Staff, invitations | organization, auth, authz |
| `patient` | Patients, allergies, assignments | tenant, authz, audit |
| `appointment` | Booking, queue | patient, staff |
| `clinical` | Visits, vitals, notes, diagnoses, orders | patient, appointment |
| `history` | Conditions, family/surgical history, timeline | patient, clinical |
| `prescription` | Prescriptions, versions, medicines | clinical |
| `lab` | Lab and imaging orders/results | clinical, document |
| `document` | Upload/download, storage abstraction | tenant, authz |
| `billing` | Invoices, payments | clinical, patient |
| `notification` | Email, in-app, outbox jobs | tenant |
| `audit` | Append-only audit trail | tenant |
| `search` | Tenant-scoped, permission-aware search | patient, staff, appointment |

**Dependency rules**
1. No circular dependencies between modules (enforced by ArchUnit).
2. A module talks to another module only through its **public service interface**, never its repositories or entities.
3. `common` depends on nothing; feature modules never depend on `config` internals.
4. Clinical modules never depend on `billing`; billing reads through a defined interface.

## 4. Request Lifecycle

```text
HTTP request
 → Rate limit filter (Redis)
 → JWT authentication filter
 → Tenant context filter (tenant from token, client hints validated/ignored)
 → Controller (DTO validation, permission annotation)
 → Service (resource policy, business rules, transaction, audit)
 → Repository (tenant-filtered)
 → MySQL
 → DTO mapping (field masking) → response envelope
```

Every stage assumes the previous one could be bypassed; each re-checks what it owns.

## 5. Cross-Cutting Concerns

| Concern | Approach |
|---|---|
| Tenancy | `TenantContext` from authenticated principal; Hibernate tenant filtering; tenant-prefixed cache and object keys |
| Authorization | Permission on every endpoint; resource policy in services; field masking at mapping |
| Auditing | `AuditService` for security and clinical events; append-only |
| Errors | Single `@RestControllerAdvice`; stable error codes; field-level validation errors |
| Validation | Jakarta Validation at the edge; domain invariants in services |
| Async | Outbox table + workers; `tenantId` carried in job payload |
| Observability | JSON logs with `traceId`, Micrometer/Prometheus, health probes |
| Configuration | Environment variables; startup fails on unsafe secrets |

## 6. Frontend Architecture

- Next.js App Router, feature-based folders (`features/patients`, `features/appointments`, …).
- TanStack Query for server state; Redux Toolkit only for auth session and UI preferences.
- React Hook Form + Zod; server field errors mapped to fields.
- One Axios instance: token attach, silent refresh, error normalization.
- Shared components (Select/Combobox with `{value,label}`, DataTable, ConfirmDialog, FormField with required marker).
- Frontend permission checks improve UX only; they are never a security boundary.

## 7. Data Architecture

- MySQL is the system of record; Flyway is the only schema-change mechanism.
- Clinical data is decomposed (patient, visit, vitals, notes, diagnoses, prescriptions, orders, results) and **versioned** rather than overwritten.
- Redis holds ephemeral data only (rate limits, OTP, short-lived tokens, selective cache).
- Files live in object storage; the database stores metadata and storage keys.

## 8. Deployment Architecture

| Environment | Topology |
|---|---|
| Local | Docker Compose: backend, frontend, MySQL, Redis, MinIO, Nginx |
| Production v1 | Vercel (frontend), EC2 + Nginx (backend), managed MySQL, S3, Redis |
| Production HA (future) | Load balancer → N stateless backends → primary/replica DB, HA Redis |

A single EC2 instance is not high availability and is not described as such.

## 9. Key Architectural Decisions

See TDD.md §22 for the ADR list (modular monolith, shared schema tenancy, cookie refresh tokens, outbox jobs, versioned clinical records, UUID identifiers, compliance-ready wording).

## 10. Architectural Prohibitions

- No controller-to-repository calls.
- No entities in API contracts.
- No client-trusted tenant IDs.
- No module reaching into another module's persistence layer.
- No message broker, microservices, or new datastore without an ADR.
- No public Swagger, actuator or Prometheus in production.
