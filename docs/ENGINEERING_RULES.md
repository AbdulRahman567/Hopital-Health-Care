# Healthcare-HMS — Engineering Rules

These rules are **mandatory** for humans and AI agents. Violations block merge.

## 1. Non-Negotiables

1. Never bypass authorization checks.
2. Never trust a tenant ID from the client; tenant comes from the authenticated principal.
3. Frontend authorization is never the only security boundary.
4. Never expose secrets; never commit `.env` files with secrets.
5. Never store plaintext passwords or log tokens/passwords.
6. Never expose production Swagger, actuator or Prometheus publicly.
7. Never show raw IDs in the UI when a readable label exists.
8. Never silently overwrite finalized clinical records; use versions/addenda.
9. Never hard-delete clinical history unless the documented retention policy permits.
10. Never claim regulatory compliance; use "compliance-ready" wording only.

## 2. Workflow Rules

1. **One phase at a time.** Do not implement future phases. Do not start the next phase automatically.
2. **Inspect before changing:** search the repo for existing classes, endpoints, tables, components, migrations.
3. **Smallest correct change.** No unrelated files, no unrelated refactors.
4. **Verify before claiming done:** compile, run tests, build frontend, run migrations, test the primary workflow.
5. **Report exactly:** files changed, DB changes, APIs, security changes, tests, verification output, known issues.
6. Architectural changes require an ADR and explicit approval.

## 3. "Do Not" List for AI Agents

- Do not invent imports, APIs, endpoints, dependencies, DB columns, migrations or config.
- Do not duplicate an existing service or component.
- Do not remove working functionality to make a new feature easier.
- Do not suppress compiler errors or warnings to get green builds.
- Do not use `TODO` as a substitute for implementation.
- Do not disable security checks, tenant isolation or validation to make tests pass.
- Do not open endpoints publicly to simplify development.
- Do not put secrets in source code.
- Do not say "done" without verification output.

## 4. Backend Rules (Java / Spring Boot)

| Area | Rule |
|---|---|
| Layering | Controller → Service → Repository. Controllers do HTTP only; no business logic; no repository access |
| DTOs | Entities never cross the API boundary; use request/response DTOs and MapStruct |
| Validation | Jakarta Validation on request DTOs; unknown JSON properties rejected |
| Transactions | `@Transactional` on service methods only; read-only where applicable; no external calls inside long transactions |
| Errors | Throw domain exceptions; single `@RestControllerAdvice`; no stack traces to clients |
| Naming | Packages by feature; `XxxController`, `XxxService`, `XxxRepository`, `XxxRequest/Response` |
| Tenancy | Every tenant entity extends `TenantOwnedEntity`; native queries must filter by `tenant_id` and are reviewed |
| Authorization | `@PreAuthorize`/`@RequirePermission` on every endpoint plus resource policy in service |
| Queries | No N+1; use fetch joins/projections; pagination mandatory for lists; `size` capped |
| Concurrency | Optimistic locking (`@Version`); unique constraints for invariants (e.g., appointment slots) |
| Lombok | Allowed for boilerplate; no `@Data` on entities; no `@EqualsAndHashCode` on associations |
| Logging | SLF4J; structured; no PHI/secrets; correct log level |
| Time | UTC in storage; `Instant`/`OffsetDateTime` not `Date` |
| Money | `BigDecimal` with explicit scale and currency |
| Config | `@ConfigurationProperties`; fail fast on missing/unsafe values |
| Dependencies | Justify each new dependency; keep versions pinned |

## 5. Database Rules

1. Schema changes only through Flyway migrations (`V###__description.sql`); never edit an applied migration.
2. Every tenant table has `tenant_id NOT NULL`; composite indexes lead with `tenant_id`.
3. Every foreign key column is indexed.
4. No `ON DELETE CASCADE` on clinical data.
5. UUID external IDs; timestamps in UTC; enums as strings with check constraints.
6. Audit columns on all entities: `created_at/by`, `updated_at/by`, `version`.
7. Verify slow/new queries with `EXPLAIN`.

## 6. API Rules

- Base path `/api/v1`; plural nouns, kebab-case.
- Standard success/error envelopes and pagination (see API.md).
- Correct status codes; `404` for foreign-tenant resources.
- Field-level validation errors must name the exact field.
- Create endpoints prone to duplicates support idempotency keys.

## 7. Frontend Rules (Next.js / React / TypeScript)

| Area | Rule |
|---|---|
| TypeScript | `strict` mode; no `any`; no unsafe casts without justification |
| Server state | TanStack Query for API data; do not mirror it in Redux |
| Client state | Redux Toolkit only for auth session and cross-cutting UI state |
| Forms | React Hook Form + Zod; server errors mapped to fields; required fields visibly marked |
| Selects | Options are `{value, label}`; the visible text is never an ID |
| Components | Reuse shared components; no duplicate table/select/dialog implementations |
| Security | No tokens in `localStorage`/`sessionStorage`; no `dangerouslySetInnerHTML` on user content |
| UX states | Loading, empty and error states on every data view; disable submit while pending |
| Destructive actions | Always confirmation dialog |
| Accessibility | Keyboard reachable, labelled inputs, WCAG-oriented contrast |
| Animation | Only where it adds clarity |
| Lists | Paginated; virtualize very long lists |

## 8. Security Rules

- Deny by default; every endpoint declares its permission.
- Validate all input; encode all output.
- Rate-limit anonymous and authentication endpoints.
- File uploads: allow-list, size limit, magic-byte check, sanitized names, private storage.
- Secrets come from environment/secret manager; app fails to start on placeholder secrets.
- Security-sensitive changes require the Security Review prompt (AI_DEVELOPMENT_GUIDE.md).

## 9. Testing Rules

- New behavior ships with tests in the same change.
- Every endpoint has: unauthenticated, wrong-role and (where relevant) wrong-tenant tests.
- Tenant isolation tests for every tenant-owned resource.
- Do not delete or weaken tests to pass a build.
- Bug fixes include a regression test.

## 10. Git Rules

- Branch per phase: `phase/NN-short-name`.
- Conventional commits: `feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`.
- One logical change per commit; no unrelated changes.
- Never commit secrets, build output or IDE files.
- Commit only after the Definition of Done is met.

## 11. Definition of Done

- [ ] Code implemented, no unrelated modifications
- [ ] Backend compiles; frontend builds
- [ ] Unit/integration tests pass
- [ ] Migration succeeds on a clean database
- [ ] Authorization tested; tenant isolation tested where applicable
- [ ] Error states tested; UI manually tested; no console errors
- [ ] No unnecessary new warnings
- [ ] Security review done where applicable
- [ ] Documentation updated
- [ ] Git commit created

**Then stop.**
