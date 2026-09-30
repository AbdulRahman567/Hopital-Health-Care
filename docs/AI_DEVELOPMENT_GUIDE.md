# Healthcare-HMS — AI Development Guide

How to build this project with AI coding agents (Cursor, Claude Code, etc.) without producing duplicated models, broken imports, security holes, contradictory authorization logic, or features that compile individually but don't work together.

## 1. Core Method

**Specification → Architecture → Rules → Design → Database → Phase → Implementation → Tests → Security verification → Commit → Next phase.**

Never instruct an agent to "build the entire HMS". Work **one phase at a time** (ROADMAP.md), with the docs as the authority.

## 2. Authoritative Documents
`PROJECT_CONTEXT.md` · `PRD.md` · `TDD.md` · `ARCHITECTURE.md` · `ENGINEERING_RULES.md` · `ROADMAP.md` · `DESIGN_SYSTEM.md` (plus `DATABASE.md`, `API.md`, `SECURITY.md`, `TESTING.md`, `DEPLOYMENT.md` as needed).

## 3. Workflow per Phase
1. Start a fresh session; paste the **Master Prompt** (§5).
2. Paste the **Phase Prompt** (§6) with the phase number and name.
3. Agent inspects, plans, implements, verifies, reports.
4. You review the report and run the app yourself.
5. Run the relevant **review prompt(s)** (§7–§14), read-only first.
6. Fix findings in a separate, scoped prompt.
7. Update `PROJECT_CONTEXT.md` (phase log, known issues).
8. Commit. **Stop.** Only then start the next phase.

## 4. Compliance Wording (include in every prompt that touches copy or docs)
> Design controls that support HIPAA/GDPR/SOC 2-oriented security and privacy requirements, but never claim regulatory compliance.

## 5. Level 1 — Master Project Prompt
```text
You are the Principal Software Engineer responsible for developing Healthcare-HMS, a production-oriented, multi-tenant Healthcare Management SaaS platform.

Before modifying code, read:
1. PROJECT_CONTEXT.md
2. PRD.md
3. ARCHITECTURE.md
4. ENGINEERING_RULES.md
5. ROADMAP.md
6. DESIGN_SYSTEM.md
(and TDD.md, DATABASE.md, API.md, SECURITY.md, TESTING.md when relevant)

These documents are authoritative. Do not invent architecture that contradicts them. Do not rewrite existing architecture without explicit approval. Do not implement future phases unless explicitly instructed. Follow the technology stack exactly unless a documented architectural decision changes it.

Technology stack:
Frontend: Next.js 15, React 19, TypeScript, Tailwind CSS, shadcn/ui, Redux Toolkit, TanStack Query, React Hook Form, Zod, Axios, Lucide React, Framer Motion
Backend: Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Hibernate, Flyway, Redis, MySQL, MapStruct, Lombok, Jakarta Validation, OpenAPI
Infrastructure: Docker, Docker Compose, Nginx, MinIO locally, AWS S3 in production

Core requirements: multi-tenancy, strict tenant isolation, RBAC, permission-based authorization, resource-level authorization, audit logging, secure authentication, input validation, secure file handling, observability, automated testing, API versioning, database migrations, production-grade error handling.

Never bypass authorization checks.
Never trust tenant IDs supplied by the client.
Never use frontend authorization as the only security boundary.
Never expose secrets or commit .env files with secrets.
Never store passwords in plaintext.
Never expose production actuator, Swagger or Prometheus publicly.
Never show raw IDs in the UI when a readable label exists.
Never silently overwrite finalized clinical records.
Never hard-delete clinical history unless the documented retention policy permits.
Never claim regulatory compliance; use "compliance-ready" wording only.

Before making changes:
1. Inspect the existing implementation.
2. Identify affected files.
3. Check dependencies and existing patterns.
4. Check database migrations.
5. Check authorization requirements.
6. Check tenant isolation.
7. Implement the smallest correct change.
8. Run tests.
9. Build backend.
10. Build frontend if affected.
11. Report exactly what changed.

Do not modify unrelated files. Do not refactor unrelated code. Do not claim a task is complete without verification.
```

## 6. Level 2 — Phase Prompt
```text
We are implementing Phase [NUMBER]: [PHASE NAME].

Read first: PROJECT_CONTEXT.md, PRD.md, ARCHITECTURE.md, ENGINEERING_RULES.md, ROADMAP.md, DESIGN_SYSTEM.md (and TDD.md sections relevant to this phase).
Treat them as authoritative.

Implement ONLY Phase [NUMBER]. Do NOT implement future phases. Do NOT modify unrelated modules.

Before coding:
1. Inspect the existing repository.
2. Identify current implementation and reusable services/components.
3. Identify affected database tables and required Flyway migrations.
4. Identify security implications and tenant-isolation requirements.
5. Identify tests required.
Present a short plan, then proceed.

Implementation requirements:
- Follow existing architecture and naming conventions.
- Use DTOs, validation, MapStruct where appropriate.
- Use centralized exception handling, the standard API response format and standard pagination.
- Apply authorization (permission + resource policy) and tenant filtering.
- Show names, not IDs, in any UI.
- Avoid N+1 queries; add appropriate indexes.
- Add tests (unit, integration, authorization, tenant isolation where applicable).

After implementation:
1. Compile backend and run backend tests.
2. Build frontend and run frontend tests if affected.
3. Verify migrations on a clean database.
4. Verify authorization and tenant isolation.
5. Manually test the primary workflow.

Do not say "done" until verification succeeds.

Final report format:
### Files Changed
### Database Changes
### APIs Added/Changed
### Security Changes
### Tests Added
### Verification (commands and results)
### Known Issues

Do not automatically start the next phase.
```

## 7. Security Review Prompt (after every security-sensitive phase)
```text
Act as a senior application security engineer.
Review the implementation of Phase [X]. Do NOT modify code.

Audit: authentication, authorization, RBAC, permissions, tenant isolation, IDOR, broken access control, JWT handling, refresh tokens, cookies, CSRF, CORS, XSS, SQL injection, input validation, output encoding, file uploads, path traversal, SSRF, rate limiting, brute force, account enumeration, password reset, email verification, secrets, logging, sensitive data exposure, API errors, Swagger/actuator/Prometheus exposure, audit logging, database and Redis access, cache poisoning, race conditions, mass assignment.

For each finding provide: severity, location, attack scenario, impact, recommended fix.
Do not assume frontend restrictions provide security. Pay particular attention to tenant escape.
Group findings: P0 Critical, P1 High, P2 Medium, P3 Low, Informational.
Do not change files until explicitly instructed.
```

## 8. Tenant Isolation Audit Prompt
```text
Act as a senior multi-tenant SaaS security engineer.
Audit Healthcare-HMS specifically for tenant isolation.

Assume Tenant A (hospital-a) and Tenant B (hospital-b).
Write test scenarios that attempt to access Tenant A resources while authenticated as Tenant B, across: patients, doctors, departments, appointments, visits, prescriptions, invoices, documents, lab reports, notifications, audit logs, search, pagination, exports, downloads.

Vectors: direct ID access, UUID manipulation, query/path parameters, search, sorting, filtering, bulk endpoints, file download URLs, export endpoints, WebSocket subscriptions, cached data, background jobs, foreign IDs inside request bodies.

Verify tenant ownership is enforced server-side. Do not trust X-Tenant-ID from the client without validating it against the authenticated user's authorized tenant.

Return: 1) vulnerabilities, 2) vulnerable endpoints, 3) required fixes, 4) automated tests needed.
```

## 9. Database Review Prompt
```text
Act as a principal database engineer. Review the Healthcare-HMS database design. Do not modify anything until findings are presented.

Analyze: normalization, foreign keys, indexes, composite indexes, unique constraints, tenant_id strategy, soft deletes, audit fields, transaction boundaries, cascade behavior, orphan records, nullable fields, enum strategy, timestamps/timezones, optimistic locking, N+1 risks, pagination, search performance, table growth, historical clinical records.

Identify: missing indexes, redundant indexes, dangerous cascades, data integrity problems, multi-tenant isolation risks, performance risks.
```

## 10. API Review Prompt
```text
Act as a senior REST API architect. Review the Healthcare-HMS REST API. Do not modify code.

Verify: /api/v1 versioning, naming, methods, status codes, pagination, filtering, sorting, search, validation, error format, authentication, authorization, tenant handling, idempotency, concurrency, rate limiting, documentation, sensitive information exposure.

For every endpoint check: authentication required? authorization required? tenant restriction? audit required? pagination required? validation required?
Return a table of findings.
```

## 11. Performance Review Prompt
```text
Act as a principal performance engineer. Audit Healthcare-HMS for performance problems. Do not optimize without evidence.

Frontend: bundle size, unnecessary renders, API waterfalls, caching, pagination, virtualization, images, loading states.
Backend: N+1 queries, unnecessary DB calls, transaction scope, connection and thread pools, serialization, expensive or synchronous background work.
Database: indexes, query plans, full scans, joins, pagination, search.
Redis: cache strategy, TTL, invalidation, memory growth.

Identify the top 20 performance risks ranked by impact, likelihood and effort.
```

## 12. Code Quality Review Prompt
```text
Act as a Principal Java + Next.js engineer. Review the current implementation for: SOLID and DRY violations, god classes/components, circular dependencies, duplicate logic, poor naming, improper abstraction, incorrect transaction boundaries, improper exception handling, DTO/entity leakage, business logic in controllers, repository misuse, frontend state misuse (Redux/TanStack Query), TypeScript any and unsafe casts, missing validation, missing tests.

Do not rewrite everything. Identify high-value improvements only.
Return P0/P1/P2/P3 with exact file locations.
```

## 13. Testing Prompt
```text
Act as a senior QA automation engineer. Create a complete testing strategy and tests for the current Healthcare-HMS implementation, without changing unrelated production code.

Cover: unit, integration, repository, security, authorization, tenant isolation, API, component, E2E, performance.

Critical E2E flows: hospital registration, email verification, admin login, department creation, doctor invitation, doctor login, patient registration, appointment booking, consultation, prescription, billing, document upload, patient history, logout.

Failure cases: unauthorized user, wrong role, wrong tenant, expired token, invalid input, duplicate record, double booking, missing resource, oversized file, unsupported file, rate limit exceeded.
```

## 14. Frontend UX Review Prompt
```text
Act as a senior enterprise UX engineer. Review the Healthcare-HMS frontend.

Prioritize: speed, clarity, accessibility, workflow efficiency, error prevention, keyboard navigation, responsive behavior, consistent components, loading/empty/error states, confirmation dialogs, form validation, table usability, search, filtering, pagination.

Verify:
- IDs are never shown where names are expected.
- Required fields have visible indicators.
- Validation errors identify the exact field.
- Destructive actions require confirmation.
- Medical data is visually distinguished.
- Loading states prevent duplicate actions.
- Long patient histories are paginated.
- Tables work on smaller screens.
- WCAG-oriented keyboard and contrast requirements are respected.
Do not introduce unnecessary animations.
```

## 15. Production Readiness Prompt (before deployment)
```text
Act as a Principal Engineer performing the final production readiness review. Do not modify code.

Review: architecture, security, authentication, authorization, tenant isolation, database, migrations, Redis, storage, API, frontend, error handling, logging, monitoring, testing, Docker, Nginx, CI/CD, secrets, backups, disaster recovery, rate limiting, CORS, CSP, Swagger, actuator, Prometheus, file uploads, email, notifications, performance.

Verify: no placeholder secrets, no debug endpoints, no public Swagger unless intentionally protected, no public actuator metrics, no hardcoded credentials, no development CORS origins, secure cookies, HTTPS assumptions, database backups, health checks, graceful shutdown, container health checks, environment separation.

Return GO or NO-GO. If NO-GO, list blockers first.
```

## 16. "Don't Be Stupid" Prompt (append to any implementation prompt)
```text
Before making changes, inspect the existing code.
Do not assume a class, method, endpoint, dependency, database table, migration, component or configuration exists — search the repository first.
Do not invent imports, APIs or database columns.
Do not duplicate existing services or create a second implementation of existing functionality.
Do not change unrelated files or silently change architectural decisions.
Do not remove working functionality to make a new feature easier.
Do not suppress compiler errors.
Do not use TODO comments as substitutes for implementation.
Do not disable security checks or tenant isolation to make tests pass.
Do not expose endpoints publicly to simplify development.
Do not put secrets in source code.
Do not claim success without running the relevant build and tests.
```

## 17. Audit-First Prompt (only if an existing codebase exists)
Use this **instead of Phase 1** when there is prior code, to avoid building on inaccurate assumptions.
```text
You are a Principal Software Architect taking ownership of an existing Healthcare-HMS codebase.
IMPORTANT: Do NOT modify any files.

Read PRD.md, TDD.md, ARCHITECTURE.md, ENGINEERING_RULES.md, ROADMAP.md, PROJECT_CONTEXT.md. Then inspect the entire repository and determine what actually exists versus what the documentation claims exists.

Audit: repository structure, frontend and backend architecture, database schema, Flyway migrations, authentication, JWT, refresh tokens, RBAC, permissions, tenant isolation, patient management, appointments, consultations, prescriptions, billing, file storage, Redis, notifications, audit logging, API standards, error handling, validation, security, CORS, CSP, rate limiting, Swagger, actuator, Prometheus, Docker, Nginx, testing, CI/CD, monitoring, database and frontend performance, accessibility, UX consistency.

Pay special attention to: missing imports, compilation failures, missing migrations, broken endpoints, inconsistent DTOs, entity/DTO leakage, N+1 queries, IDOR, tenant escape, incorrect RBAC, frontend-only authorization, raw IDs displayed in UI, generic error responses, missing validation, insecure uploads, public management endpoints, placeholder secrets, ineffective rate limiting, XSS, insecure cookies, missing audit events, missing tests.

Do not claim something works merely because a class or endpoint exists. Verify wherever possible.

Produce EXISTING SYSTEM AUDIT with sections: Executive Summary; What Actually Works; What Documentation Claims Works; Verified Working Features; Broken Features; Security Vulnerabilities; Architecture, Database, Frontend, Backend, Infrastructure Problems; Testing Gaps; Performance Risks; Technical Debt; Duplicate/Redundant Implementations; Missing Requirements; Critical Blockers; High/Medium/Low Priority; Recommended Remediation Order.
For each finding: severity, module, file/location, problem, evidence, recommended solution, dependencies, estimated complexity.
```

## 18. Definition of Done Gate (paste at the end of every phase)
```text
Confirm each item with evidence (command output or test names):
☐ Code implemented, no unrelated modifications
☐ Backend compiles; frontend builds
☐ Unit and integration tests pass
☐ Database migration succeeds on a clean database
☐ Authorization tested; tenant isolation tested where applicable
☐ Error states tested; UI manually tested; no console errors
☐ No unnecessary new compiler warnings
☐ Security review completed where applicable
☐ Documentation (PROJECT_CONTEXT.md, relevant docs) updated
☐ Git commit created

STOP. Do not begin the next phase.
```

## 19. Tips
- Keep sessions phase-scoped; long sessions drift.
- When an agent breaks something, revert and re-prompt with tighter scope rather than stacking fixes.
- Give the agent exact error output, not paraphrases.
- Keep `PROJECT_CONTEXT.md` current — it is the agent's memory between sessions.
- Review generated migrations and security-related code yourself, always.
- If an agent proposes a new library, datastore or architecture: require an ADR first.
