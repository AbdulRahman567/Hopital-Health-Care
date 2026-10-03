# Healthcare-HMS — Roadmap

Each phase is small, verifiable and ends with the Definition of Done. **Only the current phase is implemented.** Phase 0 must be approved before Phase 1.

**Status legend (phases):** ☐ not started · ◐ in progress · ☑ done · ⚠ blocked  
**Status legend (tasks):** `[ ]` todo · `[~]` in progress · `[x]` done · `[!]` blocked

**Task format:** `- [ ] P{phase}.{n} Task title | Layer: BE/FE/DB/INFRA/DOC | Depends: P.x | Verify: exact command or test`  
*(Layer `DOC` marks documentation-only tasks; code tasks use BE/FE/DB/INFRA.)*

## Phase Overview

| # | Phase | Depends on | Status |
|---|---|---|---|
| 0 | Documentation & Architecture | – | ☑ |
| 1 | Repository & Infrastructure | 0 | ☑ |
| 2 | Backend Foundation | 1 | ☑ |
| 3 | Database Foundation | 2 | ☑ |
| 4 | Multi-Tenancy | 3 | ☑ |
| 5 | Authentication | 4 | ☐ |
| 6 | Authorization / RBAC | 5 | ☐ |
| 7 | Frontend Foundation | 2 | ☐ |
| 8 | Hospital & Organization Management | 6, 7 | ☐ |
| 9 | Staff & Doctor Management | 8 | ☐ |
| 10 | Patient Management | 9 | ☐ |
| 11 | Appointments & Queue | 10 | ☐ |
| 12 | Clinical Consultation | 11 | ☐ |
| 13 | Medical History | 12 | ☐ |
| 14 | Prescriptions & Medicines | 12 | ☐ |
| 15 | Lab & Imaging | 12, 16 | ☐ |
| 16 | Document & File Management | 10 | ☐ |
| 17 | Billing | 12 | ☐ |
| 18 | Notifications | 5 | ☐ |
| 19 | Audit & Compliance Controls | 6 | ☐ |
| 20 | Search | 10, 11 | ☐ |
| 21 | Dashboards & Analytics | 11–17 | ☐ |
| 22 | Performance Optimization | 21 | ☐ |
| 23 | Security Hardening | 22 | ☐ |
| 24 | Automated Testing (completion) | 23 | ☐ |
| 25 | Observability | 2 | ☐ |
| 26 | CI/CD | 24 | ☐ |
| 27 | Production Deployment | 26 | ☐ |
| 28 | Disaster Recovery | 27 | ☐ |
| 29 | Production Readiness | 28 | ☐ |
| 30 | Future AI / Advanced Features | 29 | ☐ |

> Note: tests, audit hooks and basic metrics are written **inside every phase**. Phases 19, 24 and 25 complete and harden them; they are not the first time these appear. Phase 25 can be pulled forward if desired.

> Open decisions blocking specific tasks: CONF-1…CONF-6 and GAP-1 are recorded in PROJECT_CONTEXT.md §8 Known Issues. Tasks that cannot start until a decision is made carry that reference in their `Depends:` field.

## Phase Details

### Phase 0 — Documentation & Architecture
- **Deliverables:** PRD, TDD, ARCHITECTURE, ENGINEERING_RULES, ROADMAP, PROJECT_CONTEXT, DESIGN_SYSTEM, DATABASE, API, SECURITY, TESTING, DEPLOYMENT, AI_DEVELOPMENT_GUIDE.
- **Exit:** Documents reviewed and approved; open questions (PRD §13) answered or defaulted.

- [x] P0.1 Author PRD.md (goals, roles, FRs, authorization model, OQs) | Layer: DOC | Depends: – | Verify: PRD.md §5 contains the FR table and §13 lists OQ-1…OQ-6 with defaults
- [x] P0.2 Author TDD.md (stack, tenancy, auth, data model, API, testing, TQs, ADRs) | Layer: DOC | Depends: P0.1 | Verify: TDD.md §21 maps ROADMAP phases 1–29 (Phase 0 = docs, Phase 30 = future, neither has a design section); §23 lists TQ-1…TQ-7
- [x] P0.3 Author ARCHITECTURE.md and ENGINEERING_RULES.md | Layer: DOC | Depends: P0.1 | Verify: ARCHITECTURE §10 prohibitions and ENGINEERING_RULES §1 non-negotiables present
- [x] P0.4 Author ROADMAP.md, PROJECT_CONTEXT.md, DESIGN_SYSTEM.md | Layer: DOC | Depends: P0.3 | Verify: ROADMAP overview lists phases 0–30; DESIGN_SYSTEM §4 defines the core components
- [x] P0.5 Author DATABASE, API, SECURITY, TESTING, DEPLOYMENT, AI_DEVELOPMENT_GUIDE | Layer: DOC | Depends: P0.2 | Verify: all 13 .md deliverables exist in `docs/` (Phase 0 deliverables list = 13; `Get-ChildItem docs -Filter *.md` → 13)
- [x] P0.6 Cross-document conflict review against repository reality | Layer: DOC | Depends: P0.5 | Verify: CONF-1…CONF-6 + GAP-1 recorded in PROJECT_CONTEXT §10/§11 and reported to the user for decision
- [x] P0.7 Fill PROJECT_CONTEXT: Current State, phase log, open questions, building-block registry | Layer: DOC | Depends: P0.6 | Verify: no placeholder cells ("fill in") remain in PROJECT_CONTEXT.md
- [x] P0.8 Phase 0 approval sign-off; open questions accepted at their defaults or overridden | Layer: DOC | Depends: P0.7 | Verify: PROJECT_CONTEXT phase log shows Phase 0 = done with an approval note (commit hash recorded after git init)

**Verification evidence — handoff session 2026-09-30 (all Phase 0 `[x]` tasks re-run):**
- P0.1 ✓ `PRD.md` L86 `§5.1` FR table present; L327–332 `§13` OQ-1…OQ-6 with defaults
- P0.2 ✓ `TDD.md` L423 `§21` phase→design map (rows 1…26–29); L465–471 `§23` TQ-1…TQ-7 — Verify wording corrected this session ("every phase" → "phases 1–29")
- P0.3 ✓ `ARCHITECTURE.md` L118 `§10` with 6 prohibitions; `ENGINEERING_RULES.md` L5 `§1` with 10 non-negotiables
- P0.4 ✓ ROADMAP overview = 31 rows (phases 0–30); `DESIGN_SYSTEM.md` L41 `§4` with 11 core components
- P0.5 ✓ all 13 deliverables exist in `docs/` — **count corrected 14 → 13 this session** (miscount; deliverables list itself is 13); **moved from repo root into `docs/` 2026-09-30 per user instruction, resolving CONF-1 in favour of TDD §5
- P0.6 ✓ `PROJECT_CONTEXT.md` §10/§11 hold all 7 conflict rows: CONF-1…CONF-6 + GAP-1, all `Open`; reported to user (prior session final report + this session's status reply)
- P0.7 ✓ `Select-String PROJECT_CONTEXT.md 'fill in'` → 0 matches
- P0.8 ✓ **Approved by the user 2026-09-30** — OQ-1…OQ-6 and TQ-1…TQ-7 accepted at their defaults; CONF-1…CONF-6 and GAP-1 all decided in the same reply (decisions recorded in PROJECT_CONTEXT §10). Approval note written to the PROJECT_CONTEXT phase log (commit hash deferred to P1.2 — no git yet)

### Phase 1 — Repository & Infrastructure
- **Scope:** monorepo layout, Docker Compose (MySQL, Redis, MinIO, Nginx), environment templates, `.gitignore`, basic README, lint/format configs.
- **Exit:** `docker compose up` starts all services healthy; no secrets committed.

- [x] P1.1 Create monorepo layout (`backend/`, `frontend/`, `infra/`, `docs/`) | Layer: INFRA | Depends: P0.8 (CONF-1 decided: docs stay in `docs/`) | Verify: directory tree matches TDD §5 with the 13 docs in `docs/`
- [x] P1.2 Git init, branch convention `phase/NN-short-name`, conventional-commit baseline | Layer: INFRA | Depends: P1.1 | Verify: `git status` clean and `git log --oneline` shows the initial commit
- [x] P1.3 Docker Compose: MySQL, Redis, MinIO, Nginx with healthchecks (topology per CONF-2: infra by default, `--profile full` also runs backend/frontend) | Layer: INFRA | Depends: P1.1 (CONF-2 decided) | Verify: `docker compose up -d --wait && docker compose ps` shows all services healthy
- [x] P1.4 Environment templates: `.env.example` with placeholders, `.gitignore` excludes `.env` | Layer: INFRA | Depends: P1.1 | Verify: `git check-ignore -v .env` succeeds; repo-wide search finds no secret values
- [x] P1.5 Lint/format configs: backend (Spotless) and frontend (ESLint, Prettier, `tsc --strict`) | Layer: INFRA | Depends: P1.1 | Verify: `./mvnw spotless:check` exits 0; `npm run lint` exits 0
- [x] P1.6 Stack smoke test: healthchecks green, DB/Redis/MinIO ports bound to localhost, no management ports published | Layer: INFRA | Depends: P1.3 | Verify: `docker compose ps` all healthy; `docker compose config` shows ports published to 127.0.0.1
- [x] P1.7 README with local setup commands that run as written | Layer: DOC | Depends: P1.3 | Verify: copy-paste the README commands into a clean shell; the stack starts
- [x] P1.8 Update PROJECT_CONTEXT (local setup, phase log) and commit | Layer: DOC | Depends: P1.6, P1.7 | Verify: PROJECT_CONTEXT Local Setup matches verified commands; `git log --oneline` shows the docs commit

**Verification evidence — Phase 1 (session 2026-09-30):**
- P1.1 ✓ root tree = `docs/` (13 `.md`), `backend/`, `frontend/`, `infra/`, `.github/workflows/` — matches TDD §5 top level; `.gitkeep` placeholders added to the four empty dirs so P1.2's initial commit captures the skeleton
- P1.2 ✓ user ran `git init` + pushed `b883039 "Initial project setup"` to `origin/main` (github.com/AbdulRahman567/Hopital-Health-Care); `git status` clean, `git log --oneline` shows the initial commit. Phase branch `phase/01-infra` created per ENGINEERING_RULES §10; conventional-commit rule recorded there and followed from the next commit on (the user's initial import message is left as-is — history already pushed)
- P1.3 ✓ `docker compose up -d --wait` EXIT=0; `docker compose ps` → **mysql, redis, minio, nginx all healthy**, every port on 127.0.0.1 (:3306/:6379/:9000-9001/:80). Smoke: nginx `/healthz`=ok, MySQL 8.4.11, Redis PONG (password auth), MinIO `/minio/health/live`=200, `/api/`→502 until the backend exists (nginx resolver trick, starts without upstreams). Three blockers resolved **with user approval**: (a) MinIO official images deleted from Docker Hub + Quay and `dl.min.io` returns 410 → local dev uses `openvidu/minio:RELEASE.2026-07-17T12-07-51Z` (production stays AWS S3); (b) native Windows Redis service owned :6379 → service stopped + disabled (reversible: `sc.exe start Redis`); (c) MinIO volume mounted at the image-declared `/bitnami/minio/data` (container runs as uid 1001)
- P1.4 ✓ `git check-ignore -v .env` → `.gitignore:2:.env` (exit 0); same rule hits `infra/.env`; `git check-ignore infra/.env.example` → exit 1 (template trackable, `!.env.example` negation works). Secret search: `git grep -E "hms_local_(root|app|redis|minio)_pw"` → 0 matches in tracked files; working-tree scan (excluding `.git/` and `infra/.env`) → 0 matches. `git status` shows `infra/.env.example`, never `infra/.env`. `.gitignore` extended per user decision: env rules + build (`target/`, `dist/`, `node_modules/`, `.next/` …) + IDE/OS (`idea/`, `*.iml`, `.DS_Store`, `Thumbs.db`); `*.jar` deliberately NOT ignored so `.mvn/wrapper/maven-wrapper.jar` stays committable (P1.5)
- P1.5 ✓ **backend:** `./mvnw spotless:apply` + `./mvnw spotless:check` → both exit 0. No system `mvn` exists → Maven Wrapper 3.3.4 (`only-script` type, no wrapper jar) bootstraps Maven 3.9.9 from Central on first run; Spotless 2.43.0 + google-java-format 1.22.0 over `src/main|test/java`; source root `com.healthcare.hms` (TDD §5) seeded with `package-info.java`. **frontend:** `npm install` → 111 packages; `npm run lint` → **exit 0** (ESLint 9 flat config `eslint.config.mjs`, typescript-eslint 8 recommended, `eslint-config-prettier` applied last); `npm run typecheck` → exit 0 (`tsc --noEmit`, `strict` + `noUncheckedIndexedAccess`); `npm run format:check` → exit 0 (Prettier 3, config normalized via `npm run format`). Toolchain: JDK 21.0.11 · Node 24.19.0 · npm 11.17.0. `.gitattributes` pins `mvnw`/`*.sh` to LF so the wrapper also runs in Git Bash/CI
- P1.6 ✓ `docker compose ps` → mysql/redis/minio/nginx **all Up (healthy)**, exit 0. `docker compose config` → exit 0; published ports `80, 3306, 6379, 9000, 9001` each carry `host_ip: 127.0.0.1` (5 ports, 5 loopback bindings — nothing on 0.0.0.0; MinIO console :9001 is loopback-only, satisfying "no management ports published"). `docker compose config --services` → `nginx, redis, minio, mysql` only (backend/frontend gated behind `--profile full` per CONF-2)
- P1.7 ✓ README.md (root) written with a paste-able quick start; verified by executing its exact commands in a **clean `powershell -NoProfile` process**: `docker compose down` → exit 0; first-run `cp .env.example .env` → exit 0 (placeholder file created; existing `infra/.env` rename-protected, then restored — README marks cp as "first run only"); `docker compose up -d --wait` → exit 0 with nginx/mysql/redis/minio all *Healthy*; `docker compose ps` → 4/4 healthy. Side fix: `infra/.env.example` header em-dash → ASCII hyphen (PS 5.1 `Get-Content` reads UTF-8-no-BOM as ANSI → mojibake in the template users copy)
- P1.8 ✓ PROJECT_CONTEXT refreshed for phase close: §16 Local Setup now holds the **exact commands verified in P1.5/P1.7** (cd infra → cp .env.example .env (first run) → `docker compose up -d --wait` → `docker compose ps`; stop/reset; `./mvnw spotless:check`; `npm install` + `npm run lint`); §4 Current State / §5 Next action (Phase 2 on instruction only) / §6 Working tree / §12 Building blocks / §13 Gotchas updated; phase-log row 1 → **Done ☑** with commit hashes. ROADMAP overview row 1 → ☑. Phase 1 Exit re-checked live: `docker compose ps` 4/4 healthy · `git grep hms_local_*_pw` → 0 matches · `git check-ignore -v .env` → matched. This docs commit is the last entry in `git log --oneline` for the phase

### Phase 2 — Backend Foundation
- **Scope:** Spring Boot skeleton, package structure, response envelope, error handling, pagination helpers, base entities, OpenAPI (dev only), JSON logging, health endpoints.
- **Exit:** App boots; error and envelope tests pass; actuator restricted.

- [x] P2.1 Spring Boot 3 / Java 21 skeleton with TDD §5 package layout | Layer: BE | Depends: P1.5 | Verify: `./mvnw -q compile` exits 0
- [x] P2.2 Response envelope + pagination helpers per API.md §3 | Layer: BE | Depends: P2.1 | Verify: `./mvnw test -Dtest=ResponseEnvelopeTest` passes
- [x] P2.3 Centralized `@RestControllerAdvice` with field-level validation errors | Layer: BE | Depends: P2.2 | Verify: `./mvnw test -Dtest=ApiExceptionHandlerTest` asserts 422 + `error.fields[]` naming the exact field
- [x] P2.4 Structured JSON logging with `traceId`, no PHI/secrets | Layer: BE | Depends: P2.2 | Verify: `./mvnw test -Dtest=JsonLoggingTest` asserts `traceId` present in output
- [x] P2.5 Health endpoints + actuator restricted (only health public) | Layer: BE | Depends: P2.1 | Verify: `./mvnw test -Dtest=ActuatorSecurityTest` (health 200, other endpoints 401/404)
- [x] P2.6 OpenAPI enabled in dev only, disabled/protected in prod profile | Layer: BE | Depends: P2.1 | Verify: `./mvnw test -Dtest=OpenApiVisibilityTest` (dev 200, prod profile 404)
- [x] P2.7 Startup secret validation: app fails to start on missing/placeholder JWT secret | Layer: BE | Depends: P2.1 | Verify: `./mvnw test -Dtest=SecretValidationTest` asserts context fails to load
- [x] P2.8 Unit tests for envelope, errors, pagination helpers | Layer: BE | Depends: P2.3 | Verify: `./mvnw test` → BUILD SUCCESS, 0 failures
- [x] P2.9 Update PROJECT_CONTEXT phase log (+ API/error docs if behavior differs) | Layer: DOC | Depends: P2.8 | Verify: phase log row for Phase 2 committed

**Verification evidence — Phase 2 (session 2026-10-01):**
- P2.1 ✓ `./mvnw -q compile` exit 0 (commit `43dd558`) — `HealthcareHmsApplication`, `application{,-dev,-prod}.yml`, `logback-spring.xml`, 20 `package-info.java` seeding TDD §5 modules (`common{,api,exception,logging}`, `config`, `tenant`, `auth`, `authz`, `organization`, `staff`, `patient`, `appointment`, `clinical`, `history`, `prescription`, `lab`, `document`, `billing`, `notification`, `audit`, `search`)
- P2.2 ✓ `ResponseEnvelopeTest` → **8 tests, 0 failures** exit 0 (commit `85596ae`) — `ApiResponse<T>` NON_NULL, `PageMeta`/`PaginationMapper`/`PageParams` (default 20, max 100, page ≥0), `TraceIds`
- P2.3 ✓ `ApiExceptionHandlerTest` → **10 tests, 0 failures** exit 0 (commit `557a8df`) — 422 + `fields[]` naming the exact field, unknown JSON property 422, malformed 400, type mismatch 400, missing param 422, 404/409/500 envelopes, traceId surfaced (standalone MockMvc with `FAIL_ON_UNKNOWN_PROPERTIES` mapper mirroring app config)
- P2.4 ✓ `JsonLoggingTest` → **2 tests, 0 failures** exit 0 (commit `d7ae274`) — `TraceIdFilter` (X-Request-Id sanitize/generate → MDC), `request_completed` debug line without query string; encoder 9.0 uses `LogstashEncoder` (no `LoggingEventEncoder` class)
- P2.5 ✓ `ActuatorSecurityTest` → **4 tests, 0 failures** exit 0 (commit `08b838f`) — health 200 UP; metrics/env/beans 401; `/api/v1/patients` 401 UNAUTHENTICATED envelope; no health components leak. `SecurityConfig` deny-by-default (`anyRequest().denyAll()`, CSRF kept `withDefaults()`, JSON 401/403 entry points)
- P2.6 ✓ `OpenApiVisibilityTest` → **2 tests, 0 failures** exit 0 (commit `b8f66fe`) — dev profile: swagger-ui + `/v3/api-docs` 200; `@Nested` `@SpringBootTest(properties="spring.profiles.active=prod")`: both 404 (springdoc `enabled=false` + security permits the paths so its own 404 shows)
- P2.7 ✓ `SecretValidationTest` → **4 tests, 0 failures** exit 0 (commit `5cab819`) — `JwtSecretValidator` (min 32 chars, placeholder deny-list, never echoes secret): missing/placeholder/too-short each fail context startup with the exact message, 48-char secret validates. **Gotcha:** test args must be `--key=value` (leading `--`) or Spring ignores them and the surefire-provided secret wins
- P2.8 ✓ `./mvnw test` exit 0 — **30 tests, 0 failures** (`8+10+2+4+2+4`; OpenApi's 2 run in `@Nested` classes). Surefire injects `hms.security.jwt-secret` test-only value so every `@SpringBootTest` context passes P2.7 validation (cmd-line `--` args still override it)
- P2.9 ✓ this docs commit (ROADMAP evidence + PROJECT_CONTEXT §4–§7/§12–§13 + `docs/progress.md` refreshed; API/error docs unchanged — envelopes behave exactly as API.md §3 specifies)

**Re-verification — session 2026-10-02 (plan §3.4 option A: verify & reuse `phase/02-backend`; stale `target/` cleaned first):**
- `./mvnw -q clean` → exit 0 · `./mvnw -q compile` → exit 0 · `./mvnw -q spotless:check` → exit 0
- Each named verify re-run individually: `ResponseEnvelopeTest` PASS · `ApiExceptionHandlerTest` PASS · `JsonLoggingTest` PASS · `ActuatorSecurityTest` PASS · `OpenApiVisibilityTest` PASS · `SecretValidationTest` PASS
- `./mvnw test` → **BUILD SUCCESS — 30 tests, 0 failures, 0 skipped** (all six test classes present in surefire reports)
- Boot smoke (`HMS_JWT_SECRET` set): app starts · `/actuator/health` → **200** `{"status":"UP","groups":["liveness","readiness"]}` · dev `/swagger-ui/index.html` → **200** · `/actuator/env` → **401** · `/api/v1/patients` → **401**
- Fail-fast re-checked live: `spring-boot:run` with no `HMS_JWT_SECRET` → startup aborts ("refuses to start without it")
- Secret scan: `git grep` → only the deliberately labelled surefire **test-only-synthetic** value + docs; `infra/.env` ignored (`!! infra/.env`); no real credential committed
- **Base entities (plan §10.2):** deferred to Phase 3 with the first migration (`BaseEntity`/`TenantOwnedEntity`) — no schema exists yet, so no entities were invented in Phase 2
- Phase closed: merged `--no-ff` into `main` as **`a982918`** (2026-10-02); `docs/features.md` group C ticked in `a1d51ff`

### Phase 3 — Database Foundation
- **Scope:** Flyway setup, baseline migrations (tenants, users, roles, permissions, audit base), naming/index conventions.
- **Exit:** Migrations run on an empty DB and the Testcontainers integration test passes; the CI stage lands in P26.3 (CONF-4 decided 2026-09-30).

- [x] P3.1 Flyway integration; `ddl-auto=validate` in all profiles | Layer: BE | Depends: P2.8 | Verify: `./mvnw test -Dtest=FlywayStartupTest` (context starts, schema validated)
- [x] P3.2 Migration V1: `tenants`, `users` with audit columns and `tenant_id` rules (per CONF-5) | Layer: DB | Depends: P3.1 | Verify: `./mvnw test -Dtest=MigrationV1IT` on empty Testcontainers MySQL
- [x] P3.3 Migration V2: `roles`, `permissions`, `role_permissions`, `user_roles` + permission seed | Layer: DB | Depends: P3.2 | Verify: `./mvnw test -Dtest=MigrationV2IT` asserts seeded permission rows > 0
- [x] P3.4 Migration V3: `audit_logs` append-only base table + audit-column conventions | Layer: DB | Depends: P3.2 | Verify: `./mvnw test -Dtest=MigrationV3IT`
- [x] P3.5 Index rules: every FK indexed, composite indexes lead with `tenant_id` | Layer: DB | Depends: P3.4 | Verify: `./mvnw test -Dtest=IndexConventionIT` (information_schema assertions)
- [x] P3.6 Tenant isolation at DB level: `tenant_id NOT NULL` rejects NULL | Layer: DB | Depends: P3.2 | Verify: `./mvnw test -Dtest=TenantIdNotNullIT` expects constraint failure
- [x] P3.7 Migration test from a clean database in one command | Layer: BE | Depends: P3.5 | Verify: `./mvnw verify -Dtest=MigrationIT` → BUILD SUCCESS
- [x] P3.8 Update DATABASE.md conventions (if deviated) + PROJECT_CONTEXT phase log | Layer: DOC | Depends: P3.7 | Verify: docs committed; no unrecorded deviations

**Verification evidence — Phase 3 (session 2026-10-02, branch `phase/03-database`; later merged `--no-ff` to `main` as `493c6e0` and pushed):**
- P3.1 ✓ `./mvnw test -Dtest=FlywayStartupTest` → **5 tests, 0 failures** (commit `c92b851`) — `flyway-core` + `flyway-mysql` + `spring-boot-starter-data-jpa` + `mysql-connector-j` (runtime) + `spring-boot-testcontainers`/`testcontainers:{mysql,junit-jupiter}`; `ddl-auto: validate` set in `application.yml` **and re-pinned** in `application-dev.yml` / `application-prod.yml`; `DataSourceSecretValidator` `@Profile("prod")` on D7 names (`HMS_DB_URL`/`HMS_DB_USERNAME`/`HMS_DB_PASSWORD`); D1 wiring added in `src/test/resources/META-INF/spring.factories` (`ApplicationContextInitializer` **and** `ContextCustomizerFactory`) so **no pre-existing test class was touched**
- P3.2 ✓ `./mvnw test -Dtest=MigrationV1IT` → **8 tests, 0 failures** (commit `1048264`) — `tenants` + `users` (BINARY(16) UUID PKs, `DATETIME(6)` UTC, `VARCHAR`+`CHECK` enums, full §2 audit columns); reserved platform tenant seeded at `00000000-0000-0000-0000-000000000001`; `uq_users_tenant_email` allows one email per tenant but the same email in two tenants; `chk_users_status`/`chk_tenants_status` reject unknown values; `fk_users_tenants` rejects an orphan tenant; **D2 delivered** — `BaseEntity` + `TenantOwnedEntity` (no `@TenantId` yet, that is P4.4)
- P3.3 ✓ `./mvnw test -Dtest=MigrationV2IT` → **11 tests, 0 failures** (commit `cfb9b05`) — `permissions` seeded with **53 rows** = plan §5.4 exactly (asserted as a frozen count, plus `code = module || '_' || action` and non-null `description`), `roles`, `role_permissions`, `user_roles` with CONF-5 composite FKs carrying `tenant_id`; a cross-tenant `(tenant_id, role_id)` pair and a mismatched `user_roles` row are both rejected; supporting parent keys `uq_roles_tenant_id` / `uq_users_tenant_id` added (V1 is immutable)
- P3.4 ✓ `./mvnw test -Dtest=MigrationV3IT` → **8 tests, 0 failures** (commit `06e757e`) — `audit_logs` append-only shape (`created_at` present, `updated_at`/`updated_by`/`version` **absent**), `tenant_id` NOT NULL, both TDD §9.3 composites present in the declared order, FKs to `tenants`/`users` each indexed, insert/select round-trip within one tenant; append-only `INSERT`/`SELECT` grants explicitly deferred to **P19.1** in the migration header
- P3.5 ✓ `./mvnw test -Dtest=IndexConventionIT` → **4 tests, 0 failures** (commit `06ec4f9`) — permanent guard reading `information_schema.statistics`/`key_column_usage`/`referential_constraints`/`table_constraints` over **every** table (no table list to maintain): every FK column indexed · every composite index leads with `tenant_id` except platform tables `tenants`/`permissions` and single-column keys · zero `ON DELETE CASCADE` · names follow `pk_`/`fk_`/`uq_`/`idx_` (+ `chk_`), with `PRIMARY` accepted for primary keys
- P3.6 ✓ `./mvnw test -Dtest=TenantIdNotNullIT` → **7 tests, 0 failures** (commit `0159597`) — `tenant_id = NULL` rejected on `users`, `roles`, `role_permissions`, `user_roles`, `audit_logs`; `information_schema.columns.IS_NULLABLE = 'NO'` asserted for all five; a positive-control row insert proves the failures come from the constraint rather than from bad SQL
- P3.7 ✓ `./mvnw verify -Dtest=MigrationIT` → **BUILD SUCCESS, 3 tests, 0 failures** (commit `6f98beb`) — empty schema → `migrationsExecuted = 3` → history order `1,2,3` → `flyway info` current `3` → a real `SpringApplication` boots against that migrated schema with `hibernate.hbm2ddl.auto = validate` (the D1 initializer is bypassed via `hms.test.datasource.override=true`) → second `migrate()` executes **0** migrations
- P3.8 ✓ this docs commit — ROADMAP checkboxes + evidence, `DATABASE.md` §2/§8 deviations recorded, `PROJECT_CONTEXT` §4–§7/§12–§13, `docs/progress.md`, `docs/features.md` group D

**Final gate (2026-10-02):** `./mvnw spotless:check` → 0 · `./mvnw test` → **BUILD SUCCESS — 76 tests, 0 failures, 0 skipped** (30 Phase 2 unchanged + 46 Phase 3: 5+8+11+8+4+7+3).

**Deviations from the design docs — every one recorded (P3.8 Verify: "no unrecorded deviations"):**
1. **`audit_logs` has no `updated_at`/`updated_by`/`version`** — DATABASE §2 audit columns vs §8 append-only. §8 wins for this table; recorded in `DATABASE.md` §8 and in the V3 header.
2. **Primary-key names surface as `PRIMARY` in MySQL** — `CONSTRAINT pk_… PRIMARY KEY` parses, but `mysql:8.4` always stores the index/constraint name as `PRIMARY` (verified live against `information_schema`). `pk_` stays in the DDL as documentation; `IndexConventionIT` and `MigrationV3IT` accept `PRIMARY`. Recorded in `DATABASE.md` §2.
3. **MySQL auto-creates an `fk_<table>_<ref>` index** for any FK no existing index can service (`fk_audit_logs_users`, `fk_user_roles_roles` today) — the names still carry the `fk_` prefix, so no new convention is introduced. Recorded in `DATABASE.md` §2.
4. **`users.status` values `('PENDING','ACTIVE','INACTIVE')`** — TDD §9.2 never documented them; this is the minimal set covering invite-pending, active and `STAFF_DEACTIVATE` (V1 header).
5. **D4 — `users.first_name`/`last_name`** added beyond TDD §9.2 for human-readable actor attribution (V1 header).
6. **`TINYINT(1)` raises MySQL 8.4 warning 1681** (integer display width deprecated) — accepted; `TINYINT(1)` is mandated by DATABASE §2 and the warning is cosmetic.
7. **Migration suites share one MySQL container per JVM** with a fresh empty schema per suite, instead of one container per test class: identical Flyway starting point (empty database), ~40 s instead of ~4 min. That server is deliberately separate from `TestDatabase`, so `hms_test` is never migrated, dropped or mutated by the migration suites.

**Explicitly not in scope (P3 plan header):** `TenantContext` / `@TenantId` filtering (Phase 4), `refresh_tokens`/`verification_tokens` (P5.1), append-only DB grants + `AuditAppendOnlyTest` (P19.1), CI stage (P26.3), HTTP layer.

### Phase 4 — Multi-Tenancy
- **Scope:** `TenantContext`, resolution filter, Hibernate tenant filtering, `TenantOwnedEntity`, tenant-prefixed cache/storage helpers.
- **Exit:** Cross-tenant repository tests prove isolation; client tenant hints cannot override.

- [x] P4.1 `TenantContext` request-scoped holder, cleared in `finally` | Layer: BE | Depends: P3.7 | Verify: `./mvnw test -Dtest=TenantContextTest`
- [x] P4.2 `TenantContextFilter`: tenant taken from JWT claim only | Layer: BE | Depends: P4.1 | Verify: `./mvnw test -Dtest=TenantContextFilterTest` (missing claim → rejected)
- [x] P4.3 Client tenant hints (`X-Tenant-ID`, body, path) ignored or 404 on mismatch | Layer: BE | Depends: P4.2 | Verify: `./mvnw test -Dtest=TenantHintRejectionTest`
- [x] P4.4 `TenantOwnedEntity` + Hibernate tenant filtering (TQ-1 default `@TenantId`) | Layer: BE | Depends: P4.2 | Verify: `./mvnw test -Dtest=HibernateTenantFilterTest`
- [x] P4.5 Cross-tenant repository isolation suite (Tenant A vs Tenant B) | Layer: BE | Depends: P4.4 | Verify: `./mvnw test -Dtest=TenantIsolationIT` → 0 failures
- [x] P4.6 Tenant-prefixed helpers: Redis `t:{tenantId}:` and storage `tenants/{tenantId}/` | Layer: BE | Depends: P4.1 | Verify: `./mvnw test -Dtest=TenantKeyPrefixTest`
- [x] P4.7 Tenant propagation helper for async job payloads (worker arrives in Phase 18) | Layer: BE | Depends: P4.1 | Verify: `./mvnw test -Dtest=JobTenantPayloadTest`
- [x] P4.8 Isolation-chain security review + record any ADR in TDD §22 | Layer: DOC | Depends: P4.5 | Verify: review findings listed in PROJECT_CONTEXT; ADR raised only if the design changed

**Verification evidence — Phase 4 (P4.1…P4.7 committed 2026-10-03 on `phase/04-multitenancy`, merged `--no-ff` into `main` as `c2ed23b` and pushed; P4.8 review + docs re-verified and re-committed the same day on `phase/05-authentication` after the Phase 5 rollback):**
- P4.1 ✓ `./mvnw test -Dtest=TenantContextTest` → **10 tests, 0 failures** (commit `5a2145e`) — `TenantContext` ThreadLocal holder with `set`/`clear`/`find`/`require`/`run`; `require()` throws `IllegalStateException` on an empty context (fail closed, ENGINEERING_RULES §1.2); `run()` saves/restores the previous value in `finally`; deliberately **not** `InheritableThreadLocal`; two threads cannot see each other's value
- P4.2 ✓ `./mvnw test -Dtest=TenantContextFilterTest` → **11 tests, 0 failures** (commit `fd28bf6`) — **D1-A**: `spring-boot-starter-oauth2-resource-server` + HS256 `NimbusJwtDecoder` built from the existing `hms.security.jwt-secret` (`JwtSecretValidator` kept as a bean parameter so P2.7's message still wins over Nimbus key errors); `TenantContextFilter` added after `BearerTokenAuthenticationFilter` (TDD §4.1 order); missing/unparseable `tenantId` claim → **401 UNAUTHENTICATED**, chain never continues; valid claim → 403 from `denyAll()` (the 401-vs-403 wiring proof); expired/bad-signature → JSON envelope; `ApiErrorWriter` renders the API.md §3 envelope from outside `DispatcherServlet`; context empty after the chain **and** after it throws; full suite back to **97 tests, 0 failures** (all 76 Phase 2/3 tests unchanged)
- P4.3 ✓ `./mvnw test -Dtest=TenantHintRejectionTest` → **6 tests, 0 failures** (commit `deb8b02`) — `X-Tenant-ID` absent/blank → continue · equal to the token tenant → continue · different (including a *different valid UUID*) → **404 NOT_FOUND and the chain is never invoked**; header only evaluated when a tenant was resolved, so a stray hint on a public route stays harmless; path/body legs structurally closed (no endpoint accepts a tenant id, P2.3 turns an unknown `tenantId` body property into 422)
- P4.4 ✓ `./mvnw test -Dtest=HibernateTenantFilterTest` → **8 tests, 0 failures** (commit `b2976ee`) — `@TenantId` on `TenantOwnedEntity.tenantId` + `TenantIdentifierResolver` registered through `HibernatePropertiesCustomizer` (Spring Boot auto-registers neither bean); `findAll`/`count`/`findById`/`findByEmail` all scoped to the bound tenant; a foreign row is `Optional.empty()` (ADR-006's primitive); `save()` under tenant A writes `tenant_id = A` (asserted against `users.tenant_id` via raw SQL); **empty context throws** instead of degrading into an unfiltered read; `User` inherits the filter from the `@MappedSuperclass`; **no migration** — `ddl-auto: validate` stayed green
- P4.5 ✓ `./mvnw test -Dtest=TenantIsolationIT` → **7 tests, 0 failures** (commit `ed1646f`) — `hospital-a` (3 rows) vs `hospital-b` (2 rows) through `UserRepository` only: listings, direct id/email lookups, `count()` and page totals never leak the other tenant's numbers; native guardrail **with** `tenant_id` returns one tenant, **without** it returns both tenants *even with a tenant bound* — the live proof of ENGINEERING_RULES §5 / TDD §6.3; `TenantContext` asserted empty after every test
- P4.6 ✓ `./mvnw test -Dtest=TenantKeyPrefixTest` → **22 tests, 0 failures** (commit `9cfebc9`) — **D7**: `TenantKeys` is a pure static builder (no Redis/storage client); exact `t:<tid>:rl:<scope>:<id>` / `t:<tid>:cache:<name>:<key>` / `t:<tid>:notif:<userId>` / `tenants/<tid>/…` formats, two tenants never collide, null tenant and blank parts rejected, `..`/`/`/`\` rejected in storage parts, no key can omit its prefix
- P4.7 ✓ `./mvnw test -Dtest=JobTenantPayloadTest` → **7 tests, 0 failures** (commit `b31dad7`) — `TenantJobPayload(tenantId, actorId, capturedAt)` + `TenantContext.toJobPayload()`/`runWith()`; capture → clear → worker observes the payload tenant; previous value restored; context cleared when the job throws; null tenant/capturedAt/payload rejected; capture with no context fails closed; two sequential jobs do not bleed; no executor/queue/broker (outbox worker = Phase 18)
- P4.8 ✓ this docs commit — AI_DEVELOPMENT_GUIDE §7/§8 review run read-only over the Phase 4 diff; findings with severity/status in `PROJECT_CONTEXT` §11; TDD §6.3 eight-layer proven/deferred table recorded; **no ADR raised** (see below)

**Final gate (re-run 2026-10-03):** `./mvnw -q spotless:check` → 0 · `./mvnw test` → **BUILD SUCCESS — 147 tests, 0 failures, 0 skipped** (76 Phase 1–3 unchanged + 71 Phase 4: 10+11+6+8+7+22+7).

**Isolation-chain security review — P4.8 summary (full findings in `PROJECT_CONTEXT` §11):** P0 Critical **0** · P1 High **0** · P2 Medium **0** · P3 Low **3** (`nativeQueryWithoutTenantId()` living in main code as a documented guardrail; `TenantContext.set` public and guarded by convention only; HS256 shared secret means a secret holder can mint any tenant claim — accepted under D1-A) · Informational **5** (refresh-phase sentinel in the resolver; only `users` is a JPA entity today; CSRF/rate-limiting unchanged from Phase 2 → P5.5/P5.6; path/body hint vectors closed until controllers exist; `config → tenant` edge not yet in ARCHITECTURE §3). **No ADR raised:** TQ-1 (`@TenantId`), D1-A (HS256) and D5's fail-closed intent are all unchanged — `@Filter` was not needed and no cross-tenant bypass was added.

**Deviations from the design docs — every one recorded (P4.8 Verify: "no design change → no ADR"):**
1. **`org.hibernate.annotations.TenantId`**, not `org.hibernate.tenant.TenantId` as the plan wrote — that package does not exist in Hibernate 6.6.53; annotation and behaviour are the TQ-1 default either way.
2. **`validateExistingCurrentSessions()`**, not `validateExistingCurrentMatch()` — the Hibernate 6.6 `CurrentTenantIdentifierResolver` method name.
3. **`MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER`** (`hibernate.tenant_identifier_resolver`), not `AvailableSettings.CURRENT_TENANT_IDENTIFIER_RESOLVER`, which does not exist in 6.6; applied through `HibernatePropertiesCustomizer` exactly as the plan required.
4. **Fail-closed resolver has a documented startup window.** Hibernate asks for the tenant on **every session creation**, and Spring Data JPA opens an EntityManager to validate derived repository queries *during context refresh* — before any request thread and before any tenant can be bound. A resolver that always throws prevents the application from starting (observed: context load failed on `UserRepository`). `TenantIdentifierResolver` therefore hands Hibernate a **zero UUID during refresh** — statements are scoped to a tenant that cannot exist (`tenant_id` FK rejects inserts, reads match nothing), never unfiltered — and `SmartInitializingSingleton.afterSingletonsInstantiated()` flips it to throw on an empty context once every singleton (repositories included) exists and before Tomcat accepts traffic. Plan risk 4/6 behaviour (empty context → exception, asserted by `HibernateTenantFilterTest`) is unchanged after startup. Recorded here and in the class javadoc; **not** an ADR because TQ-1 and D5's intent are intact.
5. **`TenantContextFilter` is constructed inside `SecurityFilterChain`**, not exposed as a `Filter` bean — Spring Boot would otherwise register it in the servlet container at `/*` and run it a second time.
6. **`User`'s no-arg constructor is `protected`**, so tests build users through `com.healthcare.hms.auth.UserFixtures` (test source, same package) instead of `new User()`.
7. **Architecture-boundary note for the next review:** `config` → `tenant` now exists (`SecurityConfig` registers `TenantContextFilter`); ARCHITECTURE §3 still lists `config` depending on `common` only. The dependency is one-way and required by the filter chain, but the boundary table should be re-read before the first `auth`/`patient` controller lands.

**Explicitly not in scope (P4 plan header):** controller/service/HTTP enforcement (no HTTP surface exists — `denyAll()` until Phase 5/6), Redis and object-storage *integration* (helpers only, P5.6/P16.6), the outbox worker that calls `runWith` (Phase 18), audit writing (Phase 19), and the Platform Admin cross-tenant path (D8 / GAP-1, P8.5 — must carry an audit event when it lands).

### Phase 5 — Authentication
- **Scope:** registration, email verification, login, access/refresh tokens with rotation and reuse detection, logout, password reset, rate limiting, lockout, secure cookies, startup secret validation.
- **Exit:** All auth flows tested including abuse cases; security review run.

- [x] P5.1 Migration: `refresh_tokens`, `verification_tokens` (hashed, expiring, single-use) | Layer: DB | Depends: P4.8 | Verify: `./mvnw test -Dtest=MigrationAuthIT`
- [x] P5.2 Hospital registration + email verification flow (FR-1.1/1.2/2.5) | Layer: BE | Depends: P5.1 (CONF-6) | Verify: `./mvnw test -Dtest=RegistrationVerificationTest` — unverified tenant cannot log in
- [x] P5.3 Password hashing (TQ-2 default Argon2id) + login issuing 10–15 min JWT | Layer: BE | Depends: P5.2 | Verify: `./mvnw test -Dtest=LoginTest` — success, uniform failure message, no enumeration
- [x] P5.4 Refresh rotation with reuse detection (reuse revokes the whole family) | Layer: BE | Depends: P5.3 | Verify: `./mvnw test -Dtest=RefreshRotationReuseTest`
- [x] P5.5 Refresh in HttpOnly/Secure/SameSite cookie + CSRF header check + logout revocation | Layer: BE | Depends: P5.4 | Verify: `./mvnw test -Dtest=CookieLogoutTest` asserts cookie flags and server-side revocation
- [x] P5.6 Redis rate limiting + progressive lockout, fail-closed for auth endpoints | Layer: BE | Depends: P5.3 | Verify: `./mvnw test -Dtest=RateLimitLockoutTest` — 429 with `Retry-After`; lockout after N failures
- [x] P5.7 Password reset: time-limited single-use token, response never reveals account existence | Layer: BE | Depends: P5.3 | Verify: `./mvnw test -Dtest=PasswordResetTest`
- [x] P5.8 Authorization + tenant isolation: unauthenticated → 401; tenant B user cannot read tenant A users | Layer: BE | Depends: P5.4 | Verify: `./mvnw test -Dtest=AuthTenantIsolationTest`
- [x] P5.9 Run Security Review prompt (AI_DEVELOPMENT_GUIDE §7); record findings | Layer: DOC | Depends: P5.8 | Verify: findings with severity/status appear in PROJECT_CONTEXT §11
- [x] P5.10 Update PROJECT_CONTEXT phase log + commit | Layer: DOC | Depends: P5.9 | Verify: phase log row for Phase 5 committed

**Verification evidence - Phase 5 (P5.1…P5.10 committed 2026-10-03/04 on `phase/05-authentication`, not yet merged):**
- P5.1 ✓ `./mvnw test -Dtest=MigrationAuthIT` → **14 tests, 0 failures** (commit `2c73963`) — `V4__refresh_and_verification_tokens.sql`: `refresh_tokens` + `verification_tokens`, both tenant-scoped; `token_hash` unique (a digest lookup is ±1 row, which is what makes the D1 secret leg safe); `type` CHECK; `used_at` nullable; **no `ON DELETE CASCADE`** (test cleanup deletes children before parents); index/naming conventions verified; `tenant_id NOT NULL`
- P5.2 ✓ `./mvnw test -Dtest=RegistrationVerificationTest` → **8 tests, 0 failures** (commit `e74ee93`, 37 files / 1819 insertions) — FR-1.1/1.2/1.3: 202 + `PENDING` + emailed link → `ACTIVE` (activation is automatic, OQ-1); only the SHA-256 digest is persisted; resend always 202 and never times out (FR-1.3); duplicate `slug` → 409 `DUPLICATE_RESOURCE` on `field: "hospitalName"` (D9's single declared exception); `hospitalSlug` is a lookup key, never a binding
- P5.3 ✓ `./mvnw test -Dtest=LoginTest` → **5 tests, 0 failures** (commit `a00a52f`) — FR-2.1: `DelegatingPasswordEncoder` + **BCrypt strength 12** (D3 = TQ-2's own fallback, zero new dependencies); access token **12 min** (D8, inside TDD §7's 10-15); claims `sub`/`tenantId`/`roles`/`jti`/`iat`/`exp`, HS256 via `NimbusJwtEncoder` over the existing `hms.security.jwt-secret` so P4.2's decoder accepts them unchanged (D10); **every** failure branch is one byte-identical 401 (D9) and the no-account path still spends a real BCrypt check (`DUMMY_HASH`) — no timing oracle; unverified tenant → 401; MFA enabled → 401, fail closed (D12)
- P5.4 ✓ `./mvnw test -Dtest=RefreshRotationReuseTest` → **7 tests, 0 failures** (commit `c479097`) — FR-2.2: rotation marks the presented row `ROTATED` and inserts a successor into the same `familyId`; replaying any still-live revoked row revokes the **whole family** `REUSED`, and `noRollbackFor` keeps that revocation committed under the rejection; 7-day sliding TTL capped by a 30-day absolute family age (D8); hash-only storage
- P5.5 ✓ `./mvnw test -Dtest=CookieLogoutTest` → **9 tests, 0 failures** (commit `484f2b9`) — FR-2.6 / D5 / D6: `hms_refresh` is `HttpOnly; SameSite=Lax; Path=/api/v1/auth` with `Secure` only under the `prod` profile; `X-Requested-With` required on `refresh` + `logout`, missing → 403 `ACCESS_DENIED` (401 without a cookie, 403 without the header); logout revokes server-side **and** clears the cookie in the same response; the raw token appears in no response body anywhere; `TenantContext` rebound from the refresh digest, never from a client hint
- P5.6 ✓ `./mvnw test -Dtest=RateLimitLockoutTest` → **7 tests, 0 failures**, plus `RateLimitPolicyTest` → **6 tests** (commit `0b76c24`, 23 files / 1626 lines) — SECURITY §12/§3 + D7: JVM-scoped Redis test container wired through the same `spring.factories` pattern as the database; `RateLimitFilter` charges the IP rows **before** the password is hashed, so BCrypt's cost cannot be turned into a DoS; account budgets are charged **before** the account lookup and lockout keys are built from slug+email, never a user id, so a 429 cannot enumerate; uniform 429 + `Retry-After`; progressive lockout (5 → 15 min, doubling, capped 1 h) expressed as the pure `lockDurationFor`; **Redis unreachable → 429 and the maximum lock (fail closed) on every path**
- P5.7 ✓ `./mvnw test -Dtest=PasswordResetTest` → **8 tests, 0 failures** (commit `5458c71`) — FR-2.3: identical 202 for known / unknown / no-such-slug, and only the real address is mailed; digest-only storage; `PasswordPolicy.validate` runs **before** the token is read, so a weak password is 422 for an invented link and a real one alike and the status code is not an oracle; a successful reset clears `failed_attempts`/`locked_until`, revokes every refresh family `PASSWORD_RESET` (SECURITY §15), and the old cookie then gets 401 on `/refresh`
- P5.8 ✓ `./mvnw test -Dtest=AuthTenantIsolationTest` → **10 tests, 0 failures** (commit `c55d067`, 675 lines, **no production code touched**) — 401 ladder + **403 for an authenticated caller** on a non-listed route (deny-by-default is not merely "no token") + a `prod` nested context where `/api/v1/patients` is 401 and `/v3/api-docs` + `/swagger-ui/index.html` are 404; A's credentials under B's slug (and the mirror) → byte-identical 401, no `accessToken`, no `Set-Cookie`; the `tenantId` claim always equals the account's own tenant; `X-Tenant-ID` only ever rejects (hint B → 404 on the probe and on `/api/v1/patients`, matching hint → 200, absent → 200); a validly-signed forged token with `tenantId=B` + `sub`=A's user reports `tenantId=B, visible=false`; refresh isolation proven by a **byte-for-byte row snapshot** across another tenant's rotations; `TenantContext.find()` empty after every request kind and again in `@AfterEach`
- P5.9 ✓ AI_DEVELOPMENT_GUIDE §7 run read-only over `4892016..HEAD` (86 files, 7875 insertions) — findings with severity/status in `PROJECT_CONTEXT` §11, see the review summary below; **no ADR raised**
- P5.10 ✓ this docs commit - ROADMAP checkboxes + this evidence block, `PROJECT_CONTEXT` §4/§5/§6/§7/§12/§13, `docs/progress.md`, `docs/features.md` group F, building-block registry

**Final gate (2026-10-04):** `./mvnw -q spotless:check` → 0 · `./mvnw clean test` → **BUILD SUCCESS - 221 tests, 0 failures, 0 skipped** (147 pre-existing + 74 new across 9 new classes: `MigrationAuthIT` 14, `RegistrationVerificationTest` 8, `LoginTest` 5, `RefreshRotationReuseTest` 7, `CookieLogoutTest` 9, `RateLimitLockoutTest` 7, `RateLimitPolicyTest` 6, `PasswordResetTest` 8, `AuthTenantIsolationTest` 10).

**D11 deviations - exactly the three declared exceptions, and nothing else.** Verified with `git diff --name-only 4892016..HEAD -- backend/src/test`, which returns only these three paths - no other pre-existing test class was edited:
1. `MigrationIT` — `MIGRATION_COUNT` 3→4, `EXPECTED_CURRENT_VERSION` "3"→"4", `containsExactly("1","2","3")` → `("1","2","3","4")` (commit `2c73963`), sanctioned by that file's own javadoc.
2. `TenantIdNotNullIT` — `refresh_tokens` and `verification_tokens` added to `TENANT_TABLES` (commit `2c73963`, the optional strengthening D11 allowed).
3. `src/test/resources/META-INF/spring.factories` — the accepted D1 test wiring extended with the JVM-scoped Redis container and its property sources (commit `0b76c24`).

**Test-only deviation recorded at P5.8:** `AuthTenantIsolationTest` registers a `TenantProbeController` at `/swagger-ui/tenant-probe` from a nested `@TestConfiguration`. It is the only path `SecurityConfig` leaves open without a method restriction, and springdoc serves that path from a *resource* handler, so an MVC mapping wins outright and never clashes. Registered as a `@Bean`, not a `@Component`, so it exists only in that suite's application context; nothing under `src/test` reaches a packaged application. The endpoint-level `WrongTenantTest` the plan's point 5 could not prove without a resource endpoint stays at **P6.7**.

**Security review - P5.9 summary (full findings in `PROJECT_CONTEXT` §11):** P0 Critical **0** · P1 High **0** · P2 Medium **1** (`SEC-1`: `RateLimitKeys.ip` documents that a reverse proxy is handled by `server.forward-headers-strategy`, but that property is configured nowhere while `infra/nginx/nginx.conf` sends `X-Real-IP`/`X-Forwarded-For` — behind nginx every client shares one `rl:ip:*` bucket, so 20 requests deny login to the whole deployment; `X-Forwarded-For` is deliberately not read, so there is **no spoofing bypass**, the gap is the opposite one) · P3 Low **3** (`SEC-2`: `RateLimitFilter.bucketFor` and `CustomHeaderCsrfFilter.shouldNotFilter` compare the raw `requestURI` while `SecurityConfig` and Spring MVC use `PathContainer` — probed with a scratch test and **not exploitable today**: `/x/` is stopped by `denyAll()` and `;`/`%3b` by Spring Security's `StrictHttpFirewall`, so the divergence is latent; `SEC-3`: addresses appear in lockout/limit logs - an exhaustive pass over every `log.` call under `com.healthcare.hms` found **no password, token, reset/verification link or hash** anywhere; `SEC-4`: `verify-email` and `reset-password` consume a single-use token without the `FOR UPDATE` lock rotation takes) · Informational **1** (`SEC-5`: earlier reset links stay valid until their own 30-minute expiry). **ISO-6** flipped from "deferred by design" to **delivered** at P5.5/P5.6; **ISO-7** re-audited with the first request bodies landing and **structurally closed for Phase 5**, to be re-read at P6.7. **No ADR raised:** TQ-2's Argon2id fallback to BCrypt was pre-authorised at D3 and ADR-003's cookie refresh at D6, so the review changed no design.

**Explicitly not in scope (P5 plan header):** RBAC / permissions / `@RequirePermission` / roles CRUD (Phase 6), `WrongTenantTest` (P6.7), the *invited*-user half of FR-2.5 (P9.3 reusing `verification_tokens.type`), the end-to-end `TenantActivationIT` workflow (P8.4), TOTP/OTP enrolment and enforcement (D12 = schema + login seam only), rich mail templates beyond the four plain-text mails, the async resend flow (P18.4), and the D6 deployment constraint that `SameSite=Lax` assumes one registrable domain for app + API (**record for P27**, no code change now).

### Phase 6 — Authorization / RBAC
- **Scope:** permission catalog, roles, role management, `@RequirePermission`, resource policy framework, field masking, ArchUnit rule "every endpoint has a permission".
- **Exit:** Wrong-role and wrong-tenant tests for every existing endpoint.

- [ ] P6.1 Permission catalog (`MODULE_ACTION`) in code + Flyway seed | Layer: DB | Depends: P5.10 | Verify: `./mvnw test -Dtest=PermissionCatalogTest` — code catalog matches seed rows
- [ ] P6.2 Roles CRUD (tenant-scoped) + custom roles from the catalog (FR-3.5) | Layer: BE | Depends: P6.1 | Verify: `./mvnw test -Dtest=RoleManagementTest`
- [ ] P6.3 `@RequirePermission` on every endpoint, deny by default | Layer: BE | Depends: P6.2 | Verify: `./mvnw test -Dtest=EndpointPermissionArchUnitTest` fails if any controller method lacks a permission
- [ ] P6.4 Resource policy framework (`PatientAccessPolicy` etc., OQ-2 default assigned/previously treated) | Layer: BE | Depends: P6.2 | Verify: `./mvnw test -Dtest=ResourcePolicyTest` — unassigned doctor denied, assigned allowed
- [ ] P6.5 `FieldMaskingService`: diagnosis/notes/vitals by permission, masked fields omitted | Layer: BE | Depends: P6.2 | Verify: `./mvnw test -Dtest=FieldMaskingTest` — receptionist response contains no clinical fields
- [ ] P6.6 Wrong-role tests for every existing endpoint (matrix allowed/denied) | Layer: BE | Depends: P6.3 | Verify: `./mvnw test -Dtest=EndpointPermissionMatrixTest` → 0 failures
- [ ] P6.7 Wrong-tenant tests for every existing endpoint (404, never 403, for foreign data) | Layer: BE | Depends: P6.3 | Verify: `./mvnw test -Dtest=WrongTenantTest` → 0 failures
- [ ] P6.8 Document the permission catalog + update PROJECT_CONTEXT phase log | Layer: DOC | Depends: P6.7 | Verify: docs committed; phase log row for Phase 6 present

### Phase 7 — Frontend Foundation
- **Scope:** Next.js app shell, auth flow with silent refresh, Axios layer, shared components (FormField with required marker, Select/Combobox with labels, DataTable, ConfirmDialog), design tokens, layout, permission hooks.
- **Exit:** Login/logout works; shared components have tests; no raw IDs anywhere.

- [ ] P7.1 Next.js 15 app shell: TS strict, Tailwind, shadcn tokens (light + dark) | Layer: FE | Depends: P1.5 | Verify: `npm run build` exits 0
- [ ] P7.2 Axios instance: attach access token, silent refresh on 401 via cookie, error normalization, `traceId` surfacing | Layer: FE | Depends: P7.1 | Verify: `npm test -- src/lib/api` (refresh + error-mapping tests pass)
- [ ] P7.3 Redux auth session slice + TanStack Query provider (server state not mirrored in Redux) | Layer: FE | Depends: P7.2 | Verify: `npm test -- src/store` passes
- [ ] P7.4 Shared components: FormField (required `*` + `aria-required`), SelectField/Combobox (`{value,label}`), DataTable, ConfirmDialog, Button, Toast, Empty/Error/Skeleton | Layer: FE | Depends: P7.1 | Verify: `npm test -- src/components` (Vitest + RTL) passes
- [ ] P7.5 Layout shell: permission-filtered sidebar, top bar with tenant name + user menu | Layer: FE | Depends: P7.4 | Verify: `npm run build` + component test renders shell
- [ ] P7.6 Login/logout wired to Phase 5 auth endpoints (silent refresh included) | Layer: FE | Depends: P7.3, P5.10 (CONF-3) | Verify: `npx playwright test tests/e2e/login-logout.spec.ts` passes
- [ ] P7.7 Permission hooks / `PermissionGate` (UX only, server enforces) | Layer: FE | Depends: P7.5, P6.3 | Verify: component test hides an action the user lacks
- [ ] P7.8 No-raw-IDs audit: selects, tables and labels render names, never IDs | Layer: FE | Depends: P7.4 | Verify: `npm run lint` + component tests asserting visible labels, not UUIDs
- [ ] P7.9 Update PROJECT_CONTEXT phase log + commit | Layer: DOC | Depends: P7.8 | Verify: phase log row for Phase 7 committed

### Phase 8 — Hospital & Organization Management
- **Scope:** hospital settings, departments CRUD with field-level validation errors, tenant activation workflow (no manual DB edits).
- **Exit:** Department creation and hospital settings work end-to-end.

- [ ] P8.1 Migration: `departments`, hospital settings columns on `tenants` | Layer: DB | Depends: P6.8, P7.9 | Verify: `./mvnw test -Dtest=MigrationOrgIT`
- [ ] P8.2 GET/PUT hospital settings (name, contact, timezone, working hours — FR-1.5) | Layer: BE | Depends: P8.1 | Verify: `./mvnw test -Dtest=HospitalSettingsTest`
- [ ] P8.3 Departments CRUD with precise field-level errors (FR-4.1) | Layer: BE | Depends: P8.1 | Verify: `./mvnw test -Dtest=DepartmentValidationTest` asserts `error.fields[]` names the offending field
- [ ] P8.4 Tenant activation workflow: verify → ACTIVE, never a manual DB edit (FR-1.4, OQ-1 default) | Layer: BE | Depends: P8.2 | Verify: `./mvnw test -Dtest=TenantActivationIT` — register → verify → login succeeds
- [ ] P8.5 Platform Admin suspend/reactivate blocks all tenant logins (FR-1.6) | Layer: BE | Depends: P8.4 (GAP-1) | Verify: `./mvnw test -Dtest=TenantSuspensionTest` — suspended tenant login → 401/403
- [ ] P8.6 Frontend: hospital settings + departments pages (FormField, DataTable, names not IDs) | Layer: FE | Depends: P8.3 | Verify: `npm test -- src/features/organization` + `npm run build`
- [ ] P8.7 Authorization + tenant isolation: department list scoped to tenant; wrong permission → 403 | Layer: BE | Depends: P8.3 | Verify: `./mvnw test -Dtest=DepartmentAuthzIsolationTest`
- [ ] P8.8 Update PROJECT_CONTEXT phase log + commit | Layer: DOC | Depends: P8.7 | Verify: phase log row for Phase 8 committed

### Phase 9 — Staff & Doctor Management
- **Scope:** staff profiles, invitation flow (secure, expiring), role assignment, deactivation with session revocation.
- **Exit:** Invite → accept → login works as an E2E test.

- [ ] P9.1 Migration: `staff_profiles`, `doctor_availability`, `invitations` | Layer: DB | Depends: P8.8 | Verify: `./mvnw test -Dtest=MigrationStaffIT`
- [ ] P9.2 Staff profiles: specialty, license number, department, availability (FR-4.3) | Layer: BE | Depends: P9.1 | Verify: `./mvnw test -Dtest=StaffProfileTest`
- [ ] P9.3 Invitation create + accept with secure, expiring link (FR-4.2) | Layer: BE | Depends: P9.1 (CONF-6) | Verify: `./mvnw test -Dtest=InvitationTest` — expired/tampered token rejected
- [ ] P9.4 Deactivation revokes sessions but preserves historical attribution (FR-4.4) | Layer: BE | Depends: P9.2 | Verify: `./mvnw test -Dtest=StaffDeactivationTest` — refresh token dead, past records unchanged
- [ ] P9.5 Frontend staff list + invite form with field-level errors | Layer: FE | Depends: P9.2 | Verify: `npm test -- src/features/staff` + `npm run build`
- [ ] P9.6 E2E: invite → accept → login | Layer: FE | Depends: P9.5, P7.6 | Verify: `npx playwright test tests/e2e/staff-invite.spec.ts`
- [ ] P9.7 Authorization + tenant isolation: staff/invitations tenant-scoped; foreign IDs → 404 | Layer: BE | Depends: P9.3 | Verify: `./mvnw test -Dtest=StaffIsolationTest`
- [ ] P9.8 Update PROJECT_CONTEXT phase log + commit | Layer: DOC | Depends: P9.7 | Verify: phase log row for Phase 9 committed

### Phase 10 — Patient Management
- **Scope:** patient registration, MRN generation, allergies, assignments, search/pagination, duplicate warning, archive.
- **Exit:** Resource policy (assigned/treated) enforced; receptionist field masking verified.

- [ ] P10.1 Migration: `patients`, `allergies`, `patient_assignments`; unique `(tenant_id, mrn)`; name/phone indexes | Layer: DB | Depends: P9.8 | Verify: `./mvnw test -Dtest=MigrationPatientIT` + duplicate MRN insert fails
- [ ] P10.2 MRN generation: human-readable, tenant-unique (FR-5.2) | Layer: BE | Depends: P10.1 | Verify: `./mvnw test -Dtest=MrnGenerationTest`
- [ ] P10.3 Patient registration with field-specific validation errors (FR-5.1) | Layer: BE | Depends: P10.1 | Verify: `./mvnw test -Dtest=PatientRegistrationTest` asserts field-level messages
- [ ] P10.4 Duplicate detection warning (FR-5.3) | Layer: BE | Depends: P10.3 | Verify: `./mvnw test -Dtest=DuplicatePatientWarningTest`
- [ ] P10.5 Allergies with severity, prominently readable (FR-5.4) | Layer: BE | Depends: P10.3 | Verify: `./mvnw test -Dtest=AllergyTest`
- [ ] P10.6 Paginated tenant-scoped search + archive (never hard delete) (FR-5.5/5.6) | Layer: BE | Depends: P10.3 | Verify: `./mvnw test -Dtest=PatientSearchArchiveTest` — page size capped, delete attempt → archived only
- [ ] P10.7 Frontend patient list/detail: names, MRN, allergy banner, required markers | Layer: FE | Depends: P10.3 | Verify: `npm test -- src/features/patients` + `npm run build` + no raw IDs in rendered output
- [ ] P10.8 Resource policy + field masking verified (receptionist sees no clinical fields) | Layer: BE | Depends: P6.4, P6.5 | Verify: `./mvnw test -Dtest=PatientAccessPolicyTest,FieldMaskingPatientTest`
- [ ] P10.9 Tenant isolation suite for patients (direct ID, UUID manipulation, pagination boundaries) | Layer: BE | Depends: P10.6 | Verify: `./mvnw test -Dtest=PatientIsolationIT` → 0 failures
- [ ] P10.10 Update PROJECT_CONTEXT phase log + commit | Layer: DOC | Depends: P10.9 | Verify: phase log row for Phase 10 committed

### Phase 11 — Appointments & Queue
- **Scope:** booking/reschedule/cancel, availability, double-booking prevention (constraint + transaction), day queue.
- **Exit:** Concurrent booking test proves one winner; UI shows names not IDs.

- [ ] P11.1 Migration: `appointments` with unique `(tenant_id, doctor_id, start_at)` + date indexes | Layer: DB | Depends: P10.10 | Verify: `./mvnw test -Dtest=MigrationAppointmentIT` + duplicate slot insert fails
- [ ] P11.2 Book, reschedule, cancel with transactional overlap check (FR-6.1/6.2) | Layer: BE | Depends: P11.1 | Verify: `./mvnw test -Dtest=AppointmentBookingTest`
- [ ] P11.3 Concurrent double-booking test: exactly one winner, other gets 409 `SLOT_UNAVAILABLE` | Layer: BE | Depends: P11.2 | Verify: `./mvnw test -Dtest=ConcurrentBookingTest` — one success, one 409
- [ ] P11.4 Doctor availability + day queue statuses (FR-6.4) | Layer: BE | Depends: P11.2 | Verify: `./mvnw test -Dtest=DayQueueStatusTest`
- [ ] P11.5 Frontend booking + queue views showing patient/doctor names, never raw IDs (FR-6.3) | Layer: FE | Depends: P11.2 | Verify: `npm test -- src/features/appointments` asserts labels, not UUIDs + `npm run build`
- [ ] P11.6 Authorization tests for appointment permissions by role (receptionist book, etc.) | Layer: BE | Depends: P11.2 | Verify: `./mvnw test -Dtest=AppointmentAuthzTest` → 0 failures
- [ ] P11.7 Tenant isolation: booking with another tenant's `patientId` → 404; foreign appointment unreadable | Layer: BE | Depends: P11.2 | Verify: `./mvnw test -Dtest=AppointmentIsolationTest`
- [ ] P11.8 Update PROJECT_CONTEXT phase log + commit | Layer: DOC | Depends: P11.7 | Verify: phase log row for Phase 11 committed

### Phase 12 — Clinical Consultation
- **Scope:** visits, vitals, notes, diagnoses, recommendations, orders, finalize, addenda.
- **Exit:** Finalized visits are read-only; amendments recorded with reason.

- [ ] P12.1 Migration: `visits`, `vitals`, `clinical_notes`, `diagnoses`, `recommendations`, `orders` | Layer: DB | Depends: P11.8 | Verify: `./mvnw test -Dtest=MigrationClinicalIT`
- [ ] P12.2 Start a visit from an appointment or as a walk-in (FR-7.1) | Layer: BE | Depends: P12.1 | Verify: `./mvnw test -Dtest=VisitStartTest`
- [ ] P12.3 Record vitals, notes, diagnoses, recommendations, orders per visit (FR-7.2) | Layer: BE | Depends: P12.2 | Verify: `./mvnw test -Dtest=VisitRecordTest`
- [ ] P12.4 Finalize → read-only; corrections only via addenda with reason (FR-7.3/7.4) | Layer: BE | Depends: P12.3 | Verify: `./mvnw test -Dtest=VisitFinalizeTest` — edit after finalize → 409 `RECORD_FINALIZED`
- [ ] P12.5 Attribution on every entry: author, timestamp, change reason | Layer: BE | Depends: P12.3 | Verify: `./mvnw test -Dtest=ClinicalAttributionTest`
- [ ] P12.6 Frontend consultation screen: Clinical accent, names not IDs, finalize confirmation (FR-7.5) | Layer: FE | Depends: P12.4 | Verify: `npm test -- src/features/clinical` + `npm run build`
- [ ] P12.7 Authorization + field masking: nurse/receptionist matrix; receptionist never receives notes/diagnoses | Layer: BE | Depends: P6.6, P6.5 | Verify: `./mvnw test -Dtest=ClinicalAuthzMaskingTest` → 0 failures
- [ ] P12.8 Tenant isolation: visits/records of Tenant A invisible to Tenant B | Layer: BE | Depends: P12.4 | Verify: `./mvnw test -Dtest=VisitIsolationTest`
- [ ] P12.9 Update PROJECT_CONTEXT phase log + commit | Layer: DOC | Depends: P12.8 | Verify: phase log row for Phase 12 committed

### Phase 13 — Medical History
- **Scope:** conditions, family and surgical history, paginated unified timeline.
- **Exit:** Timeline is bounded and paginated; clinician attribution visible.

- [ ] P13.1 Migration: `conditions`, `family_history`, `surgical_history` + timeline indexes | Layer: DB | Depends: P12.9 | Verify: `./mvnw test -Dtest=MigrationHistoryIT`
- [ ] P13.2 Conditions, family history, surgical history CRUD (FR-8.1) | Layer: BE | Depends: P13.1 | Verify: `./mvnw test -Dtest=HistoryRecordTest`
- [ ] P13.3 Unified paginated timeline across visits, prescriptions, labs, imaging, documents (FR-8.2) | Layer: BE | Depends: P13.2 | Verify: `./mvnw test -Dtest=TimelineTest` — size capped, no unbounded query possible
- [ ] P13.4 Timeline entries show responsible clinician and department (FR-8.3) | Layer: BE | Depends: P13.3 | Verify: `./mvnw test -Dtest=TimelineAttributionTest`
- [ ] P13.5 Frontend Timeline component + patient history page (paginated, loading/empty/error states) | Layer: FE | Depends: P13.3 | Verify: `npm test -- src/components/Timeline` + `npm run build`
- [ ] P13.6 Authorization + tenant isolation for history and timeline | Layer: BE | Depends: P13.3 | Verify: `./mvnw test -Dtest=HistoryIsolationTest`
- [ ] P13.7 Update PROJECT_CONTEXT phase log + commit | Layer: DOC | Depends: P13.6 | Verify: phase log row for Phase 13 committed

### Phase 14 — Prescriptions & Medicines
- **Scope:** medicine catalog, versioned prescriptions with duration, prescriber attribution, allergy warning, PDF output.
- **Exit:** Editing creates v2 with reason; cross-doctor visibility audited.

- [ ] P14.1 Migration: `medicines`, `prescriptions`, `prescription_versions`, `prescription_items` | Layer: DB | Depends: P12.9 | Verify: `./mvnw test -Dtest=MigrationPrescriptionIT`
- [ ] P14.2 Tenant-scoped searchable medicine catalog (FR-9.5) | Layer: BE | Depends: P14.1 | Verify: `./mvnw test -Dtest=MedicineCatalogTest` (tenant-scoped results)
- [ ] P14.3 Create prescription: medicine, dose, frequency, duration, instructions + prescriber (FR-9.1/9.3) | Layer: BE | Depends: P14.1 | Verify: `./mvnw test -Dtest=PrescriptionCreateTest`
- [ ] P14.4 Versioning: edit creates v2 with reason; v1 remains retrievable (FR-9.2) | Layer: BE | Depends: P14.3 | Verify: `./mvnw test -Dtest=PrescriptionVersioningTest` — GET versions returns v1 and v2
- [ ] P14.5 Allergy conflict warning on prescribing (FR-9.4) | Layer: BE | Depends: P14.3 | Verify: `./mvnw test -Dtest=AllergyConflictWarningTest`
- [ ] P14.6 Cross-doctor prescriber attribution visible and audited (FR-9.3, TDD §8.3) | Layer: BE | Depends: P14.3 | Verify: `./mvnw test -Dtest=PrescriberAuditTest` — audit row asserted
- [ ] P14.7 Printable/PDF prescription (FR-9.6) | Layer: BE | Depends: P14.4 | Verify: `./mvnw test -Dtest=PrescriptionPdfTest` — PDF bytes non-empty
- [ ] P14.8 Frontend prescription form + history showing prescriber names, duration (DESIGN_SYSTEM §7) | Layer: FE | Depends: P14.4 | Verify: `npm test -- src/features/prescriptions` + `npm run build`
- [ ] P14.9 Authorization + tenant isolation: prescriptions Tenant A vs Tenant B | Layer: BE | Depends: P14.4 | Verify: `./mvnw test -Dtest=PrescriptionIsolationTest`
- [ ] P14.10 Update PROJECT_CONTEXT phase log + commit | Layer: DOC | Depends: P14.9 | Verify: phase log row for Phase 14 committed

### Phase 15 — Lab & Imaging
- **Scope:** orders, results, abnormal flags, report attachment, timeline integration.
- **Exit:** Lab staff see only order-related data.

- [ ] P15.1 Migration: `lab_results`, `imaging_studies` linked to orders/visits + `document_id` | Layer: DB | Depends: P12.9, P16.7 | Verify: `./mvnw test -Dtest=MigrationLabIT`
- [ ] P15.2 Result entry/report upload linked to order and visit (FR-10.2) | Layer: BE | Depends: P15.1 | Verify: `./mvnw test -Dtest=LabResultTest`
- [ ] P15.3 Abnormal flags + results appearing on the patient timeline (FR-10.3) | Layer: BE | Depends: P15.2 | Verify: `./mvnw test -Dtest=AbnormalFlagTimelineTest`
- [ ] P15.4 Lab/imaging staff restricted to their order-related data only | Layer: BE | Depends: P15.2 | Verify: `./mvnw test -Dtest=LabStaffScopeTest` — unrelated order → 404
- [ ] P15.5 Frontend lab/imaging result views (Clinical accent, names not IDs) | Layer: FE | Depends: P15.3 | Verify: `npm test -- src/features/lab` + `npm run build`
- [ ] P15.6 Authorization + tenant isolation for orders and results | Layer: BE | Depends: P15.4 | Verify: `./mvnw test -Dtest=LabIsolationTest`
- [ ] P15.7 Update PROJECT_CONTEXT phase log + commit | Layer: DOC | Depends: P15.6 | Verify: phase log row for Phase 15 committed

### Phase 16 — Document & File Management
- **Scope:** storage abstraction (MinIO/S3), validated uploads, authorized short-lived downloads, audit of downloads.
- **Exit:** Oversized/unsupported/spoofed files rejected; cross-tenant download blocked.

- [ ] P16.1 `StorageService` interface + `MinioStorageService` selected by profile | Layer: BE | Depends: P10.10 | Verify: `./mvnw test -Dtest=MinioStorageTest` with local MinIO container
- [ ] P16.2 Upload validation: extension + MIME allow-list, magic-byte check, size limit, sanitized names, checksum (FR-11.2) | Layer: BE | Depends: P16.1 | Verify: `./mvnw test -Dtest=UploadValidationTest` — oversized/spoofed rejected with `FILE_TOO_LARGE` / `FILE_TYPE_NOT_ALLOWED`
- [ ] P16.3 `S3StorageService` behind the same interface (prod profile) | Layer: BE | Depends: P16.1 | Verify: `./mvnw test -Dtest=S3StorageProfileTest` — profile wiring selects implementation
- [ ] P16.4 Authorized short-lived download (pre-signed or streamed) + download audited (FR-11.3) | Layer: BE | Depends: P16.2 | Verify: `./mvnw test -Dtest=DownloadAuthzTest` — cross-tenant download → 404; audit row asserted
- [ ] P16.5 Frontend upload UI with clear rejection errors and progress states | Layer: FE | Depends: P16.2 | Verify: `npm test -- src/features/documents` + `npm run build`
- [ ] P16.6 Tenant isolation: object keys `tenants/{tenantId}/`; foreign key access blocked | Layer: BE | Depends: P16.4 | Verify: `./mvnw test -Dtest=DocumentIsolationTest`
- [ ] P16.7 Update PROJECT_CONTEXT phase log + commit | Layer: DOC | Depends: P16.6 | Verify: phase log row for Phase 16 committed

### Phase 17 — Billing
- **Scope:** invoices, line items, payments (partial), status, idempotency, billing role restrictions.
- **Exit:** Totals verified with `BigDecimal` tests; billing staff cannot read clinical detail.

- [ ] P17.1 Migration: `invoices`, `invoice_items`, `payments` (`DECIMAL(19,4)` + currency) | Layer: DB | Depends: P12.9 | Verify: `./mvnw test -Dtest=MigrationBillingIT`
- [ ] P17.2 Invoice from a visit with line items and totals (FR-12.1) | Layer: BE | Depends: P17.1 | Verify: `./mvnw test -Dtest=InvoiceTotalsTest` — BigDecimal scale/rounding assertions
- [ ] P17.3 Payments full/partial + invoice status + `Idempotency-Key` (FR-12.2) | Layer: BE | Depends: P17.2 | Verify: `./mvnw test -Dtest=PaymentIdempotencyTest` — replay returns the original result
- [ ] P17.4 Billing role sees billable data only, no clinical detail (FR-12.3) | Layer: BE | Depends: P17.2 | Verify: `./mvnw test -Dtest=BillingFieldMaskingTest` — response has no diagnosis/notes fields
- [ ] P17.5 Frontend invoices/payments (StatusBadge, names not IDs, confirm on destructive actions) | Layer: FE | Depends: P17.2 | Verify: `npm test -- src/features/billing` + `npm run build`
- [ ] P17.6 Tenant isolation: invoice of Tenant A invisible to Tenant B; foreign `visitId` → 404 | Layer: BE | Depends: P17.3 | Verify: `./mvnw test -Dtest=BillingIsolationTest`
- [ ] P17.7 Update PROJECT_CONTEXT phase log + commit | Layer: DOC | Depends: P17.6 | Verify: phase log row for Phase 17 committed

### Phase 18 — Notifications
- **Scope:** outbox-based email (verification, invite, reset), retries, in-app notifications.
- **Exit:** Sending never blocks requests; failures retried and observable.

- [ ] P18.1 Migration: `outbox_jobs`, `notifications` | Layer: DB | Depends: P5.10 | Verify: `./mvnw test -Dtest=MigrationNotificationIT`
- [ ] P18.2 `JobPublisher` interface + outbox worker with retry/backoff; payload carries `tenantId` | Layer: BE | Depends: P18.1 | Verify: `./mvnw test -Dtest=OutboxWorkerTest` — failed job retried with backoff
- [ ] P18.3 SMTP abstraction sends verification/invite/reset emails asynchronously (FR-13.1) | Layer: BE | Depends: P18.2 (CONF-6) | Verify: `./mvnw test -Dtest=AsyncEmailTest` — request completes before send finishes
- [ ] P18.4 Resend verification rate limited, never times out or leaves the flow (FR-1.3) | Layer: BE | Depends: P18.3 | Verify: `./mvnw test -Dtest=ResendVerificationRateLimitTest` — 429 after limit; UI stays on page
- [ ] P18.5 In-app notifications with read/unread state (FR-13.2) | Layer: BE | Depends: P18.1 | Verify: `./mvnw test -Dtest=InAppNotificationTest`
- [ ] P18.6 Tenant isolation: worker restores tenant context; notifications scoped to user/tenant | Layer: BE | Depends: P18.2 | Verify: `./mvnw test -Dtest=NotificationIsolationTest`
- [ ] P18.7 Record the email provider decision (OQ-5/TQ-3) + update PROJECT_CONTEXT phase log | Layer: DOC | Depends: P18.4 | Verify: OQ-5/TQ-3 marked decided with provider name; phase log row committed

### Phase 19 — Audit & Compliance Controls
- **Scope:** complete audit event coverage, audit viewer, append-only DB permissions, retention configuration.
- **Exit:** Audit coverage checklist met; audit rows are tenant-scoped.

- [ ] P19.1 Migration: `audit_logs` append-only; app DB user granted INSERT/SELECT only | Layer: DB | Depends: P6.8 | Verify: `./mvnw test -Dtest=AuditAppendOnlyTest` — UPDATE/DELETE as app user fails
- [ ] P19.2 `AuditService` + aspect covering the TDD §16 event list | Layer: BE | Depends: P19.1 | Verify: `./mvnw test -Dtest=AuditEventCoverageTest` — each documented event emits a row
- [ ] P19.3 Audit coverage checklist vs TDD §16 / SECURITY §10 | Layer: DOC | Depends: P19.2 | Verify: checklist completed in docs; every checked item has a corresponding test
- [ ] P19.4 Audit viewer endpoint + UI, paginated, admin-only (FR-14.2) | Layer: BE | Depends: P19.2 | Verify: `./mvnw test -Dtest=AuditViewerTest` (permission, pagination) + `npm run build`
- [ ] P19.5 Retention configuration; no purge job without a documented policy (OQ-4 default) | Layer: BE | Depends: P19.1 | Verify: `./mvnw test -Dtest=AuditRetentionConfigTest` — no deletion path exists
- [ ] P19.6 Tenant isolation: audit rows tenant-scoped; viewer sees only its own tenant | Layer: BE | Depends: P19.4 | Verify: `./mvnw test -Dtest=AuditIsolationTest`
- [ ] P19.7 Update PROJECT_CONTEXT phase log + commit | Layer: DOC | Depends: P19.6 | Verify: phase log row for Phase 19 committed

### Phase 20 — Search
- **Scope:** permission-aware, tenant-scoped search across patients/staff/appointments.
- **Exit:** No cross-tenant or unauthorized result leakage; indexes verified.

- [ ] P20.1 `GET /search?q=` across patients, staff, appointments; paginated, permission-aware (FR-15.1) | Layer: BE | Depends: P11.8 | Verify: `./mvnw test -Dtest=SearchEndpointTest` — page/size caps enforced
- [ ] P20.2 Index support for search access paths + `EXPLAIN` verification | Layer: DB | Depends: P20.1 | Verify: `EXPLAIN` output recorded in docs; no full-table scan on the hot path
- [ ] P20.3 Frontend global search, debounced ~300 ms, minimum query length | Layer: FE | Depends: P20.1 | Verify: `npm test -- src/features/search` + `npm run build`
- [ ] P20.4 Tests: no cross-tenant leakage, no unauthorized results, no unbounded results | Layer: BE | Depends: P20.1 | Verify: `./mvnw test -Dtest=SearchIsolationTest` → 0 failures
- [ ] P20.5 Update PROJECT_CONTEXT phase log + commit | Layer: DOC | Depends: P20.4 | Verify: phase log row for Phase 20 committed

### Phase 21 — Dashboards & Analytics
- **Scope:** role-based dashboards using aggregate endpoints (e.g., consolidated staff counts).
- **Exit:** Dashboards load with a bounded number of requests.

- [ ] P21.1 Aggregate endpoints: queue size, appointments, revenue, workload — all bounded and paginated where applicable | Layer: BE | Depends: P17.7 | Verify: `./mvnw test -Dtest=DashboardAggregateTest` — bounded response sizes
- [ ] P21.2 Role-based dashboards (FR-16.1) with loading/empty/error states | Layer: FE | Depends: P21.1 | Verify: `npm test -- src/features/dashboard` + `npm run build`
- [ ] P21.3 Tests: bounded request count per dashboard, role-scoped, tenant-scoped | Layer: BE | Depends: P21.1 | Verify: `./mvnw test -Dtest=DashboardAuthzIsolationTest`
- [ ] P21.4 Update PROJECT_CONTEXT phase log + commit | Layer: DOC | Depends: P21.3 | Verify: phase log row for Phase 21 committed

### Phase 22 — Performance Optimization
- **Scope:** evidence-driven: query plans, N+1 removal, caching where measured, bundle analysis, load tests.
- **Exit:** Documented before/after measurements; targets in PRD §9.2 met.

- [ ] P22.1 Capture baseline: p95 latency and query counts on list/search/login/booking | Layer: INFRA | Depends: P21.4 | Verify: k6 run output saved as the baseline report
- [ ] P22.2 N+1 detection and removal where the baseline shows it | Layer: BE | Depends: P22.1 | Verify: before/after query-count comparison for each fixed endpoint
- [ ] P22.3 Query plan review (`EXPLAIN`) for list/search access paths | Layer: DB | Depends: P22.1 | Verify: `EXPLAIN` results documented; no regressions
- [ ] P22.4 Selective Redis caching where measured; no clinical PHI cached without an ADR | Layer: BE | Depends: P22.2 | Verify: `./mvnw test -Dtest=CacheInvalidationTest` + ADR only if PHI considered
- [ ] P22.5 Frontend bundle analysis and code-splitting where measured | Layer: FE | Depends: P22.1 | Verify: `npm run build` size report shows no regression
- [ ] P22.6 Meet PRD §9.2 target: p95 API latency ≤ 500 ms for standard reads under expected load | Layer: BE | Depends: P22.3, P22.4 | Verify: k6 report shows p95 ≤ 500 ms
- [ ] P22.7 Document before/after measurements (PRD §12/§9.2 evidence) + phase log | Layer: DOC | Depends: P22.6 | Verify: measurement table committed; phase log row for Phase 22

### Phase 23 — Security Hardening
- **Scope:** full security audit, CSP/headers tuning, dependency scan fixes, rate-limit review, pen-test style checks.
- **Exit:** No open P0/P1 findings.

- [ ] P23.1 Run Security Review prompt (AI_DEVELOPMENT_GUIDE §7); triage findings P0–P3 | Layer: DOC | Depends: P22.7 | Verify: findings with severity/location/status listed in PROJECT_CONTEXT §11
- [ ] P23.2 Headers, CSP, CORS allow-list tuning per SECURITY §6 | Layer: BE | Depends: P23.1 | Verify: `./mvnw test -Dtest=SecurityHeadersCorsTest` (HSTS, nosniff, frame-ancestors, no wildcard CORS)
- [ ] P23.3 Dependency + secret scans; fix all critical/high issues | Layer: INFRA | Depends: P23.1 | Verify: dependency scan exits with no critical/high findings; secret scan clean
- [ ] P23.4 Rate-limit review against the SECURITY §12 limit table | Layer: BE | Depends: P23.1 | Verify: `./mvnw test -Dtest=RateLimitMatrixTest` — each documented limit enforced
- [ ] P23.5 Pen-test style checks: IDOR, mass assignment, XSS, upload abuse, enumeration | Layer: BE | Depends: P23.2 | Verify: `./mvnw verify` passes the abuse-case test suite
- [ ] P23.6 Verify no public Swagger/actuator/Prometheus in the production profile | Layer: INFRA | Depends: P23.2 | Verify: prod-profile test + external request returns 401/404
- [ ] P23.7 Fix every P0/P1 finding with a regression test | Layer: BE | Depends: P23.5 | Verify: previously failing tests now green; zero open P0/P1 in PROJECT_CONTEXT §11
- [ ] P23.8 Update SECURITY.md checklist status + PROJECT_CONTEXT phase log | Layer: DOC | Depends: P23.7 | Verify: docs committed; phase log row for Phase 23

### Phase 24 — Automated Testing (completion)
- **Scope:** fill coverage gaps, full tenant-isolation suite, critical E2E flows, failure-case matrix.
- **Exit:** Suite green in CI; flake rate acceptable.

- [ ] P24.1 Fill coverage gaps to ≥ 80% service-layer lines (TESTING §3) | Layer: BE | Depends: P23.8 | Verify: `./mvnw verify` + JaCoCo report shows ≥ 80%
- [ ] P24.2 Full tenant-isolation suite covering every vector in TESTING §4 | Layer: BE | Depends: P24.1 | Verify: `./mvnw test -Dtest=TenantIsolationSuite` → 0 failures
- [ ] P24.3 Critical E2E flows (TESTING §7, all 14 flows) automated | Layer: FE | Depends: P24.1 | Verify: `npx playwright test` — all green
- [ ] P24.4 Failure-case matrix automated (TESTING §8) | Layer: BE | Depends: P24.1 | Verify: `./mvnw test -Dtest=FailureCaseMatrixTest` → 0 failures
- [ ] P24.5 Accessibility checks (axe) on key pages | Layer: FE | Depends: P24.3 | Verify: `npx playwright test tests/a11y` — no violations on key pages
- [ ] P24.6 Fix or quarantine flaky tests with a tracked issue | Layer: BE | Depends: P24.3 | Verify: three consecutive full-suite runs with identical results
- [ ] P24.7 Update TESTING.md coverage/CI status + PROJECT_CONTEXT phase log | Layer: DOC | Depends: P24.6 | Verify: docs committed; phase log row for Phase 24

### Phase 25 — Observability
- **Scope:** Prometheus metrics, dashboards/alerts, trace propagation, OpenTelemetry (optional), log redaction verification.
- **Exit:** Key SLIs visible; monitoring endpoints not public.

- [ ] P25.1 Prometheus metrics: request latency/error rate/throughput, DB & Redis latency, JVM, pools | Layer: BE | Depends: P2.4 | Verify: `./mvnw test -Dtest=MetricsEndpointTest` — required metrics registered
- [ ] P25.2 `traceId` propagation across logs (and traces if OpenTelemetry added) | Layer: BE | Depends: P25.1 | Verify: `./mvnw test -Dtest=TracePropagationTest`
- [ ] P25.3 Log redaction verification: no PHI, passwords or tokens in logs | Layer: BE | Depends: P25.2 | Verify: `./mvnw test -Dtest=LogRedactionTest` — scan of captured log output
- [ ] P25.4 Monitoring dashboards + alerts per DEPLOYMENT §9 | Layer: INFRA | Depends: P25.1 | Verify: dashboards load in staging; a test alert fires
- [ ] P25.5 Monitoring endpoints reachable only from the internal network | Layer: INFRA | Depends: P25.1 | Verify: external request to Prometheus/actuator → 404/401
- [ ] P25.6 Update PROJECT_CONTEXT phase log + commit | Layer: DOC | Depends: P25.5 | Verify: phase log row for Phase 25 committed

### Phase 26 — CI/CD
- **Scope:** pipeline (build, test, scan, migrate-check, deploy), environment promotion, secret handling.
- **Exit:** Merge to main deploys to staging automatically.

- [ ] P26.1 Pipeline stage: backend compile + unit tests on every PR | Layer: INFRA | Depends: P24.7 | Verify: pipeline run green on a test PR
- [ ] P26.2 Pipeline stage: integration + tenant-isolation tests (Testcontainers) | Layer: INFRA | Depends: P26.1 | Verify: CI run shows isolation suite executed and green
- [ ] P26.3 Pipeline stage: migration check on an empty database | Layer: INFRA | Depends: P26.1 | Verify: CI migration stage green
- [ ] P26.4 Pipeline stage: frontend typecheck, lint, tests, build | Layer: INFRA | Depends: P26.1 | Verify: CI frontend stage green
- [ ] P26.5 Pipeline stage: dependency and secret scans | Layer: INFRA | Depends: P26.1 | Verify: scans run in CI; secrets never committed
- [ ] P26.6 Deploy to staging + E2E smoke + manual approval → production | Layer: INFRA | Depends: P26.2, P26.3, P26.4 | Verify: merge to main deploys staging; smoke test green
- [ ] P26.7 Secret handling: CI secrets from a store, never in the repo | Layer: INFRA | Depends: P26.5 | Verify: secret scan clean; no secret values in workflow files
- [ ] P26.8 Update DEPLOYMENT.md pipeline section + PROJECT_CONTEXT phase log | Layer: DOC | Depends: P26.6 | Verify: docs committed; phase log row for Phase 26

### Phase 27 — Production Deployment
- **Scope:** Vercel + EC2 + managed MySQL + S3 + Redis, TLS, environment config, backups enabled.
- **Exit:** Smoke tests pass in production; no dev config present.

- [ ] P27.1 Frontend production deploy (Vercel) with environment configuration | Layer: INFRA | Depends: P26.6 | Verify: production URL serves the app build from the release commit
- [ ] P27.2 Backend container on EC2 behind Nginx with TLS, HTTP→HTTPS redirect, HSTS | Layer: INFRA | Depends: P27.1 | Verify: `curl -I https://…` returns 200/301 and HSTS header
- [ ] P27.3 Managed MySQL + Redis in private networking; least-privilege `app_rw` user | Layer: INFRA | Depends: P27.2 | Verify: DB reachable only from backend security group; `app_rw` cannot ALTER
- [ ] P27.4 S3 private bucket with SSE and IAM scoped to the `tenants/*` prefix | Layer: INFRA | Depends: P27.2 | Verify: bucket policy denies public access; IAM limited to prefix
- [ ] P27.5 Production configuration checklist (DEPLOYMENT §6) verified | Layer: INFRA | Depends: P27.4 | Verify: every checklist item checked with evidence
- [ ] P27.6 Automated backups enabled for database and object storage | Layer: INFRA | Depends: P27.3 | Verify: backup console shows a recent successful run
- [ ] P27.7 Production smoke tests; no dev CORS/Swagger/debug config present | Layer: INFRA | Depends: P27.5 | Verify: smoke script exits 0; prod profile config audit clean
- [ ] P27.8 Update PROJECT_CONTEXT phase log + commit | Layer: DOC | Depends: P27.7 | Verify: phase log row for Phase 27 committed

### Phase 28 — Disaster Recovery
- **Scope:** backup schedule, restore drill, runbooks, RPO/RTO definition.
- **Exit:** Successful restore drill documented.

- [ ] P28.1 Backup schedule + point-in-time recovery configured and verified | Layer: INFRA | Depends: P27.6 | Verify: PITR restore point available for a recent timestamp
- [ ] P28.2 Define RPO/RTO with stakeholders (proposed start: RPO ≤ 15 min, RTO ≤ 4 h) | Layer: DOC | Depends: P28.1 | Verify: agreed values recorded in DEPLOYMENT.md §10
- [ ] P28.3 Perform a restore drill on a copy and document it | Layer: INFRA | Depends: P28.1 | Verify: drill report committed with timings and steps
- [ ] P28.4 Runbooks: restore DB, rotate secrets, revoke sessions, suspend tenant, rollback release | Layer: DOC | Depends: P28.3 | Verify: each runbook has steps verified in the drill
- [ ] P28.5 Update PROJECT_CONTEXT phase log + commit | Layer: DOC | Depends: P28.4 | Verify: phase log row for Phase 28 committed

### Phase 29 — Production Readiness
- **Scope:** Production Readiness review prompt; go/no-go.
- **Exit:** **GO** decision, blockers resolved.

- [ ] P29.1 Run Production Readiness prompt (AI_DEVELOPMENT_GUIDE §15); list blockers | Layer: DOC | Depends: P28.5 | Verify: review output committed with GO/NO-GO and blocker list
- [ ] P29.2 Verify all automatic NO-GO conditions are absent (DEPLOYMENT §12) | Layer: INFRA | Depends: P29.1 | Verify: checklist output shows no placeholder secrets, no public Swagger/actuator/Prometheus, no dev CORS, backups present
- [ ] P29.3 Full tenant-isolation + critical E2E suites green in a production-like environment | Layer: BE | Depends: P29.2 | Verify: `./mvnw verify` and `npx playwright test` both green
- [ ] P29.4 Record the final **GO** decision in PROJECT_CONTEXT phase log | Layer: DOC | Depends: P29.3 | Verify: phase log row for Phase 29 = done with GO note

### Phase 30 — Future AI / Advanced Features
- **Candidates:** clinical summarization, decision support, OCR of lab documents, patient portal, HL7/FHIR interoperability, multi-branch tenants, message broker adoption.
- **Rule:** each is proposed with an ADR and its own PRD addendum.

- [ ] P30.1 For each candidate feature: ADR + PRD addendum approved before any code | Layer: DOC | Depends: P29.4 | Verify: merged ADR and PRD addendum exist for the feature before its first code task opens
- [ ] P30.2 Phase stays ☐ (or ⚠ blocked) until a feature is proposed and approved | Layer: DOC | Depends: P30.1 | Verify: overview status reflects the ADR decision; no code exists for unapproved features


