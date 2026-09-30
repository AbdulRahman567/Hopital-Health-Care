# Healthcare-HMS — Testing Strategy

## 1. Philosophy
Tests are part of every phase, not a final phase. Security and tenant isolation are first-class test subjects. A feature is not done until its tests pass.

```text
               E2E (Playwright)
             /                  \
      Integration              Component
   (Testcontainers)           (Vitest + RTL)
             \                  /
              Unit (JUnit 5 / Vitest)
```

## 2. Test Types
| Type | Tools | Scope |
|---|---|---|
| Unit (backend) | JUnit 5, Mockito, AssertJ | Services, policies, mappers, validators |
| Unit (frontend) | Vitest | Hooks, utilities, Zod schemas |
| Repository | Testcontainers MySQL | Queries, constraints, tenant filter, indexes |
| Integration | Spring Boot Test, Testcontainers (MySQL, Redis, MinIO) | Full request → DB flows |
| Migration | Flyway on empty DB | Migrations apply cleanly and validate against entities |
| Security | Spring Security Test | Auth, roles, permissions, tokens |
| **Tenant isolation** | Integration | Tenant A vs Tenant B (see §4) |
| API contract | MockMvc / REST Assured | Envelopes, status codes, pagination, error format |
| Architecture | ArchUnit | Layering, module dependencies, endpoint permission annotation |
| Component | Vitest + React Testing Library | Forms, selects, tables, dialogs |
| E2E | Playwright | Critical workflows |
| Accessibility | axe (via Playwright) | Key pages |
| Performance | k6 or Gatling | List/search endpoints, login, booking |

## 3. Coverage Expectations
| Area | Target |
|---|---|
| Service layer (backend) | ≥ 80% line coverage, higher for auth/authz/tenant code |
| Policies and masking | 100% of rules have tests |
| Endpoints | Each has unauthenticated, wrong-role and (if tenant data) wrong-tenant tests |
| Critical E2E flows | 100% automated |
Coverage is a floor, not a goal; meaningful assertions matter more.

## 4. Tenant Isolation Suite
Fixtures: `hospital-a` and `hospital-b`, each with admin, doctor, receptionist and data.

Authenticated as Tenant B, attempt to access Tenant A's:
Patients · Doctors/staff · Departments · Appointments · Visits · Prescriptions · Invoices · Documents · Lab reports · Notifications · Audit logs · Search results · Exports · Downloads

Vectors:
- Direct ID access, UUID manipulation
- Path and query parameters; sorting and filtering
- Bulk endpoints; pagination boundaries
- Client-supplied `X-Tenant-ID` (must be ignored/rejected)
- File download URLs and pre-signed URL reuse
- Cached data (Redis key collisions)
- Background jobs and outbox payloads
- Foreign keys referencing another tenant's rows in request bodies (e.g., booking with another tenant's patient)

Expected: 404 (or empty results) and **no leakage** of existence, counts or metadata.

## 5. Authorization Tests
- Every role × sensitive endpoint matrix (allowed / denied).
- Resource-level: unassigned doctor cannot read patient; assigned/treating doctor can; cross-doctor reads audited.
- Field-level: receptionist never receives diagnosis, prescription or notes fields.
- Permission changes take effect on the next token refresh or request as designed.

## 6. Authentication Tests
Login success/failure, lockout, rate limiting, refresh rotation, refresh reuse detection, logout invalidation, expired token, tampered token, password reset (single use, expiry, no enumeration), email verification, invitation acceptance, secrets validation on startup.

## 7. Critical E2E Flows
1. Hospital registration
2. Email verification
3. Admin login
4. Department creation
5. Doctor invitation and acceptance
6. Doctor login
7. Patient registration
8. Appointment booking
9. Consultation and finalization
10. Prescription creation and amendment (v2)
11. Billing — invoice and payment
12. Document upload and download
13. Patient history/timeline review, including "which doctor prescribed what"
14. Logout

## 8. Failure-Case Matrix
| Case | Expected |
|---|---|
| Unauthenticated request | 401 |
| Wrong role | 403 |
| Wrong tenant | 404 |
| Expired/invalid token | 401 |
| Invalid input | 422 with field errors |
| Duplicate record | 409 |
| Double booking (concurrent) | Exactly one success, other 409 |
| Missing resource | 404 |
| Edit finalized record | 409 `RECORD_FINALIZED` |
| Stale version update | 409 `VERSION_CONFLICT` |
| Oversized file | Rejected with clear error |
| Unsupported/spoofed file type | Rejected |
| Rate limit exceeded | 429 with `Retry-After` |

## 9. Frontend-Specific Checks
- Selects render labels, never IDs.
- Required fields marked; errors attach to the correct field.
- Submit buttons disable while pending; double-click does not duplicate.
- Loading, empty, error and forbidden states exist for each view.
- No console errors or hydration warnings on key pages.

## 10. Test Data
- Deterministic factories/builders; no shared mutable fixtures.
- Synthetic data only — **never real patient data**.
- Each test isolates its own tenant or cleans up.

## 11. CI Pipeline Gates
1. Backend compile + unit tests
2. Integration + isolation tests (Testcontainers)
3. Migration validation on empty DB
4. Frontend typecheck, lint, unit/component tests, build
5. Dependency and secret scans
6. E2E smoke on staging deploy
Merge blocked on any failure.

## 12. Rules
- Never delete or weaken tests to make a build pass.
- Bug fix ⇒ regression test.
- Flaky tests are fixed or quarantined with a tracked issue within one sprint.
